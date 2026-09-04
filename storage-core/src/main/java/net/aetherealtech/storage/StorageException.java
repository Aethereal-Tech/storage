package net.aetherealtech.storage;

/**
 * Everything this library throws at RUNTIME, so a caller that only wants "storage went wrong" can
 * catch one type.
 *
 * <p>Unchecked, deliberately. A storage failure is not something most call sites can recover from,
 * and a checked exception would put a {@code try} block around every image write in three products
 * to rethrow the same thing.
 *
 * <p>An invalid {@link ObjectKey} is NOT one of these — see that class for why.
 */
public class StorageException extends RuntimeException {

    public StorageException(final String message) {
        super(message);
    }

    public StorageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
