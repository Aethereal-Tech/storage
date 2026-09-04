package net.aetherealtech.storage;

/**
 * Storage is not configured, or the configured store cannot be reached.
 *
 * <p>One exception for both because they are the same thing to a caller: nothing can be written or
 * read, and no change to the REQUEST would help. A missing credential and a refused connection
 * differ only in who fixes it, and the message says which.
 *
 * <p><b>When it is thrown depends on the mode.</b> In {@code required} mode the Spring
 * autoconfiguration throws this while building the bean, so the context never comes up and no
 * request is served — a deployment with no bucket is broken rather than reduced, and startup is the
 * cheapest place to find that out. In {@code optional} mode
 * {@link UnavailableObjectStorage} throws it per call instead, and the consumer maps it to a 503.
 */
public class StorageUnavailableException extends StorageException {

    public StorageUnavailableException(final String message) {
        super(message);
    }

    public StorageUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
