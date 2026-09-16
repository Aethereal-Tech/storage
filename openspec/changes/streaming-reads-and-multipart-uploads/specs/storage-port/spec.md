## ADDED Requirements

### Requirement: Streaming reads and multipart uploads for objects larger than memory
`ObjectStorage` SHALL gain a way to read an object as a stream rather than buffer it whole into a `StoredObject`,
and a way for `put` to upload an object larger than memory using multipart upload, once a real consumer needs to
store something larger than a photo.

#### Scenario: A document too large to buffer is read as a stream
- **WHEN** a consumer needs to read an object too large to hold comfortably in memory
- **THEN** `ObjectStorage` offers a streaming read that does not require the whole object to be buffered first

#### Scenario: A document too large for one request is written via multipart upload
- **WHEN** a consumer needs to write an object too large for a single request
- **THEN** `ObjectStorage` offers a multipart upload path rather than requiring the whole object in memory up front
