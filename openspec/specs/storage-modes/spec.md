# Storage modes

## Purpose

Storage modes covers `storage.mode`, the one design point the library carries because its consumers genuinely
disagree, the full `storage.*` property namespace, and the precedence that decides which `ObjectStorage` bean a
given mode and set of credentials produces.

## Requirements

### Requirement: storage.mode is required or optional, and neither degrades into the other
`storage.mode` SHALL be either `required` or `optional`, and it SHALL stay a MODE rather than becoming a default.
`required` SHALL mean no credentials produces `StorageUnavailableException` at STARTUP, for a product where every
image lives in the bucket, so a deployment without one is broken rather than reduced. `optional` SHALL mean no
credentials produces `UnavailableObjectStorage`, with every call refusing at request time, for a product where an
asset is decorative. Neither mode SHALL be treated as the "right" one, and no third mode SHALL be added.

| `storage.mode` | credentials | `storage-s3` on classpath | Result |
|---|---|---|---|
| `required` (default) | absent | either | `StorageUnavailableException` at STARTUP, naming both properties — the context never comes up |
| `required` | present | yes | `S3ObjectStorage` |
| `required` | present | no | `StorageUnavailableException` at startup, saying to add the artifact |
| `optional` | absent | either | `UnavailableObjectStorage`; every call refuses at request time |
| `optional` | present | yes | `S3ObjectStorage` |
| `optional` | present | no | `UnavailableObjectStorage` + a `WARNING` naming the missing artifact |
| either | either | either | a consumer's own `ObjectStorage` bean always wins |

#### Scenario: required mode with no credentials fails at startup
- **WHEN** `storage.mode=required` and no credentials are configured
- **THEN** the application context fails to start with `StorageUnavailableException`, naming both missing properties

#### Scenario: required mode with credentials but no storage-s3 artifact fails at startup
- **WHEN** `storage.mode=required`, credentials are present, but `storage-s3` is not on the classpath
- **THEN** the application context fails to start with `StorageUnavailableException` saying to add the artifact

#### Scenario: optional mode with no credentials degrades to UnavailableObjectStorage
- **WHEN** `storage.mode=optional` and no credentials are configured
- **THEN** the context starts with `UnavailableObjectStorage` wired in, and every call refuses at request time

#### Scenario: optional mode with credentials but no storage-s3 artifact warns and degrades
- **WHEN** `storage.mode=optional`, credentials are present, but `storage-s3` is not on the classpath
- **THEN** `UnavailableObjectStorage` is wired in with a `WARNING` naming the missing artifact

#### Scenario: A consumer's own ObjectStorage bean always wins
- **WHEN** a consumer defines its own `ObjectStorage` bean
- **THEN** that bean is used regardless of mode, credentials or classpath

### Requirement: Credentials alone decide isConfigured; a named bucket without a secret is S3Config's refusal
The presence of credentials, and NOTHING else, SHALL decide `isConfigured()`. A blank bucket with credentials
present SHALL be refused by `S3Config` by name instead — a deployment that named a bucket and forgot the secret key
has TRIED to configure storage, and the useful failure names which piece is missing.

#### Scenario: isConfigured depends only on credentials
- **WHEN** `isConfigured()` is evaluated
- **THEN** its answer depends only on whether credentials are present, not on any other property

#### Scenario: A named bucket without a secret key is refused by S3Config
- **WHEN** a bucket is named but the secret key is missing
- **THEN** `S3Config` refuses by naming the missing piece, since the deployment has clearly tried to configure
  storage

### Requirement: A present-but-empty property counts as absent
A property that is present and EMPTY SHALL be treated as absent. `access-key: ${STORAGE_S3_ACCESS_KEY:}` is what a
deployment writes so an unset environment variable still has a default, and `@ConditionalOnProperty` would call
that configured — which is why the decision is a bound-properties question in a `Condition` and a bean method
rather than an annotation.

#### Scenario: An empty bound property is treated as absent, not configured
- **WHEN** `storage.s3.access-key` is bound from `${STORAGE_S3_ACCESS_KEY:}` and the environment variable is unset
- **THEN** the property is treated as absent, not as configured
- **AND** the decision is made in a `Condition` and a bean method rather than by `@ConditionalOnProperty`

### Requirement: storage.* properties have no vendor defaults except mode and its S3-only defaults
`storage.*` SHALL be one namespace bound by one properties class, so an operator never has to know which jar reads
which key. No vendor default SHALL exist for endpoint, region, bucket, access key or secret key: a library that
defaulted an endpoint would let a deployment that forgot one still start and sign requests for somewhere nobody
chose.

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

#### Scenario: storage.mode defaults to required
- **WHEN** `storage.mode` is left unconfigured
- **THEN** it defaults to `required`

#### Scenario: No endpoint, region, bucket or credential has a vendor default
- **WHEN** `storage.s3.endpoint`, `storage.s3.region`, `storage.s3.bucket`, `storage.s3.access-key` or
  `storage.s3.secret-key` is left unconfigured
- **THEN** it has no default value at all, rather than a value pointing anywhere

#### Scenario: presign-ttl defaults to 15 minutes and path-style to false
- **WHEN** `storage.s3.presign-ttl` and `storage.s3.path-style` are left unconfigured
- **THEN** they default to `15m` and `false` respectively
