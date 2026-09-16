# S3 adapter

## Purpose

The S3 adapter covers `S3ObjectStorage` and `S3Config`: virtual-hosted addressing, how the client and presigner
share configuration, the one place errors are mapped and what they map to, retry and timeout behavior, the chunked
encoding and checksum decisions, what a presigned URL carries, and the bucket lifecycle boundary.

## Requirements

### Requirement: Virtual-hosted addressing by default; path-style exists only for MinIO
The adapter SHALL address virtual-hosted (`bucket.endpoint/key`) by default; `path-style` (`endpoint/bucket/key`)
SHALL exist only for MinIO. Hetzner — and AWS, which deprecated path-style — needs virtual-hosted addressing for
presigned URLs to work at all.

#### Scenario: Default addressing is virtual-hosted
- **WHEN** `storage.s3.path-style` is left at its default
- **THEN** requests address the bucket as `bucket.endpoint/key`

#### Scenario: path-style exists for MinIO
- **WHEN** `storage.s3.path-style=true`
- **THEN** requests address the bucket as `endpoint/bucket/key`
- **AND** this switch exists only so the adapter can be pointed at MinIO

### Requirement: The client and the presigner are built from one S3Config, configured identically
The client and the presigner SHALL be built from ONE `S3Config` and configured identically. A signature covers the
host, so a presigner disagreeing about addressing style mints URLs that fail to verify — an error a browser shows
that says nothing about addressing. There SHALL be no constructor taking two separately-configured clients.

#### Scenario: No constructor accepts a separately-configured presigner
- **WHEN** `S3ObjectStorage` is constructed
- **THEN** it takes one `S3Config` from which both the client and the presigner are built
- **AND** no constructor exists that would let the two disagree about addressing

### Requirement: Errors are mapped in one place, S3ObjectStorage.execute
Errors SHALL be mapped in ONE place, `S3ObjectStorage.execute`, so the mapping cannot differ per method:
`NoSuchKeyException` maps to `ObjectNotFoundException`; any other `S3Exception` carrying status 404 maps to
`ObjectNotFoundException` as well; every other `S3Exception` maps to `StorageAccessException` carrying the status
and the provider's error code (`null` when the response held no parseable error body); a transport failure that
never got an HTTP answer at all (`SdkClientException` — connection refused, DNS, timeout) maps to
`StorageUnavailableException`, naming the endpoint. `exists` SHALL answer `false` rather than propagating the
not-found, and `delete` SHALL swallow it so a retry is safe. A 403 SHALL be produced by BOTH a bad secret key and a
missing bucket, because the store will not confirm a bucket's existence to a caller it has not authenticated.

#### Scenario: NoSuchKeyException maps to ObjectNotFoundException
- **WHEN** the SDK raises `NoSuchKeyException`
- **THEN** `execute` maps it to `ObjectNotFoundException`

#### Scenario: Any other 404 S3Exception also maps to ObjectNotFoundException
- **WHEN** the SDK raises an `S3Exception` other than `NoSuchKeyException` carrying HTTP status 404
- **THEN** `execute` maps it to `ObjectNotFoundException`

#### Scenario: Every other S3Exception maps to StorageAccessException with status and error code
- **WHEN** the SDK raises an `S3Exception` that is not a 404
- **THEN** `execute` maps it to `StorageAccessException` carrying the HTTP status and the provider's error code
- **AND** the error code is `null` when the response held no parseable error body

#### Scenario: A transport failure with no HTTP answer maps to StorageUnavailableException
- **WHEN** the SDK raises `SdkClientException` for a connection refused, a DNS failure or a timeout
- **THEN** `execute` maps it to `StorageUnavailableException`, naming the endpoint

#### Scenario: exists answers false rather than propagating not-found
- **WHEN** `exists` is called for a key that does not exist
- **THEN** it answers `false` rather than throwing

#### Scenario: delete swallows not-found so a retry is safe
- **WHEN** `delete` is called for a key that does not exist
- **THEN** it completes without throwing, so calling `delete` again on the same key is safe

#### Scenario: A 403 is produced by both a bad secret key and a missing bucket
- **WHEN** a 403 is returned by the store
- **THEN** it is understood to mean either a bad secret key or a missing bucket, because the store will not confirm
  a bucket's existence to a caller it has not authenticated

### Requirement: Which 404 branch a call takes depends on the operation
Both 404 branches (the typed `NoSuchKeyException` and the generic `S3Exception` at status 404) SHALL be reachable,
and which one a given 404 takes SHALL depend on the OPERATION. The SDK's generated unmarshaller for `HeadObject`
synthesizes a typed `NoSuchKeyException` for ANY 404, body or none, because that operation is documented to have no
other error. `GetObject` has more modelled errors, so a 404 with no parseable `<Code>` — an empty body, a proxy's
own 404 page — cannot be defaulted that way and reaches the generic `S3Exception` branch instead. This SHALL be
measured by a dedicated test for each branch, not assumed.

#### Scenario: HeadObject's 404 always synthesizes NoSuchKeyException
- **WHEN** a `HeadObject` call (behind `exists`) receives any 404, with or without a body
- **THEN** the SDK synthesizes a typed `NoSuchKeyException`

#### Scenario: GetObject's unparseable 404 reaches the generic S3Exception branch
- **WHEN** a `GetObject` call receives a 404 with no parseable `<Code>` — an empty body or a proxy's own 404 page
- **THEN** the SDK does not synthesize `NoSuchKeyException`, and the generic `S3Exception` branch is reached instead

### Requirement: Retries are off and a call is bounded at ten seconds
Retries SHALL be off — `AwsRetryStrategy.doNotRetry()`, one attempt — with a ten-second `apiCallTimeout` and
`apiCallAttemptTimeout`. A caller sits in a request thread waiting on this, so a refused connection or a slow store
MUST fail fast rather than be retried silently, for tens of seconds, inside a library the caller does not control.
Whether to retry is the caller's decision. `retryStrategy` SHALL be used rather than the deprecated
`retryPolicy(RetryPolicy.none())`; both say zero retries and only one survives the SDK's move off `RetryPolicy`.

#### Scenario: A 503 that the SDK's default strategy would retry produces exactly one request
- **WHEN** the store answers 503 — a status the SDK's default retry strategy would retry
- **THEN** exactly one request is made, because retries are configured off

#### Scenario: A call is bounded at ten seconds
- **WHEN** a call to the store does not complete
- **THEN** it is bounded by a ten-second `apiCallTimeout` and `apiCallAttemptTimeout`

### Requirement: Chunked encoding is disabled and checksum calculation is WHEN_REQUIRED
Chunked encoding SHALL be disabled, and request checksum calculation SHALL be `WHEN_REQUIRED` rather than the SDK's
default `WHEN_SUPPORTED`. `PutObject` requires a checksum per its own model; left alone the SDK satisfies that by
streaming the body as `aws-chunked` with a TRAILING CRC32, a wire feature an arbitrary S3-compatible store is not
guaranteed to implement, and Hetzner is the target. Every write here hands the SDK a length-known body already in
hand, so with chunking off the checksum is computed up front and sent as an ordinary header, and the store receives
a plain body. `WHEN_REQUIRED` closes the second, independent route by which an opportunistic checksum could reach
the wire.

#### Scenario: A PUT sends a plain body with the checksum as an ordinary header
- **WHEN** an object is written with `put`
- **THEN** the request body is sent as a plain, non-chunked body
- **AND** the checksum travels as an ordinary header rather than a trailing CRC32 on an `aws-chunked` body

### Requirement: A presigned URL carries the standard SigV4 query parameters and makes no HTTP request
A presigned URL SHALL carry `X-Amz-Algorithm=AWS4-HMAC-SHA256`, `X-Amz-Credential`, `X-Amz-Date`, `X-Amz-Expires`
(exactly the ttl in seconds), `X-Amz-SignedHeaders` and `X-Amz-Signature`. Presigning SHALL make no HTTP request.

#### Scenario: A presigned URL carries the standard SigV4 parameters
- **WHEN** `presignGet` is called
- **THEN** the returned URI carries `X-Amz-Algorithm=AWS4-HMAC-SHA256`, `X-Amz-Credential`, `X-Amz-Date`,
  `X-Amz-Expires` set to the ttl in seconds, `X-Amz-SignedHeaders` and `X-Amz-Signature`

#### Scenario: Presigning makes no HTTP request
- **WHEN** `presignGet` is called
- **THEN** no HTTP request is made to the store

### Requirement: The adapter never creates, configures or deletes a bucket
The library SHALL NEVER create, configure or delete a bucket — not the bucket itself, not its CORS policy, not a
lifecycle rule. `storage-s3` SHALL write keys beneath a bucket that already exists and do nothing else. A library
that created one on demand would make a typo in a bucket name into a new, empty, silently-wrong bucket.

#### Scenario: The adapter performs no bucket-management call
- **WHEN** `S3ObjectStorage` is used for any operation
- **THEN** it makes no call that creates, configures or deletes a bucket, its CORS policy or a lifecycle rule
