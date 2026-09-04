# Conventions for this repository

SPECS.md is the record of what exists; this file is the rules.

A standalone library, consumed by more than one product. These are its own rules; nothing here
inherits from a consumer's repo, and nothing here may be bent to suit one of them.

## The rules that are not negotiable

**1. No domain types in any signature, ever.** Not a `Listing`, not an `Organization`, not an
`Order` — not in `ObjectStorage`, not in `ObjectKey`, not in the inspector, not "just for
convenience". An object is a KEY, some BYTES and a CONTENT TYPE, and that is the whole vocabulary.

This is the mistake the library exists to undo. Three products wrote the same S3 client, each
against its own entities, and none of the three could be moved. If a change here would be easier
with a domain type in the signature, the change is wrong.

**2. `storage-core` has ZERO mandatory runtime dependencies.** Everything in it is the JDK: the
port and the key are plain Java, and upload inspection reads magic bytes by hand and asks
`javax.imageio` — which is `java.desktop`, part of the JDK — for PNG and JPEG dimensions. Spring
appears only as `<optional>true</optional>`. Adding a runtime dependency to `storage-core` needs a
reason in the PR description, measured against "is this genuinely more than a few hundred lines of
well-scoped JDK code?" — not "a library would be more convenient".

**3. A key is NEVER built from a filename.** An uploaded filename is user input: a path traversal
and a collision at once, since two people uploading `roof.jpg` would overwrite each other.
`ObjectKey` refuses the shapes that make traversal possible, but refusing them is a backstop —
the rule is that a key is composed from ids the product already owns. Say so wherever a key is
made.

**4. The declared content type is never an input.** `UploadInspector` sniffs the format from the
MAGIC BYTES and returns the content type to store. A caller's `Content-Type` header is written by
the caller; believing it is how a HEIC gets stored as a PNG and rendered by nothing. There is no
overload that takes one.

**5. The library never creates, configures or deletes a bucket.** Not the bucket, not its CORS
policy, not a lifecycle rule. Those are hand-applied once per environment, and a library that
created one on demand would make a typo in a bucket name into a new, empty, silently-wrong bucket.
`storage-s3` writes keys beneath a bucket that already exists and nothing else.

## Architecture

Two artifacts, one repository, one version, published together:

```
storage-core   the port (ObjectStorage, ObjectKey, StoredObject, the exceptions), upload inspection
               (UploadRule, UploadInspector, UploadFormat, UploadRejectedException), the two
               implementations that need nothing (InMemoryObjectStorage, UnavailableObjectStorage),
               the AfterCommit helper, and the Spring-optional autoconfiguration.
               ZERO mandatory runtime dependencies.
  |
storage-s3     S3ObjectStorage and S3Config, and the AWS SDK they need. Its own autoconfiguration,
               ordered before the core one so S3 wins when credentials are configured.
```

The split is about the dependency, not about tidiness: a consumer who only wants the port, the key
rule and an in-memory implementation for its own suite should not inherit an S3 client or its CVE
feed. Both artifacts are published from the same commit at the same number, so a mismatched pair
cannot be resolved.

**Where an implementation lives.** `InMemoryObjectStorage` and `UnavailableObjectStorage` sit in
the root package beside the port because they need nothing but it. Anything that talks to something
gets its own module and brings its own config record.

## Modes

`storage.mode` is the one design point this library carries because its consumers genuinely
disagree, and it must stay a MODE rather than becoming a default:

- **`required`** — no credentials means `StorageUnavailableException` at STARTUP. For a product
  where every image lives in the bucket, so a deployment without one is broken rather than reduced.
- **`optional`** — no credentials means `UnavailableObjectStorage`, and every call refuses at
  request time. For a product where an asset is decorative.

Neither is the "right" one. Do not add a third, and do not make one of them silently degrade into
the other.

## Conventions

- **Java 25**, compiled with `--release 25`. Consumers need JDK 25+; deliberate, not an oversight.
- **No Lombok.** Records and plain Java. The sibling libraries have none either.
- **`final` on method and constructor parameters.** Consistent throughout `src/main`; match it.
- 4-space indent. Private methods at the bottom.
- **Comments explain what the code cannot**: a constraint, a framework behaviour, a rejected
  alternative, a subtlety a future reader would otherwise re-derive. Never restate the next line.
  Javadoc on every public type.
- **Conventional commits**, and **no AI attribution** — no `Co-Authored-By` for tools, no "generated
  with" trailer, nothing of the kind in commit messages, PR bodies, or code comments.
- **When a FUTURE item in SPECS.md lands, it moves to PRESENT in the same commit that ships it.**

## Testing

- **Assert the wire, never a round trip.** `S3ObjectStorageTest` asserts the actual HTTP the AWS SDK
  puts on a socket — the `Host` header virtual-hosted addressing produces, the request line
  path-style produces, the PUT body, the SigV4 query parameters on a presigned URL — against
  WireMock. A test that writes with `S3ObjectStorage` and reads it back with `S3ObjectStorage`
  proves the two agree with each other and nothing about what Hetzner will accept.
- **No live storage, ever.** No test may reach a bucket, and no test needs Docker or MinIO. A suite
  that needs credentials is a suite that quietly stops running.
- **Coverage is per module**, gated at 90% line and 80% branch. The `spring` packages sit outside
  both gates on purpose: what they must get right is *which `ObjectStorage` a given mode and set of
  credentials produces*, and that is proven by the decision matrix in the autoconfiguration tests
  rather than by a percentage over accessors that exist for Spring's binder to call. If a change
  cannot clear a gate, the change needs tests, not a lower gate.
- Every recognised format is tested from REAL bytes — ImageIO-generated for PNG and JPEG,
  hand-written headers for the rest. A sniffer tested against the constants it was written from
  tests nothing.

## Three things that look like bugs and are not

- **`UnavailableObjectStorage.exists` throws rather than answering `false`.** A null object that
  answered "no logo" would make `optional` mode indistinguishable from a configured bucket that
  happens to be empty, and a consumer would ship the difference without noticing. A consumer that
  genuinely wants the soft answer catches `StorageUnavailableException` at its own call site, where
  the decision is visible.
- **`UploadRejectedException` separates `HEIC_UNSUPPORTED` from `UNSUPPORTED_FORMAT`.** HEIC is the
  iPhone's default, so it WILL be tried, and the message has to say "export as JPEG" rather than
  "unsupported". Recognising a type and accepting it are different things.
- **Unreadable dimensions PASS the minimum-size check.** The format check has already established
  what this is; refusing on an `ImageReader` that cannot introspect a valid file would turn a
  library quirk into a rejected upload. `TOO_SMALL` is only ever raised on a dimension actually
  read.

## Versioning

`0.x` — the API may still change. Publishing is automatic from `master` (see
`.github/workflows/publish.yml`); the poms stay on `-SNAPSHOT` and the release number is stamped in
by CI, so there is never a version-bump commit to conflict over. A commit that should not cut a
release (docs, ci, chore, test, style with no accompanying fix/feat) must be typed accordingly — the
workflow trusts the commit type, not a judgment call at merge time.

## Build

```
./mvnw clean verify
```

JDK 25. Runs both modules, both coverage gates. Read Maven's own exit code: a pipe reports the
pipe's status.
