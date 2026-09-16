## Why

A CORS policy that failed to apply looks exactly like one that was never set — the same absent header, the same
browser error, and nothing in the response to the original `put-bucket-cors` call that says which happened. A
method wrapping `get-bucket-cors` would let a deployment assert its policy actually applied.

## What Changes

- `storage-s3` would gain a read-only method wrapping `get-bucket-cors`, so a deployment — or a health check — can
  assert the bucket's CORS policy is what it was meant to be.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `s3-adapter`: the adapter would gain one read-only bucket-configuration call, narrowly scoped to reading CORS
  back, alongside the existing rule that it never creates, configures or deletes a bucket.

## Impact

`net.aetherealtech.storage.s3.S3ObjectStorage`.

## Status

**Tag.** PARKED.

**Why it waits.** It is a read against the bucket's CONFIGURATION, and this library's rule is that it never touches
bucket configuration at all. A read-only helper is arguably outside that rule, but it would be the first crack in
it, and the operational answer — `aws s3api get-bucket-cors` by hand, once per environment — already works.

**Reopens when** a consumer wants this in a health check rather than a by-hand step.

**Prerequisites (owner).** A consumer's request for it.
