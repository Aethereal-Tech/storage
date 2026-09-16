## Why

Before this library existed, each consumer wrote its own object-storage client against its own entities. Each
consumer still owns the work of deleting that hand-rolled package and depending on this library instead — work that
belongs to each product's own release train, not to this repository.

## What Changes

None in this repository. Each consumer, on its own schedule, deletes its own `storage`-equivalent package (its own
`ObjectStorage`, its own S3 client, its own key-building code) and replaces it with `storage-core` plus, where
needed, `storage-s3`. A consumer with its own product-specific policy layer above the port — a key-naming
convention, an upload-rule catalogue — keeps that layer; only the hand-rolled transport and validation code
disappears.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — this change is entirely within each consumer's own repository.

## Impact

No files in this repository. Each consumer's own storage package and its dependency declarations.

## Status

**Tag.** PARKED.

**Why it waits.** Each is a change to a shipping product with its own release train, made on that product's own
schedule rather than this library's.

**Reopens when** each consumer schedules its own migration.

**Prerequisites (owner).** Each consumer, on its own schedule. This library reaching a stable-enough 0.x was the
prerequisite for starting; the migrations themselves remain each consumer's own work.
