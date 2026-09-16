## Why

`get` returns a `StoredObject` holding the whole object in memory, and `put` from a stream still sends it in one
request. Every consumer's largest object today is a 5 MB photo, so the API this would complicate buys nothing yet.

## What Changes

- `get` would gain a way to stream an object rather than buffer it whole — either an `InputStream` the caller must
  close, or a `Consumer<InputStream>` so the library can close it — and `put` would gain multipart upload for an
  object larger than memory.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `storage-port`: `get` and `put` would gain streaming variants alongside the existing whole-object methods.

## Impact

`net.aetherealtech.storage.ObjectStorage`, `net.aetherealtech.storage.s3.S3ObjectStorage`, and
`net.aetherealtech.storage.InMemoryObjectStorage`.

## Status

**Tag.** PARKED.

**Why it waits.** Every consumer's largest object is a 5 MB photo, so the API this would complicate buys nothing
yet, and the shape it should take — an `InputStream` the caller must close, or a `Consumer<InputStream>` so the
library can — is a decision better made against a real caller than guessed at in advance.

**Reopens when** a consumer needs to store a document rather than an image.

**Prerequisites (owner).** A consumer that stores a document rather than an image.
