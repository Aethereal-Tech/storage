## ADDED Requirements

### Requirement: Server-side copy without pulling bytes through the application
`ObjectStorage` SHALL gain a copy operation that asks the store to duplicate an object server-side — `CopyObject`,
in S3 terms — without the application reading and rewriting the bytes itself, once a consumer builds a feature that
needs to duplicate a stored object.

#### Scenario: An object is duplicated without its bytes passing through the application
- **WHEN** a consumer needs to duplicate a stored object, such as when duplicating a listing and its photos
- **THEN** `ObjectStorage` offers a copy operation that the store performs server-side, without the bytes being
  read into and rewritten by the application
