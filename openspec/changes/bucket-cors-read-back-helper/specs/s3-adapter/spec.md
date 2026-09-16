## MODIFIED Requirements

### Requirement: The adapter never creates, configures or deletes a bucket
The library SHALL NEVER create, configure or delete a bucket — not the bucket itself, not its CORS policy, not a
lifecycle rule — with ONE narrow exception: a read-only method wrapping `get-bucket-cors`, so a deployment can
assert an already-applied CORS policy is what it was meant to be. `storage-s3` SHALL otherwise write keys beneath a
bucket that already exists and do nothing else.

A policy that failed to apply looks exactly like one that was never set, which is what makes a read-back helper
worth the narrow exception to the rule, while creating or changing the policy itself stays entirely by hand.

#### Scenario: The adapter performs no bucket-management call
- **WHEN** `S3ObjectStorage` is used for any operation
- **THEN** it makes no call that creates, configures or deletes a bucket, its CORS policy or a lifecycle rule

#### Scenario: The one exception is a read-only CORS read-back
- **WHEN** `S3ObjectStorage` is used for any operation
- **THEN** the only bucket-configuration call it may make is the read-only helper that reads back an
  already-applied CORS policy
- **AND** it still never creates, configures or deletes a bucket or a lifecycle rule

#### Scenario: A deployment asserts its CORS policy actually applied
- **WHEN** a deployment or a health check calls the CORS read-back helper after applying a policy by hand
- **THEN** it can tell a policy that applied from one that silently failed to apply, which otherwise look identical
