# Packaging

## Purpose

Packaging covers the two-artifact split, why it is split that way, what each artifact is allowed to depend on, and
how the two are versioned and published so a mismatched pair can never be resolved.

## Requirements

### Requirement: Two artifacts, one repository, one version, published together
The reactor SHALL publish exactly two artifacts, `net.aetherealtech:storage-core` and `net.aetherealtech:storage-s3`,
from the same commit at the same version, so a mismatched pair cannot be resolved.

`storage-core` holds the port (`ObjectStorage`, `ObjectKey`, `StoredObject`, the exceptions), upload inspection
(`UploadRule`, `UploadInspector`, `UploadFormat`, `UploadRejectedException`), the two implementations that need
nothing (`InMemoryObjectStorage`, `UnavailableObjectStorage`), the `AfterCommit` helper, and the Spring-optional
autoconfiguration. `storage-s3` holds `S3ObjectStorage` and `S3Config`, and the AWS SDK they need, with its own
autoconfiguration ordered before the core one so S3 wins when credentials are configured.

The split is about the dependency, not about tidiness: a consumer who only wants the port, the key rule and an
in-memory implementation for its own suite should not inherit an S3 client or its CVE feed.

#### Scenario: Both artifacts publish from the same commit at the same version
- **WHEN** a version of `storage-core` is published
- **THEN** `storage-s3` is published from the same commit at the same version
- **AND** a consumer cannot resolve a mismatched pair

#### Scenario: A port-only consumer pulls no S3 dependency
- **WHEN** a consumer depends on `storage-core` alone, for the port, the key rule and `InMemoryObjectStorage` in its
  own test suite
- **THEN** it inherits no S3 client and no AWS SDK CVE feed

### Requirement: storage-core has zero mandatory runtime dependencies
`storage-core` SHALL have ZERO mandatory runtime dependencies. The port and the key are plain Java; upload
inspection reads magic bytes by hand and asks `javax.imageio` — part of the JDK's `java.desktop` module — for PNG
and JPEG dimensions. Spring appears only as `<optional>true</optional>`.

#### Scenario: storage-core resolves with no runtime dependency
- **WHEN** `storage-core`'s dependency tree is inspected
- **THEN** it carries no mandatory runtime dependency
- **AND** Spring is present only as `<optional>true</optional>`

### Requirement: storage-s3 brings the AWS SDK and excludes the ambiguous clients
`storage-s3` SHALL depend on `software.amazon.awssdk:s3` plus `url-connection-client`, excluding `apache-client`,
`apache5-client` and `netty-nio-client`.

The AWS SDK ships four HTTP clients and picks one off the classpath at runtime, failing at startup if it finds none
or more than one. `url-connection-client` is the smallest that does the job and the only one reaching a consumer.
`s3` pulls `apache-client` AND `apache5-client` transitively, so an unexcluded pair is an ambiguous-client failure in
a consumer's build, for a dependency they never named — the other three clients are excluded rather than merely not
added. `S3ObjectStorage` also names the client explicitly rather than relying on the scan.

#### Scenario: Only one HTTP client reaches a consumer
- **WHEN** a consumer depends on `storage-s3`
- **THEN** `url-connection-client` is the only AWS SDK HTTP client on its classpath
- **AND** `apache-client`, `apache5-client` and `netty-nio-client` are excluded

#### Scenario: S3ObjectStorage names its client explicitly
- **WHEN** `S3ObjectStorage` builds its SDK client
- **THEN** it names `url-connection-client` explicitly rather than relying on the SDK's classpath scan

### Requirement: An implementation that needs nothing sits beside the port
`InMemoryObjectStorage` and `UnavailableObjectStorage` SHALL sit in the root `net.aetherealtech.storage` package
beside the port, because they need nothing but it. An implementation that talks to something SHALL get its own
module and bring its own config record.

#### Scenario: In-memory and unavailable implementations need no separate module
- **WHEN** `InMemoryObjectStorage` or `UnavailableObjectStorage` is used
- **THEN** it is found in the root package of `storage-core`, needing nothing beyond the port

#### Scenario: An implementation that talks to something gets its own module
- **WHEN** an implementation of `ObjectStorage` talks to an external store
- **THEN** it lives in its own module with its own config record, as `S3ObjectStorage` and `S3Config` do in
  `storage-s3`

### Requirement: Every Maven request needs a token; being public keeps the bar at read:packages alone
Every Maven request to `maven.pkg.github.com` SHALL require an authenticated token, even though the package is
public — anonymous downloads return 401 whatever a repository's visibility is, a GitHub platform limitation rather
than a choice made here. Because the package is public, `read:packages` alone SHALL be enough: a classic personal
access token with that one scope, a fine-grained token, or, inside a GitHub Actions workflow, that workflow's own
`GITHUB_TOKEN` — no `repo` scope and no membership of the publishing organization is needed.

A job authenticating with a workflow's own `GITHUB_TOKEN` MUST declare `packages: read` whenever it declares a
`permissions:` block at all, since declaring one zeroes every permission not named. **The condition is the
CREDENTIAL, not the repository**: it binds a consuming repository's workflow exactly as it binds one inside this
repository, and it does not bind a personal access token at all, because a `permissions:` block scopes
`GITHUB_TOKEN` alone and leaves a PAT in `settings.xml` untouched.

#### Scenario: A workflow reads the public package with its own GITHUB_TOKEN and declares no permissions block
- **WHEN** a GitHub Actions workflow in any repository reads `storage-core` or `storage-s3` with its own default
  `GITHUB_TOKEN` and declares no `permissions:` block
- **THEN** the read succeeds, because the package is public and that token's default permissions already include
  `packages: read`

#### Scenario: A job declaring a permissions block must name packages: read
- **WHEN** a job authenticating with its own `GITHUB_TOKEN` declares a `permissions:` block, in this repository or
  in a consuming one
- **THEN** that block must include `packages: read`
- **AND** omitting it leaves the token unable to read the package even though it is otherwise entitled to it

#### Scenario: A permissions block does not affect a personal access token
- **WHEN** a job authenticates with a personal access token supplied as the `<server>` password in `settings.xml`
- **THEN** the job's `permissions:` block has no bearing on whether the package resolves, because that block scopes
  `GITHUB_TOKEN` alone

### Requirement: 0.x publishing is automatic with no version-bump commit
The API MAY still change under `0.x`. Publishing SHALL be automatic from `master`; the poms SHALL stay on
`-SNAPSHOT` and the release number SHALL be stamped in by CI, so there is never a version-bump commit to conflict
over. A commit that should not cut a release (docs, ci, chore, test, style with no accompanying fix/feat) MUST be
typed accordingly, since the workflow trusts the commit type rather than a judgment call at merge time.

#### Scenario: Poms stay on -SNAPSHOT between releases
- **WHEN** the repository is inspected between releases
- **THEN** every pom's version is a `-SNAPSHOT`, with the release number stamped in by CI at publish time

#### Scenario: A docs-only commit does not cut a release
- **WHEN** a commit is typed `docs`, `ci`, `chore`, `test` or `style` with no accompanying `fix`/`feat`
- **THEN** the publish workflow does not cut a release for it
