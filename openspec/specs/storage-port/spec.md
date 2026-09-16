# Storage port

## Purpose

The storage port covers `ObjectStorage` itself — its seven methods, the absence of any domain type in its
signature — `StoredObject`, and the three implementations that exist against it today.

## Requirements

### Requirement: ObjectStorage is seven methods with no domain type in any signature
`net.aetherealtech.storage.ObjectStorage` SHALL expose exactly these seven methods, and no domain type SHALL appear
in any of them:

```java
void      put(ObjectKey key, byte[] bytes, String contentType);
void      put(ObjectKey key, InputStream stream, long length, String contentType);
StoredObject get(ObjectKey key);                 // throws ObjectNotFoundException
boolean   exists(ObjectKey key);                 // a HEAD
void      delete(ObjectKey key);                 // quiet if already gone
URI       presignGet(ObjectKey key, Duration ttl);
URI       presignGet(ObjectKey key);             // the implementation's configured lifetime
```

An object is a KEY, some BYTES and a CONTENT TYPE, and that is the whole vocabulary. Not a `Listing`, not an
`Organization`, not an `Order` — not in `ObjectStorage`, not in `ObjectKey`, not in the inspector, not "just for
convenience". If a change here would be easier with a domain type in the signature, the change is wrong.

#### Scenario: No domain type appears in the port
- **WHEN** `ObjectStorage`'s seven method signatures are inspected
- **THEN** none of them names a domain type — only `ObjectKey`, `byte[]`, `InputStream`, `long`, `String`,
  `StoredObject`, `boolean`, `URI` and `Duration` appear

#### Scenario: get throws ObjectNotFoundException
- **WHEN** `get` is called with a key nothing is stored under
- **THEN** it throws `ObjectNotFoundException`

#### Scenario: delete is quiet if the object is already gone
- **WHEN** `delete` is called with a key that does not exist
- **THEN** it completes without throwing

### Requirement: Reads are presigned, writes are not
Reads SHALL be presigned and writes SHALL NOT be presigned: a presigned GET is what makes a public page render
images with no session, and a write through the server is what lets the server enforce the bytes first.

#### Scenario: A public page renders images through a presigned GET
- **WHEN** a public page needs to render a stored image
- **THEN** it uses a presigned GET requiring no session

#### Scenario: A write goes through the server, never presigned
- **WHEN** an object is written
- **THEN** it goes through the server via `put`, never through a presigned upload URL
- **AND** this is what lets the server enforce the bytes first

### Requirement: The stream overload's length is mandatory
The stream `put` overload's `length` parameter SHALL be mandatory and undiscoverable from the stream itself: an S3
`PUT` needs a `Content-Length` up front, so an implementation given an unknown length would have to buffer the whole
object, which is the case this overload exists to avoid.

#### Scenario: Stream put requires an explicit length
- **WHEN** `put(ObjectKey, InputStream, long, String)` is called
- **THEN** the caller supplies the length explicitly
- **AND** no overload allows an unknown length that would force buffering the whole object

### Requirement: StoredObject copies in, copies out, and compares by value
`StoredObject(byte[] bytes, String contentType)` SHALL copy its byte array in on construction and copy it out on
`bytes()`, and SHALL compare by value, since a record's generated `equals` would otherwise compare the array by
identity.

#### Scenario: Mutating a caller's array after construction does not change the stored bytes
- **WHEN** a caller constructs a `StoredObject` from a byte array and then mutates that array
- **THEN** the `StoredObject`'s own bytes are unaffected

#### Scenario: Two StoredObjects with equal content are equal
- **WHEN** two `StoredObject` instances hold separately-allocated but content-equal byte arrays and the same content
  type
- **THEN** they are equal, even though a record's default `equals` would compare the arrays by identity

### Requirement: Three implementations exist: S3, in-memory, and unavailable
`S3ObjectStorage` (in `storage-s3`), `InMemoryObjectStorage` (thread-safe, for a consumer's own test suite, with a
stable fake presign URI at `https://storage.invalid` — a host that must never resolve) and `UnavailableObjectStorage`
(every call throws) SHALL be the three implementations of `ObjectStorage`.

#### Scenario: InMemoryObjectStorage is thread-safe and presigns a stable, non-resolving URI
- **WHEN** `InMemoryObjectStorage.presignGet` is called for a key
- **THEN** it returns a stable URI at `https://storage.invalid`, a host that must never resolve
- **AND** concurrent access from multiple threads is safe

#### Scenario: UnavailableObjectStorage throws on every call including exists and delete
- **WHEN** any method of `UnavailableObjectStorage` is called, including `exists` and `delete`
- **THEN** it throws `StorageUnavailableException`

### Requirement: UnavailableObjectStorage throws on exists rather than answering false
`UnavailableObjectStorage.exists` SHALL throw rather than answering `false`. A null object that answered "no logo"
would make `optional` mode indistinguishable from a configured bucket that happens to be empty, and a consumer would
ship the difference without noticing. A consumer that genuinely wants the soft answer catches
`StorageUnavailableException` at its own call site, where the decision is visible.

#### Scenario: exists throws instead of returning false when storage is unavailable
- **WHEN** `UnavailableObjectStorage.exists` is called
- **THEN** it throws `StorageUnavailableException` rather than returning `false`
- **AND** a consumer wanting a soft "no" catches the exception at its own call site
