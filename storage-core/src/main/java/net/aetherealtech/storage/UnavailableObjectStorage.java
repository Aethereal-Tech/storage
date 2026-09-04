package net.aetherealtech.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * The implementation installed in {@code optional} mode when no credentials are configured: every
 * call refuses with {@link StorageUnavailableException}, which a consumer maps to a 503.
 *
 * <p>A null object rather than a null bean, so nothing downstream has to ask whether storage exists.
 * The alternative — every caller checking — is how one call site ends up not checking, and a
 * developer with no credentials gets a {@code NullPointerException} instead of a message naming the
 * two properties to set.
 *
 * <p><b>{@link #exists} and {@link #delete} refuse too, and that is deliberate.</b> A null object
 * that answered "no, there is no logo" and swallowed deletes would make an unconfigured deployment
 * indistinguishable from a configured bucket that happens to be empty — a consumer would ship the
 * difference without noticing, and the screens would look right the whole time. A consumer that
 * genuinely wants the soft answer catches this exception at its own call site, where the decision is
 * visible and testable.
 */
public class UnavailableObjectStorage implements ObjectStorage {

    private final String message;

    /** With the library's own message, which names the two properties. */
    public UnavailableObjectStorage() {
        this(
                "Object storage is not configured. Set storage.s3.access-key and"
                        + " storage.s3.secret-key. The bucket must already exist; this library"
                        + " never creates one.");
    }

    /**
     * With the consumer's message. Worth using: only the consumer knows which environment variables
     * its deployment actually spells these as, and what stops working without them.
     */
    public UnavailableObjectStorage(final String message) {
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    @Override
    public void put(final ObjectKey key, final byte[] bytes, final String contentType) {
        throw refuse();
    }

    @Override
    public void put(
            final ObjectKey key,
            final InputStream stream,
            final long length,
            final String contentType) {
        throw refuse();
    }

    @Override
    public StoredObject get(final ObjectKey key) {
        throw refuse();
    }

    @Override
    public boolean exists(final ObjectKey key) {
        throw refuse();
    }

    @Override
    public void delete(final ObjectKey key) {
        throw refuse();
    }

    @Override
    public URI presignGet(final ObjectKey key, final Duration ttl) {
        throw refuse();
    }

    @Override
    public URI presignGet(final ObjectKey key) {
        throw refuse();
    }

    private StorageUnavailableException refuse() {
        return new StorageUnavailableException(message);
    }
}
