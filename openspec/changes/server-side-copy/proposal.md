## Why

The only caller anyone has named for a server-side copy — `CopyObject` without pulling the bytes through the
application — is "duplicate a listing", and nobody has built listing duplication yet.

## What Changes

- `ObjectStorage` would gain a copy operation that asks the store to duplicate an object server-side, once a
  consumer builds a feature that needs to duplicate a stored object rather than upload a new one.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `storage-port`: `ObjectStorage` would gain a server-side copy method.

## Impact

`net.aetherealtech.storage.ObjectStorage` and `net.aetherealtech.storage.s3.S3ObjectStorage`.

## Status

**Tag.** PARKED.

**Why it waits.** The only caller anyone has named is "duplicate a listing", which nobody has built.

**Reopens when** a consumer builds a feature — such as listing duplication — that needs to duplicate a stored
object without pulling its bytes through the application.

**Prerequisites (owner).** A consumer, if that duplication feature ships.
