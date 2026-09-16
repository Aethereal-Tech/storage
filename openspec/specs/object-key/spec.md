# Object key

## Purpose

The key rule covers `ObjectKey`'s validation, what it refuses and what it deliberately allows, how a derived key
differs from a recorded one, and why a rejection is unchecked.

## Requirements

### Requirement: A key is never built from an uploaded filename
`ObjectKey` SHALL be composed from ids the product already owns, NEVER from an uploaded filename. An uploaded
filename is user input: a path traversal and a collision at once, since two people uploading `roof.jpg` would
overwrite each other. Validation is a backstop, not the rule — the rule is composition from owned ids, stated
wherever a key is made.

#### Scenario: Two uploads of the same filename by different owners do not collide
- **WHEN** two different owners each upload a file named `roof.jpg`
- **THEN** their object keys do not collide, because neither key is built from the filename

### Requirement: ObjectKey validation refuses specific shapes
`ObjectKey` SHALL refuse: null, empty, over 1024 characters, a leading `/`, a backslash anywhere, any ISO control
character, and any segment that is empty, whitespace-only, `.` or `..`. Rejection SHALL be an
`IllegalArgumentException`, since a key is composed from values the caller controls, so a bad one is a programming
error at the composition site, not a refusal an end user can act on.

#### Scenario: Null or empty key is refused
- **WHEN** `ObjectKey` is constructed with a null or empty value
- **THEN** it throws `IllegalArgumentException`

#### Scenario: A key over 1024 characters is refused
- **WHEN** `ObjectKey` is constructed with a value over 1024 characters
- **THEN** it throws `IllegalArgumentException`

#### Scenario: A leading slash is refused
- **WHEN** `ObjectKey` is constructed with a value starting with `/`
- **THEN** it throws `IllegalArgumentException`

#### Scenario: A backslash anywhere is refused
- **WHEN** `ObjectKey` is constructed with a value containing a backslash
- **THEN** it throws `IllegalArgumentException`

#### Scenario: An ISO control character is refused
- **WHEN** `ObjectKey` is constructed with a value containing any ISO control character
- **THEN** it throws `IllegalArgumentException`

#### Scenario: An empty, whitespace-only, "." or ".." segment is refused
- **WHEN** `ObjectKey` is constructed with a segment that is empty, whitespace-only, `.` or `..`
- **THEN** it throws `IllegalArgumentException`

### Requirement: A ".." inside a segment is accepted; only a whole traversal segment is refused
A `..` INSIDE a segment (for example `a/..b/c`) SHALL be accepted — only a whole segment equal to `..` is traversal.
`join(String...)` SHALL join with `/` and validate the result, so a traversal smuggled inside a joined segment is
still caught.

#### Scenario: A ".." embedded inside a segment is accepted
- **WHEN** `ObjectKey` is constructed with a segment like `a/..b/c`
- **THEN** it is accepted, because `..b` is not a whole segment equal to `..`

#### Scenario: join validates the joined result
- **WHEN** `ObjectKey.join(String...)` is called with arguments that together spell a traversal segment
- **THEN** the joined result is validated and the traversal is refused

### Requirement: A derived key overwrites; a recorded key does not
A DERIVED key SHALL overwrite on a repeated write, so a URL already issued resolves to the new object — correct for
an organization logo, and wrong for a listing photo, which is why a listing photo instead gets a generated key
RECORDED on its own row. The library SHALL NOT choose between the two for a caller; it SHALL make the choice
expressible.

#### Scenario: A derived key's URL resolves to the newest object
- **WHEN** an object is written twice under the same derived key
- **THEN** a URL issued before the second write resolves to the object written by the second write

#### Scenario: A photo's key is recorded rather than derived, so it does not overwrite a sibling
- **WHEN** two photos are attached to the same owning record
- **THEN** each gets its own generated key recorded on its own row, so writing one does not overwrite the other
