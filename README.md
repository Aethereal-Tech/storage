# storage

A small object-storage library for Java, built to be shared between products that have nothing else
in common.

Its whole idea is a port that knows nothing about your domain: an object is a **key**, some **bytes**
and a **content type**. No `Listing`, no `Organization`, no `Order` appears in any signature here,
and none ever will — that is the mistake this library exists to undo. Three products wrote the same
S3 client against their own entities, and none of the three could be moved.

It also carries the two things that go wrong around object storage and are easy to get wrong the same
way twice: **keys built from filenames**, and **content types taken from whoever uploaded the file**.

**Requires JDK 25 or newer.** MIT licensed. Not affiliated with Amazon or Hetzner — the AWS SDK is
used as an S3 *protocol* client, and everything is decided by the endpoint you configure.

## What you get

- **`ObjectStorage`** — seven methods, three implementations:
  - **`S3ObjectStorage`** (separate artifact) — any S3-compatible service: Hetzner, MinIO, AWS.
  - **`InMemoryObjectStorage`** — a thread-safe map, for your own test suite. Its presigned URLs are
    stable and point at a host that must never resolve.
  - **`UnavailableObjectStorage`** — the null object for `optional` mode; every call refuses.
- **`ObjectKey`** — a validated key. No `..` segment, no leading `/`, no backslash, no control
  characters, no empty segment, at most 1024 characters.
- **Upload inspection** — `UploadRule` says what is allowed; `UploadInspector` reads what the bytes
  actually are from their **magic bytes** and refuses everything else, with a reason you can turn
  into a sentence. HEIC gets its own reason, because it is what an iPhone produces by default.
- **`AfterCommit`** — deletes the object once the transaction that dropped its row has committed.
- **Optional Spring Boot autoconfiguration** with two modes: refuse to start without storage, or run
  without it and refuse per call.

**Zero mandatory runtime dependencies** in the core artifact. Spring is `optional`; the AWS SDK is a
separate artifact you add only if you talk to a real store.

## Install

Two artifacts, always released together at the same version.

```xml
<dependency>
    <groupId>net.aetherealtech</groupId>
    <artifactId>storage-core</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Only if you talk to an S3-compatible service. This is the one that brings the AWS SDK. -->
<dependency>
    <groupId>net.aetherealtech</groupId>
    <artifactId>storage-s3</artifactId>
    <version>0.1.0</version>
</dependency>

<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Aethereal-Tech/storage</url>
    </repository>
</repositories>
```

### Authentication, and what a private repository changes

**GitHub Packages requires authentication even for public artifacts.** Anonymous Maven downloads
from `maven.pkg.github.com` return 401 regardless of repository visibility. That is a GitHub platform
limitation, not a choice made here.

**This repository is private, and that raises the bar.** A token with `read:packages` alone is not
enough — it must also be able to see this organization's private packages:

- a **classic** personal access token needs `read:packages` **and** `repo`; or
- a **fine-grained** token needs to be granted access to the `Aethereal-Tech/storage` repository,
  with **Contents: read** and the organization's **Packages: read** permission.

`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN</password>
    </server>
  </servers>
</settings>
```

The `<id>` must match the `<repository><id>` above.

**In a consuming repository's CI**, the workflow's own `GITHUB_TOKEN` never reaches a package in
another repository. GitHub Packages for Maven always inherit the permissions of the repository that
published them, and there is no per-package Actions access grant to widen that — no `permissions:`
configuration in the consuming workflow makes a `GITHUB_TOKEN` from elsewhere work. The only
credential that works is a token of the kind above, put in a secret and used on every Maven step
(Aethereal-Tech repositories use the organization secret `PACKAGES_READ_TOKEN`, wired into
`actions/setup-java` as `server-password: PACKAGES_READ_TOKEN`). A step that runs `mvn` without one
fails resolving the dependency, not at some later step that looks related.

The `permissions:` block does matter for a workflow reading this package from *within* this
repository, using the default `GITHUB_TOKEN`: if it declares one at all, it must include
`packages: read` — declaring any permission zeroes every permission not named, so a job with, say,
only `contents: read` cannot read the package even though the token is otherwise entitled to it.

## Quickstart — plain Java

```java
S3Config config = S3Config.of(
                URI.create("https://nbg1.your-objectstorage.com"),
                "nbg1",
                "my-bucket",
                System.getenv("STORAGE_S3_ACCESS_KEY"),
                System.getenv("STORAGE_S3_SECRET_KEY"))
        .withPresignTtl(Duration.ofMinutes(15));

try (S3ObjectStorage storage = new S3ObjectStorage(config)) {

    ObjectKey key = ObjectKey.join("organizations", organizationId.toString(), "logo");

    storage.put(key, bytes, "image/png");
    URI url = storage.presignGet(key);          // hand this to the browser
}
```

The bucket must already exist. This library never creates, configures or deletes one.

### What it puts on the wire, and why it is not the SDK's defaults

Two request-shape decisions are made for you, both because "S3-compatible" is a smaller set of
features than S3 itself.

**Virtual-hosted addressing** (`bucket.endpoint/key`). Hetzner — and AWS, which has deprecated
path-style — needs it for a presigned URL to work at all. `storage.s3.path-style` is the switch, and
MinIO is what it is for.

**No chunked encoding, and a checksum only where the operation requires one.** `PutObject` requires
a checksum per its own model. Left at its defaults the SDK satisfies that by streaming the body as
`aws-chunked` with a *trailing* CRC32 — a wire feature an arbitrary S3-compatible store is not
guaranteed to implement, and a store that does not simply refuses the write. Every write here hands
the SDK a body whose length is already known, so with chunking off the checksum is computed up front
and travels as an ordinary header, and the store receives a plain body: the request shape every
store this library targets already has to support. Worth knowing before you point this at something
exotic and wonder why the bytes on the wire are not what the AWS SDK would produce on its own.

Neither is configurable. A store that needs the opposite of either is a store this library has not
met yet, and the fix is a report rather than a property.

## Quickstart — Spring Boot

Add `storage-core` and `storage-s3`, set the properties, and inject the port. That is the whole
integration.

```java
@Service
class LogoService {

    private final ObjectStorage storage;
    private final UploadInspector inspector;

    LogoService(final ObjectStorage storage) {
        this.storage = storage;
        this.inspector = new UploadInspector(
                UploadRule.of(5 * 1024 * 1024, 32, UploadFormat.PNG, UploadFormat.JPEG));
    }

    void upload(final Long organizationId, final byte[] bytes) {
        InspectedUpload upload = inspector.inspect(bytes);
        storage.put(
                ObjectKey.join("organizations", organizationId.toString(), "logo"),
                bytes,
                upload.contentType());
    }
}
```

In your own tests, swap the bean:

```java
@Bean
@Primary
ObjectStorage objectStorage() {
    return new InMemoryObjectStorage();
}
```

### Properties

`storage.*`, one namespace. **Nothing has a vendor default** — a library that defaulted an endpoint
would let a deployment that forgot one start anyway and sign requests for somewhere nobody chose.

| Property | Environment variable | Default | |
|---|---|---|---|
| `storage.mode` | `STORAGE_MODE` | `required` | See below. |
| `storage.s3.endpoint` | `STORAGE_S3_ENDPOINT` | — | With its scheme. Hetzner: `https://nbg1.your-objectstorage.com`. |
| `storage.s3.region` | `STORAGE_S3_REGION` | — | The signing region. Hetzner's is the location code: `nbg1`, `fsn1`, `hel1`. |
| `storage.s3.bucket` | `STORAGE_S3_BUCKET` | — | Must already exist. |
| `storage.s3.access-key` | `STORAGE_S3_ACCESS_KEY` | — | Presence selects S3. Never logged. |
| `storage.s3.secret-key` | `STORAGE_S3_SECRET_KEY` | — | Never logged. |
| `storage.s3.presign-ttl` | `STORAGE_S3_PRESIGN_TTL` | `15m` | The lifetime `presignGet(key)` uses. Short on purpose — see below. |
| `storage.s3.path-style` | `STORAGE_S3_PATH_STYLE` | `false` | Virtual-hosted otherwise. Turn it on for MinIO and nothing else. |

### The two modes

`storage.mode` is the one design point this library carries as a mode, because its consumers
genuinely disagree and both are right about their own product.

| `storage.mode` | With credentials | Without credentials |
|---|---|---|
| `required` (default) | `S3ObjectStorage` | **the application does not start** — `StorageUnavailableException` while building the bean, naming the missing properties |
| `optional` | `S3ObjectStorage` | `UnavailableObjectStorage`; every call throws `StorageUnavailableException`, which you map to a 503 |

Pick `required` when every image in the product lives in the bucket: a deployment without one is
broken rather than reduced, and startup is the cheapest place to find that out. Pick `optional` when
the asset is decorative — a tenant with no logo is an ordinary tenant, and a developer, CI and your
integration tier then all run with no bucket and no setup.

A `Mailer`-style third option, where an unconfigured application quietly logs and carries on, is
deliberately absent. There is no useful thing to do with bytes nobody stored.

Which `ObjectStorage` you get, highest precedence first:

| Condition | Bean |
|---|---|
| you defined an `ObjectStorage` bean yourself | yours |
| credentials set, and `storage-s3` on the classpath | `S3ObjectStorage` |
| `storage.mode=optional` | `UnavailableObjectStorage`, with a `WARNING` saying which piece is missing |
| `storage.mode=required` | nothing — the context fails to start |

Credentials set with no `storage-s3` on the classpath is treated as the same failure as having no
credentials at all, with a message naming the missing artifact rather than the missing properties: a
half-configuration deserves a sentence about the half that is missing, not a silent fallback.

**A property that is present and empty counts as absent.** `access-key: ${STORAGE_S3_ACCESS_KEY:}` is
what a deployment writes so an unset variable has a default, and treating that as configured — which
is what `@ConditionalOnProperty` does — would select S3 on every machine that had never configured
anything. The failure would then surface as a 403 from the store at the first upload rather than as
anything a reader could see.

## Upload inspection, worked

You have bytes from a multipart request. You do **not** have a trustworthy content type.

```java
UploadRule rule = UploadRule.of(
        5 * 1024 * 1024,                    // maxBytes
        32,                                 // minLongEdge, in pixels
        UploadFormat.PNG, UploadFormat.JPEG);

UploadInspector inspector = new UploadInspector(rule);   // build ONE, share it
```

Build one and hand the same instance to every upload endpoint — and to the test that asserts your
file picker agrees with it. Two copies of the rule is two places for one to drift, and the drift is
invisible until somebody uploads through the door that was not updated.

```java
InspectedUpload upload = inspector.inspect(bytes);

storage.put(key, bytes, upload.contentType());   // the SNIFFED type, never the declared one
```

`inspect` either describes the upload or throws `UploadRejectedException`, which carries a reason and
the format it actually detected. The message is yours to write — this library ships no product
wording:

```java
catch (final UploadRejectedException e) {
    String message = switch (e.reason()) {
        case HEIC_UNSUPPORTED  -> "iPhone photos need exporting as JPEG first.";
        case UNSUPPORTED_FORMAT -> "Upload a PNG or a JPEG. That file is a " + e.format() + ".";
        case TOO_LARGE          -> "At most 5 MB.";
        case TOO_SMALL          -> "At least 32 pixels on the longest side.";
        case UNREADABLE         -> "That file is empty.";
    };
    throw new BadRequest(message);
}
```

Two things worth knowing about it:

- **`HEIC_UNSUPPORTED` is a separate reason from `UNSUPPORTED_FORMAT`** because HEIC is what an
  iPhone produces by *default*. It will be tried, by people with no idea their camera does anything
  unusual, and the only useful message names the format and says to export as a JPEG. "Unsupported
  file type" sends that person back to try the same photo again.
- **Dimensions that cannot be read pass the minimum-size check.** The format check has already
  established what the file is; refusing on an `ImageReader` that cannot introspect a perfectly valid
  file would turn a library quirk into a rejected upload. `TOO_SMALL` is only ever raised on a
  dimension actually read. PNG and JPEG have readers in every JDK.

Nothing here decodes an image. Dimensions come from the header, never from `ImageIO.read`, which
would expand the pixels — a few hundred kilobytes becoming hundreds of megabytes of heap is exactly
the decompression bomb an upload endpoint must not be open to.

## Three rules for whoever uses this

They are not enforced by the compiler, and each of them has been got wrong in a real product.

**1. Build keys from ids you own. Never from an uploaded filename.** A filename is user input: a path
traversal and a collision at once, since two people uploading `roof.jpg` overwrite each other, and
one uploading `../../other-tenant/logo` writes outside their own prefix. `ObjectKey` refuses the
shapes that make traversal possible, but that is a *backstop* — the rule is what you compose the key
from.

```java
ObjectKey.join("listings", listingId.toString(), UUID.randomUUID().toString());   // recorded on the row
ObjectKey.join("organizations", organizationId.toString(), "logo");               // derived from the id
```

Derive from the **id**, never a name: a rename would orphan every object written under the old one.
And note what deriving costs — a derived key **overwrites**, so a URL already issued resolves to the
new object. Right for a logo. Wrong for a photo, which is why a listing photo gets a fresh UUID and
its key recorded on its row: a derived key would make two photos on a listing one photo.

**2. Never trust the declared content type.** `UploadInspector` returns the one to store. There is no
overload that takes the caller's, and there will not be. A HEIC announced as `image/png` passes a
declared check, is stored, is served back as a PNG, and is rendered by nothing — a silent failure
that surfaces much later as a page whose images are blank for everybody.

**3. Delete the row first, the object after the commit.**

```java
@Transactional
public void removePhoto(final Long photoId) {
    ListingPhoto photo = photos.findById(photoId).orElseThrow();
    photos.delete(photo);
    AfterCommit.delete(storage, ObjectKey.of(photo.getObjectKey()));
}
```

This way a failed delete leaks an unreferenced object, which costs storage. The other way round
leaves a live row pointing at bytes that are gone — a broken image on a page, with nothing left to
repair it from. `AfterCommit` deletes immediately when no transaction is active, does nothing on
rollback, and logs rather than throws if the delete fails after the commit: your work already
succeeded, and failing the request would report a problem the caller cannot act on.

## And one operational rule

**Bucket CORS is hand-applied, once per environment, and you must read it back.**

A browser will not fetch a presigned URL cross-origin without it, and this library never touches
bucket configuration — a library that created a policy on demand would turn a typo in a bucket name
into a new, empty, silently-wrong bucket.

```bash
aws s3api put-bucket-cors --bucket my-bucket --endpoint-url https://nbg1.your-objectstorage.com \
  --cors-configuration '{"CORSRules":[{
      "AllowedOrigins":["https://example.com"],
      "AllowedMethods":["GET","HEAD"],
      "AllowedHeaders":["*"],
      "MaxAgeSeconds":3000}]}'

# Then, always:
aws s3api get-bucket-cors --bucket my-bucket --endpoint-url https://nbg1.your-objectstorage.com
```

**Read it back.** A policy that failed to apply looks exactly like one that was never set: the same
absent header, the same browser error, and nothing in the response to the `put` that says which
happened. `GET` and `HEAD` only — writes go through your server, which is the whole reason writes are
not presigned.

## Presigned URLs are bearer tokens

`presignGet` mints a URL carrying its own signature. For its lifetime, **anyone holding it reads that
object with no session and no ownership check**. Mint one only after those checks have passed, keep
the ttl short, and do not log one or put one in a URL that gets stored. That is the whole of the
protection, and it is why the default lifetime here is fifteen minutes rather than a day.

The one thing it buys is worth the care: a public page renders every image straight from the bucket,
with no token and nothing proxied through your API.

## Failure: it throws, and you decide

Everything extends `StorageException`, which is unchecked.

| Exception | Means |
|---|---|
| `StorageUnavailableException` | nothing is configured, or the store could not be reached. In `required` mode this is a startup failure; in `optional` mode it is every call. |
| `ObjectNotFoundException` | nothing is stored under that key. Usually a fault: the object outlives the row by construction, so a pointer with nothing behind it means the two stores have drifted. |
| `StorageAccessException` | the store answered and refused. `statusCode()` and `errorCode()` carry its own words for it. |
| `UploadRejectedException` | an upload was refused before it reached the store. `reason()` and `format()`; the only one here an end user caused and can fix. |

A 403 is worth reading twice: it is what **both** a bad secret key and a missing bucket produce,
because the store will not confirm a bucket's existence to a caller it has not authenticated.

## Testing your own code

`InMemoryObjectStorage` is the double. Your real controllers, real services and real database run
against it with no bucket and no network.

```java
InMemoryObjectStorage storage = new InMemoryObjectStorage();

logoService.upload(organizationId, pngBytes);

assertThat(storage.keys()).containsExactly(ObjectKey.of("organizations/17/logo"));
assertThat(storage.get(ObjectKey.of("organizations/17/logo")).contentType()).isEqualTo("image/png");
assertThat(storage.presignGet(key).toString()).contains("X-Amz-Signature");
```

Its presigned URL is **stable** for a given key and ttl, so you can assert on the whole thing — a
real signature is not stable, which is why nothing should assert on one. It points at
`https://storage.invalid`, a host that must never resolve, so a test that accidentally follows one
fails instead of reaching something.

Call `clear()` between tests. A cached Spring context keeps one instance for the whole run, and one
test's bytes are otherwise visible to the next.

What it deliberately cannot prove: that a presigned URL a real store mints actually works, or that
the bucket's CORS policy is set. Neither is assertable without the real service, and both belong to
the by-hand setup rather than to the code.

## Non-goals

Deliberately absent from 0.1.0. Each is a decision, not an oversight — see SPECS.md for which are
parked and on what.

- **No bucket creation, configuration or deletion.** Including CORS and lifecycle rules.
- **No streaming reads or multipart uploads.** `get` holds the whole object; every consumer's largest
  is a photo.
- **No `list(prefix)`, no server-side copy.** Nothing needs them yet.
- **No second provider.** All three consumers speak S3, and an adapter with no consumer is an
  untested adapter with a dependency.
- **No retries.** The SDK's own are switched off and a single call is bounded at ten seconds. A
  caller is sitting in a request thread; whether to try again is its decision, not a library's.
- **No image processing.** No resizing, no re-encoding, no thumbnails. Inspection reads the header
  and nothing decodes a pixel.

## Building

```
./mvnw clean verify
```

JDK 25. Runs both modules: the key validation table, every recognised format from real bytes, each
rejection reason, the `AfterCommit` transaction cases, the S3 adapter against WireMock, both Spring
decision matrices, and each module's JaCoCo gate (90% line, 80% branch). **No test reaches a bucket,
and none needs Docker or MinIO.**

## License

MIT — see [LICENSE](LICENSE).
