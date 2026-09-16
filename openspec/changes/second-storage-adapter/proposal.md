## Why

A second adapter — GCS, Azure Blob — has been considered and decided against. All consumers known to this library
are on Hetzner, which speaks S3, and MinIO already covers local development.

## What Changes

None. This records a decision against building a second adapter, so the idea is not proposed again without new
information.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None.

## Impact

None — no code changes.

## Status

**Tag.** CUT.

**Why it waits.** It does not — all known consumers are on Hetzner, which speaks S3, and MinIO covers local
development. An adapter with no consumer is an untested adapter with a dependency, and the port is the thing that
makes adding one cheap later.

**Prerequisites (owner).** None; do not re-propose without a consumer that has actually chosen another provider.
