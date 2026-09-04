package net.aetherealtech.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object storage for a consumer's own tests: a map.
 *
 * <p>Lets the real controllers, the real services and the real database run with no bucket and no
 * network. It is NOT a mock of the thing under test — the rules a consumer's suite asserts (who may
 * upload, which types are refused, that a logo URL appears in a payload) live ABOVE this and would
 * be just as true against Hetzner.
 *
 * <p>What it deliberately cannot prove: that a presigned URL a real store mints actually works, or
 * that the bucket's CORS policy is set. Neither is assertable without the real service, and both
 * belong to the by-hand setup rather than to the code. The S3 tier's WireMock tests cover the wire;
 * nothing covers the bucket.
 *
 * <p>Thread-safe, because a consumer's integration context is shared across a suite that may run in
 * parallel.
 */
public class InMemoryObjectStorage implements ObjectStorage {

    /**
     * The host a presigned URL points at. Not a real domain — it must never resolve, so a test that
     * accidentally follows one fails rather than reaching something.
     */
    public static final String PRESIGN_HOST = "https://storage.invalid";

    private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(15);

    private final Map<ObjectKey, StoredObject> objects = new ConcurrentHashMap<>();

    private final Duration presignTtl;

    public InMemoryObjectStorage() {
        this(DEFAULT_PRESIGN_TTL);
    }

    /** For a consumer asserting that its own configured lifetime reaches {@link #presignGet}. */
    public InMemoryObjectStorage(final Duration presignTtl) {
        this.presignTtl = Objects.requireNonNull(presignTtl, "presignTtl must not be null");
    }

    @Override
    public void put(final ObjectKey key, final byte[] bytes, final String contentType) {
        objects.put(key, new StoredObject(bytes, contentType));
    }

    @Override
    public void put(
            final ObjectKey key,
            final InputStream stream,
            final long length,
            final String contentType) {
        final byte[] bytes;
        try {
            // Exactly `length` bytes, not everything the stream has: an implementation that read to
            // the end would accept a stream whose length argument was wrong, and a consumer's test
            // would then pass against a store that will not.
            bytes = stream.readNBytes(Math.toIntExact(length));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length < length) {
            throw new IllegalArgumentException(
                    "stream ended after %d bytes, %d were declared".formatted(bytes.length, length));
        }
        objects.put(key, new StoredObject(bytes, contentType));
    }

    @Override
    public StoredObject get(final ObjectKey key) {
        return Optional.ofNullable(objects.get(key))
                .orElseThrow(() -> new ObjectNotFoundException(key, null));
    }

    @Override
    public boolean exists(final ObjectKey key) {
        return objects.containsKey(key);
    }

    @Override
    public void delete(final ObjectKey key) {
        objects.remove(key);
    }

    @Override
    public URI presignGet(final ObjectKey key, final Duration ttl) {
        // Shaped like the real thing — a path, an expiry and a signature parameter — so a consumer
        // asserting "the payload carries a fetchable link" asserts the shape a browser will get.
        // STABLE for a given key and ttl, so an assertion on the whole URL is possible; a real
        // signature is not stable, which is why nothing should assert on one.
        return URI.create(
                "%s/%s?X-Amz-Expires=%d&X-Amz-Signature=in-memory"
                        .formatted(PRESIGN_HOST, encodePath(key), ttl.toSeconds()));
    }

    @Override
    public URI presignGet(final ObjectKey key) {
        return presignGet(key, presignTtl);
    }

    /** Every key currently held, for a test that wants to assert nothing was left behind. */
    public Set<ObjectKey> keys() {
        return Set.copyOf(objects.keySet());
    }

    public int size() {
        return objects.size();
    }

    /**
     * Forgets everything.
     *
     * <p>A consumer's integration context caches this bean for the whole run, so without a reset
     * between tests every object written by every test stays resident — and one test's bytes are
     * visible to the next, which is its own hazard.
     */
    public void clear() {
        objects.clear();
    }

    private static String encodePath(final ObjectKey key) {
        // Per segment: URLEncoder would turn the separators themselves into %2F, and a '+' for a
        // space is form encoding rather than path encoding.
        return String.join(
                "/",
                java.util.Arrays.stream(key.value().split("/"))
                        .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                        .map(segment -> segment.replace("+", "%20"))
                        .toList());
    }
}
