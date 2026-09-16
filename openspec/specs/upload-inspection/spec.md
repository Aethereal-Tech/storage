# Upload inspection

## Purpose

Upload inspection covers `UploadInspector`, `UploadRule`, `UploadFormat` and `UploadRejectedException` — what byte
signature identifies each recognised format, the order the checks run in, why that order exists, and the three
refusal reasons that read like bugs and are not.

## Requirements

### Requirement: The declared content type is never an input
`UploadInspector(UploadRule).inspect(byte[])` SHALL NOT accept a declared content type anywhere in its signature.
The format SHALL be read from the magic bytes, and `InspectedUpload.contentType()` SHALL be what to store the object
under. A caller's `Content-Type` header is written by the caller; believing it is how a HEIC gets stored as a PNG and
rendered by nothing.

#### Scenario: No overload accepts a declared content type
- **WHEN** `UploadInspector.inspect` is called
- **THEN** its signature accepts only the raw bytes, no declared content type
- **AND** `InspectedUpload.contentType()` is derived from the sniffed magic bytes, not any caller-supplied value

### Requirement: Recognised formats are identified by their magic-byte signature
`UploadFormat` SHALL recognise exactly these formats by these signatures, each checked against only the bytes it
needs:

| Format | Signature | Content type |
|---|---|---|
| `PNG` | `89 50 4E 47` | `image/png` |
| `JPEG` | `FF D8 FF` | `image/jpeg` |
| `GIF` | `GIF8` | `image/gif` |
| `WEBP` | `RIFF` at 0, `WEBP` at 8 | `image/webp` |
| `BMP` | `BM` | `image/bmp` |
| `TIFF` | `II*\0` or `MM\0*` | `image/tiff` |
| `PDF` | `%PDF` | `application/pdf` |
| `HEIC` | `ftyp` at 4, brand `heic heix heim heis hevc hevx mif1 msf1` | `image/heic` |
| `AVIF` | `ftyp` at 4, brand `avif avis` | `image/avif` |
| `UNKNOWN` | nothing matched | `application/octet-stream` |

A single twelve-byte floor (WebP's) would make a valid eight-byte PNG stub come back `UNKNOWN`, so each signature
checks only the bytes it needs. `BM` is checked last, being two bytes long.

#### Scenario: Each format is recognised from its own signature
- **WHEN** bytes carrying one of the signatures above are inspected
- **THEN** `UploadFormat` recognises the corresponding format and content type

#### Scenario: A short valid PNG is not misclassified as UNKNOWN
- **WHEN** an eight-byte PNG stub carrying only the PNG signature is inspected
- **THEN** it is recognised as `PNG`, not `UNKNOWN`, because WebP's twelve-byte check is not applied to bytes that
  already matched a shorter signature

#### Scenario: Nothing matches
- **WHEN** bytes match none of the recognised signatures
- **THEN** the format is `UNKNOWN` with content type `application/octet-stream`

### Requirement: UploadRule has no presets or defaults except the megapixel bound
`UploadRule(Set<UploadFormat> allowed, long maxBytes, int minLongEdge, long maxDecodedPixels)` SHALL have NO presets
and NO defaults on `allowed`, `maxBytes` or `minLongEdge` — those are one product's policy, not the library's.
`maxDecodedPixels` SHALL be the one field with a library default, `UploadRule.DEFAULT_MAX_DECODED_PIXELS` at
50,000,000, applied by `UploadRule.of(maxBytes, minLongEdge, formats...)`, which keeps its three-argument-plus-varargs
shape. A consumer wanting a different bound SHALL use the canonical constructor.

#### Scenario: UploadRule.of applies the default megapixel bound
- **WHEN** `UploadRule.of(maxBytes, minLongEdge, formats...)` is used
- **THEN** the rule's `maxDecodedPixels` is `UploadRule.DEFAULT_MAX_DECODED_PIXELS`, 50,000,000

#### Scenario: The canonical constructor sets a different megapixel bound
- **WHEN** a consumer wants a `maxDecodedPixels` other than the default
- **THEN** it uses `UploadRule`'s canonical four-argument constructor

### Requirement: Checks run size, then format, then dimensions, then megapixel bound, then decode
`inspect` SHALL run its checks in this order: **size → format → dimensions/minimum edge → megapixel bound →
decode.**

| Reason | Raised when |
|---|---|
| `UNREADABLE` | no bytes at all |
| `TOO_LARGE` | over `maxBytes` (before anything parses, so an oversized upload never reaches a reader) **or** a declared `width × height` over `maxDecodedPixels` (before anything decodes) — the message names bytes or pixels accordingly |
| `HEIC_UNSUPPORTED` | a HEIC the rule does not allow; its own reason because it is the iPhone default and the message must say "export as JPEG" |
| `UNSUPPORTED_FORMAT` | any other format the rule does not allow, `UNKNOWN` included |
| `TOO_SMALL` | a long edge actually READ that is under `minLongEdge` |
| `UNDECODABLE` | dimensions were read, the megapixel bound cleared, but `ImageIO.read` on a fresh stream returned `null` or threw |

#### Scenario: No bytes at all is UNREADABLE
- **WHEN** `inspect` is called with no bytes
- **THEN** it throws `UploadRejectedException` with reason `UNREADABLE`

#### Scenario: Over maxBytes is TOO_LARGE before any parsing
- **WHEN** the byte count exceeds `maxBytes`
- **THEN** `inspect` refuses `TOO_LARGE`, naming bytes, before anything parses the content

#### Scenario: A HEIC the rule disallows is refused with its own reason
- **WHEN** the sniffed format is `HEIC` and the rule's `allowed` set does not include it
- **THEN** `inspect` refuses `HEIC_UNSUPPORTED`, distinct from `UNSUPPORTED_FORMAT`

#### Scenario: Any other disallowed format, including UNKNOWN, is UNSUPPORTED_FORMAT
- **WHEN** the sniffed format is not `HEIC` and is not in the rule's `allowed` set (including `UNKNOWN`)
- **THEN** `inspect` refuses `UNSUPPORTED_FORMAT`

#### Scenario: A long edge actually read under the minimum is TOO_SMALL
- **WHEN** a format with a working `ImageReader` reads a long edge under `minLongEdge`
- **THEN** `inspect` refuses `TOO_SMALL`

#### Scenario: Dimensions read, megapixel bound cleared, but ImageIO.read fails is UNDECODABLE
- **WHEN** dimensions were read successfully, the megapixel bound was cleared, and `ImageIO.read` on a fresh stream
  over the same bytes returns `null` or throws
- **THEN** `inspect` refuses `UNDECODABLE`

### Requirement: Dimensions come from the header; unreadable dimensions pass
Dimensions SHALL come from the header via a `javax.imageio` `ImageReader`, which only has to parse enough to answer
`getWidth`/`getHeight` — a header is free to lie about the pixels behind it, which is exactly what
`maxDecodedPixels` is checked against BEFORE anything calls `ImageIO.read`, the call that allocates a buffer sized
by what the header claims. Dimensions that could not be read SHALL PASS, and neither `TOO_SMALL`, the megapixel
bound, nor the decode step SHALL ever be raised on a guess: a format with no `ImageReader` on this JDK (PDF, WEBP,
HEIC, AVIF) reaches none of the three. PNG, JPEG, GIF and BMP have readers in every JDK; TIFF has since JDK 9.

#### Scenario: A format with no ImageReader on this JDK skips dimension, megapixel and decode checks
- **WHEN** an upload's sniffed format is PDF, WEBP, HEIC or AVIF, none of which has an `ImageReader` on this JDK
- **THEN** `TOO_SMALL`, the megapixel bound and `UNDECODABLE` are never raised on it, since its dimensions could not
  be read

#### Scenario: PNG, JPEG, GIF and BMP always have a reader; TIFF has since JDK 9
- **WHEN** an upload's sniffed format is PNG, JPEG, GIF or BMP
- **THEN** an `ImageReader` is available on this JDK to read its dimensions
- **AND** TIFF also has one, on JDK 9 and later

### Requirement: A megapixel bound over the declared header is TOO_LARGE before any decode
A declared `width × height` over `maxDecodedPixels` SHALL be refused as `TOO_LARGE`, naming the pixel count, BEFORE
`ImageIO.read` is ever called — closing the decompression-bomb route where a small file declares an enormous decoded
size.

#### Scenario: A declared pixel count over the bound is refused before decoding
- **WHEN** a header declares `width × height` over `maxDecodedPixels`
- **THEN** `inspect` refuses `TOO_LARGE`, naming the pixel count, without calling `ImageIO.read`

### Requirement: Acceptance requires the bytes to actually decode
Once a reader exists and the megapixel bound has cleared, `inspect` SHALL call `ImageIO.read` on a FRESH stream over
the same bytes — the reader used to measure dimensions is already spent and disposed — and SHALL refuse
`UNDECODABLE` on a `null` result or any exception. This is what catches a file carrying a valid signature and a
valid, correctly-sized header while its compressed pixel data is corrupt, which would otherwise pass every earlier
check and render as a broken image only once it is already stored.

#### Scenario: A corrupt-but-well-headed file is refused rather than stored
- **WHEN** a file carries a valid signature and a valid, correctly-sized header but corrupt compressed pixel data
- **THEN** `inspect` refuses it as `UNDECODABLE` rather than accepting it for storage

#### Scenario: Decoding reads a fresh stream, not the reader already used for dimensions
- **WHEN** `inspect` proceeds to the decode step
- **THEN** it calls `ImageIO.read` on a fresh stream over the same bytes, since the `ImageReader` used to measure
  dimensions is already spent and disposed
