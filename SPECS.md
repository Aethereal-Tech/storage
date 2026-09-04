# storage — the record

What exists, and what is deliberately deferred. `CLAUDE.md` holds the rules; this file holds the
record. **When a FUTURE item lands it moves to PRESENT in the same commit that ships it.**

Legend for FUTURE: **CUT** = decided against, do not re-propose without new information.
**PARKED** = will return, and the entry says on what.

# PRESENT

## Artifacts and dependency posture

| Artifact | Holds | Runtime dependencies |
|---|---|---|
| `net.aetherealtech:storage-core` | the port, `ObjectKey`, `StoredObject`, the exceptions, upload inspection, `InMemoryObjectStorage`, `UnavailableObjectStorage`, `AfterCommit`, the Spring autoconfiguration | **none.** `spring-boot-autoconfigure` and `spring-tx` are `<optional>true</optional>` |
| `net.aetherealtech:storage-s3` | `S3ObjectStorage`, `S3Config`, its own autoconfiguration | `software.amazon.awssdk:s3` + `url-connection-client`; `apache-client`, `apache5-client` and `netty-nio-client` excluded |

Java 25, compiled with `--release 25`. MIT. Both artifacts are published from the same commit at the
same number, so a mismatched pair cannot be resolved. Published to GitHub Packages from `master` by
`.github/workflows/publish.yml`; the poms stay on `-SNAPSHOT` and the release number is stamped in by
CI.

A GitHub Actions workflow in another repository can never read these packages with its own
`GITHUB_TOKEN` — GitHub Packages for Maven always inherit the permissions of the publishing
repository, and there is no per-package Actions access grant to widen that; the only credential that
works from elsewhere is a personal access token held as a secret (Aethereal-Tech's org secret is
`PACKAGES_READ_TOKEN`). The `permissions: packages: read` rule instead governs a workflow reading
these packages from *within* this repository, using the default `GITHUB_TOKEN`: declaring a
`permissions:` block at all zeroes every permission not named, so that block must include
`packages: read` or the token cannot read the package.

The AWS SDK ships four HTTP clients and picks one off the classpath at runtime, failing at startup
if it finds none or more than one. `url-connection-client` is the smallest that does the job and
the only one reaching a consumer; the other three — `apache-client`, `apache5-client` and
`netty-nio-client` — are excluded rather than merely not added, because `s3` pulls `apache-client`
AND `apache5-client` transitively, and an unexcluded pair is an ambiguous-client failure in a
consumer's build, for a dependency they never named. `S3ObjectStorage` also names the client
explicitly rather than relying on the scan.

## The port

`net.aetherealtech.storage.ObjectStorage`, seven methods and no domain types anywhere:

```java
void      put(ObjectKey key, byte[] bytes, String contentType);
void      put(ObjectKey key, InputStream stream, long length, String contentType);
StoredObject get(ObjectKey key);                 // throws ObjectNotFoundException
boolean   exists(ObjectKey key);                 // a HEAD
void      delete(ObjectKey key);                 // quiet if already gone
URI       presignGet(ObjectKey key, Duration ttl);
URI       presignGet(ObjectKey key);             // the implementation's configured lifetime
```

Reads are presigned, writes are not: a presigned GET is what makes a public page render images with
no session, and a write through the server is what lets the server enforce the bytes first. The
stream overload's `length` is mandatory and undiscoverable — an S3 `PUT` needs a `Content-Length` up
front, so an implementation given an unknown length would buffer the whole object, which is the case
that overload exists to avoid.

`StoredObject(byte[] bytes, String contentType)` copies in and copies out, and compares by value
(a record's generated `equals` would compare the array by identity).

Three implementations: `S3ObjectStorage` (separate artifact), `InMemoryObjectStorage` (thread-safe,
for a consumer's own suite, stable fake presign URI at `https://storage.invalid` — a host that must
never resolve) and `UnavailableObjectStorage` (every call throws, including `exists` and `delete`).

## The key rule

`ObjectKey` is a validated record. **Built from ids the product already owns, NEVER from an uploaded
filename** — that is a path traversal and a collision at once. The validation is a backstop, not the
rule.

Refused: null, empty, over 1024 characters, a leading `/`, a backslash anywhere, any ISO control
character, and any segment that is empty, whitespace-only, `.` or `..`. A `..` INSIDE a segment
(`a/..b/c`) is accepted — only a whole segment is traversal. `join(String...)` joins with `/` and
validates the result, so a traversal smuggled inside a segment is still caught. Rejection is an
`IllegalArgumentException`: a key is composed from values the caller controls, so a bad one is a
programming error at the composition site, not a refusal an end user can act on.

A DERIVED key overwrites, so a URL already issued resolves to the new object — right for a logo,
wrong for a photo, which gets a generated key RECORDED on its row. The library does not choose;
it makes the choice expressible.

## Upload inspection

`UploadInspector(UploadRule).inspect(byte[]) → InspectedUpload`, throwing `UploadRejectedException`.

**The declared content type is never an input.** There is no overload taking one. The format is read
from the magic bytes and `InspectedUpload.contentType()` is what to store the object under.

Recognised (`UploadFormat`), which is deliberately wider than any consumer allows:

| Format | Signature | Content type |
|---|---|---|
| `PNG` | `89 50 4E 47` | `image/png` |
| `JPEG` | `FF D8 FF` | `image/jpeg` |
| `GIF` | `GIF8` | `image/gif` |
| `WEBP` | `RIFF` at 0, `WEBP` at 8 | `image/webp` |
| `BMP` | `BM` | `image/bmp` |
| `TIFF` | `II*\0` or `MM\0*` | `image/tiff` |
| `PDF` | `%PDF` | `application/pdf` |
| `HEIC` | `ftyp` at 4, brand `heic heix heim heis hevc hevx mif1 msf1` | `image/heic` |
| `AVIF` | `ftyp` at 4, brand `avif avis` | `image/avif` |
| `UNKNOWN` | nothing matched | `application/octet-stream` |

Each signature checks only the bytes it needs — a single twelve-byte floor (WebP's) would make a
valid eight-byte PNG stub come back `UNKNOWN`. `BM` is checked last, being two bytes long.

Allowed is `UploadRule(Set<UploadFormat> allowed, long maxBytes, int minLongEdge)`. **No presets and
no defaults**: five megabytes and thirty-two pixels are one product's policy.

Rejection reasons, in the order the checks run:

| Reason | Raised when |
|---|---|
| `UNREADABLE` | no bytes at all |
| `TOO_LARGE` | over `maxBytes` — before anything parses, so an oversized upload never reaches a reader |
| `HEIC_UNSUPPORTED` | a HEIC the rule does not allow; its own reason because it is the iPhone default and the message must say "export as JPEG" |
| `UNSUPPORTED_FORMAT` | any other format the rule does not allow, `UNKNOWN` included |
| `TOO_SMALL` | a long edge actually READ that is under `minLongEdge` |

Dimensions come from the header via a `javax.imageio` `ImageReader`, never `ImageIO.read`, which
would expand the pixels — a decompression bomb an upload endpoint must not be open to. PNG and JPEG
have readers in every JDK; other formats depend on what `javax.imageio` was given.
**Dimensions that could not be read PASS**, and `TOO_SMALL` is never raised on a guess.

## Modes

`storage.mode` — the one design point the library carries as a mode, because its consumers disagree
and both are right about their own product.

| `storage.mode` | credentials | `storage-s3` on classpath | Result |
|---|---|---|---|
| `required` (default) | absent | either | `StorageUnavailableException` at STARTUP, naming both properties — the context never comes up |
| `required` | present | yes | `S3ObjectStorage` |
| `required` | present | no | `StorageUnavailableException` at startup, saying to add the artifact |
| `optional` | absent | either | `UnavailableObjectStorage`; every call refuses at request time |
| `optional` | present | yes | `S3ObjectStorage` |
| `optional` | present | no | `UnavailableObjectStorage` + a `WARNING` naming the missing artifact |
| either | either | either | a consumer's own `ObjectStorage` bean always wins |

The credentials, and nothing else, decide `isConfigured()`. A blank bucket with credentials present
is refused by `S3Config` by name instead — a deployment that named a bucket and forgot the secret key
has TRIED to configure storage, and the useful failure says which piece is missing.

**A property that is present and EMPTY is treated as absent.** `access-key: ${STORAGE_S3_ACCESS_KEY:}`
is what a deployment writes, and `@ConditionalOnProperty` would call that configured — which is why
the decision is a bound-properties question in a `Condition` and a bean method rather than an
annotation. Pinned by a test in both modules.

## Properties

`storage.*`, one class describing the whole namespace so an operator never has to know which jar
reads which key. **No vendor defaults**: a library that defaulted an endpoint would let a deployment
that forgot one still start and sign requests for somewhere nobody chose.

| Property | Environment variable | Default | |
|---|---|---|---|
| `storage.mode` | `STORAGE_MODE` | `required` | `required` or `optional` |
| `storage.s3.endpoint` | `STORAGE_S3_ENDPOINT` | — | with its scheme; Hetzner is `https://nbg1.your-objectstorage.com` |
| `storage.s3.region` | `STORAGE_S3_REGION` | — | the signing region; Hetzner's is the same location code — `nbg1`, `fsn1`, `hel1` |
| `storage.s3.bucket` | `STORAGE_S3_BUCKET` | — | must already exist |
| `storage.s3.access-key` | `STORAGE_S3_ACCESS_KEY` | — | presence selects S3; never logged |
| `storage.s3.secret-key` | `STORAGE_S3_SECRET_KEY` | — | never logged |
| `storage.s3.presign-ttl` | `STORAGE_S3_PRESIGN_TTL` | `15m` | the lifetime `presignGet(key)` uses |
| `storage.s3.path-style` | `STORAGE_S3_PATH_STYLE` | `false` | virtual-hosted otherwise |

## S3 wire facts

- **Virtual-hosted by default** (`bucket.endpoint/key`); `path-style` (`endpoint/bucket/key`) exists
  for MinIO. Hetzner — and AWS, which deprecated path-style — needs virtual-hosted for presigned
  URLs to work at all.
- **The client and the presigner are built from ONE `S3Config` and configured identically.** A
  signature covers the host, so a presigner disagreeing about addressing style mints URLs that fail
  to verify, and the error a browser shows for that says nothing about addressing. There is no
  constructor taking two clients, which is what makes disagreeing impossible.
- Errors are mapped in ONE place, `S3ObjectStorage.execute`, so the mapping cannot differ per
  method: `NoSuchKeyException` → `ObjectNotFoundException`; any other `S3Exception` carrying status
  404 → `ObjectNotFoundException` as well; every other `S3Exception` → `StorageAccessException`
  carrying the status and the provider's error code (`null` when the response held no parseable
  error body); a transport failure that never got an HTTP answer at all (`SdkClientException` —
  connection refused, DNS, timeout) → `StorageUnavailableException`, naming the endpoint. `exists`
  answers `false` rather than propagating the not-found, and `delete` swallows it so a retry is
  safe. A 403 is what BOTH a bad secret key and a missing bucket produce, because the store will not
  confirm a bucket's existence to a caller it has not authenticated.
- **Both 404 branches are reachable, and which one a 404 takes depends on the OPERATION.** The SDK's
  generated unmarshaller for `HeadObject` synthesizes a typed `NoSuchKeyException` for ANY 404,
  body or none, because that operation is documented to have no other error. `GetObject` has more
  modelled errors, so a 404 with no parseable `<Code>` — an empty body, a proxy's own 404 page —
  cannot be defaulted that way and reaches the generic `S3Exception` branch instead. Measured, not
  assumed: each branch has its own test.
- **Retries are off and a call is bounded.** `AwsRetryStrategy.doNotRetry()` — one attempt — with a
  ten-second `apiCallTimeout` and `apiCallAttemptTimeout`. A caller sits in a request thread waiting
  on this, so a refused connection or a slow store must fail fast rather than be retried silently,
  for tens of seconds, inside a library the caller does not control. Whether to retry is the
  caller's decision. Pinned by a wire test: a 503 — exactly what the SDK's default strategy would
  retry — produces exactly one request. (`retryStrategy`, not the deprecated
  `retryPolicy(RetryPolicy.none())`; both say zero retries and only one survives the SDK's move off
  `RetryPolicy`.)
- **Chunked encoding is disabled, and request checksum calculation is `WHEN_REQUIRED`.**
  `PutObject` requires a checksum per its own model; left alone the SDK satisfies that by streaming
  the body as `aws-chunked` with a TRAILING CRC32, a wire feature an arbitrary S3-compatible store is
  not guaranteed to implement — and Hetzner is the target. Every write here hands the SDK a
  length-known body already in hand, so with chunking off the checksum is computed up front and sent
  as an ordinary header and the store receives a plain body. `WHEN_REQUIRED` rather than the SDK's
  `WHEN_SUPPORTED` closes the second, independent route by which an opportunistic checksum could
  reach the wire. The `put` test asserting the body byte-for-byte is what pins it.
- A presigned URL carries `X-Amz-Algorithm=AWS4-HMAC-SHA256`, `X-Amz-Credential`, `X-Amz-Date`,
  `X-Amz-Expires` (exactly the ttl in seconds), `X-Amz-SignedHeaders` and `X-Amz-Signature`.
  Presigning makes no HTTP request.
- **The bucket is never created, configured or deleted** — not the bucket, not its CORS policy, not
  a lifecycle rule.

## Transactions

`AfterCommit.delete(storage, key)` registers a `TransactionSynchronization` that deletes after the
commit, does nothing on rollback, and deletes immediately when no transaction is active. **Delete the
ROW first, the object after the commit**: this way a failed delete leaks an unreferenced object; the
other way leaves a live row pointing at bytes that are gone. A failure in the after-commit path is
logged at `WARNING`, not thrown — the caller's work already succeeded, and propagating would fail a
request that did everything right. The immediate path does throw, because there is no committed work
to contradict.

## Consumers

| Product | Mode | Store | Keys | Status |
|---|---|---|---|---|
| kapar | `storage.mode=required` | Hetzner `nbg1` | `listings/{id}/{uuid}`, `organizations/{id}/logo`, `ads/{id}/{uuid}` | migrating (kapar.net PR in progress) |
| Composure | `optional` — a tenant with no logo is an ordinary tenant, and the asset endpoints answer 503 | Hetzner `nbg1`, bucket defaulted to test | organization logos | planned |
| invicta | `storage.mode=required` | proven against the Hetzner test bucket and their MinIO e2e tier | `{organizationId}/orders/{id}/{uuid}`, `{organizationId}/product-templates/{id}/{slot}`, `{organizationId}/branding/logo` | adopting on their ERP branch, 0.1.0 |

Composure's `UnavailableObjectStorage` answers `exists → false` and swallows `delete`; **this
library's throws on both** (see CLAUDE.md for why). That is the one behaviour change Composure's
migration has to make deliberately.

## Coverage gates

90% line / 80% branch, **per module**, never merged. `storage-core`'s gate covers
`net/aetherealtech/storage/*`, `upload/*` and `tx/*`; `storage-s3`'s covers
`net/aetherealtech/storage/s3/*`. Both `spring` packages sit outside their gate on purpose: what
they must get right is which `ObjectStorage` a given mode and set of credentials produces, and that
is proven by the decision matrices in the autoconfiguration tests rather than by a percentage over
accessors that exist for Spring's binder to call.

**No test reaches a bucket, and none needs Docker or MinIO.** The S3 tier asserts the actual HTTP the
SDK puts on a socket, against WireMock on localhost.

# FUTURE

1. **Streaming reads, and multipart uploads for objects larger than memory.** `get` returns a
   `StoredObject` holding the whole object, and `put` from a stream still sends it in one request.
   *Why it waits:* every consumer's largest object is a 5 MB photo, so the API this would complicate
   buys nothing yet, and the shape it should take (an `InputStream` the caller must close? a
   `Consumer<InputStream>` so the library can?) is a decision better made against a real caller.
   *Owner:* the first consumer that stores a document rather than an image. **PARKED** on that.

2. **List by prefix.** No `list(prefix)` on the port. *Why it waits:* nothing needs it — a key is
   either derived (so it is known) or recorded on a row (so the database lists it). The first real
   use is a reconciliation job finding objects no row points at, which does not exist yet.
   *Owner:* whoever writes that job. **PARKED** on it existing.

3. **Server-side copy.** `CopyObject` without pulling the bytes through the application.
   *Why it waits:* the only caller anyone has named is "duplicate a listing", which nobody has built.
   *Owner:* kapar, if listing duplication ships. **PARKED**.

4. **A CORS read-back helper.** A method wrapping `get-bucket-cors` so a deployment can assert its
   policy applied — a policy that failed to apply looks exactly like one never set. *Why it waits:*
   it is a read against the bucket's CONFIGURATION, and this library's fifth rule is that it never
   touches bucket configuration. A read-only helper is arguably outside that rule, but it would be
   the first crack in it, and the operational answer (`aws s3api get-bucket-cors` by hand, once per
   environment) already works. *Owner:* revisit if a consumer wants it in a health check.
   **PARKED** on that request.

5. **A second adapter — GCS, Azure Blob.** **CUT.** All three consumers are on Hetzner, which speaks
   S3, and MinIO covers local development. An adapter with no consumer is an untested adapter with a
   dependency, and the port is the thing that makes adding one cheap later. Do not re-propose without
   a consumer that has actually chosen another provider.

6. **The consumers' own migrations.** kapar, Composure and invicta each delete their `storage`
   package and depend on this library. *Why it waits:* each is a change to a shipping product with
   its own release train, and kapar additionally has to keep its `ImageUrls`/`ImageKeys` layer, which
   is product policy and stays there. *Owner:* each product, on its own schedule. **PARKED** — this
   library reaching 0.1.0 is the prerequisite.
