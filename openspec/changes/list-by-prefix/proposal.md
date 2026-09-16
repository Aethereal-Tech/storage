## Why

Nothing needs `list(prefix)` on the port today — a key is either derived (so it is already known) or recorded on a
row (so the database lists it). The first real use would be a reconciliation job finding objects no row points at,
which does not exist yet.

## What Changes

- `ObjectStorage` would gain a `list(prefix)` method once a real caller — a reconciliation job — needs to enumerate
  objects the database does not already know about.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `storage-port`: `ObjectStorage` would gain a `list(prefix)` method.

## Impact

`net.aetherealtech.storage.ObjectStorage`, `net.aetherealtech.storage.s3.S3ObjectStorage`, and
`net.aetherealtech.storage.InMemoryObjectStorage`.

## Status

**Tag.** PARKED.

**Why it waits.** Nothing needs it — a key is either derived (so it is known) or recorded on a row (so the database
lists it). The first real use is a reconciliation job finding objects no row points at, which does not exist yet.

**Reopens when** a consumer writes that reconciliation job.

**Prerequisites (owner).** Whoever writes that job.
