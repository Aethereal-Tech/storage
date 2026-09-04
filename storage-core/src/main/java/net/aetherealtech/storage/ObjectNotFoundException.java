package net.aetherealtech.storage;

/**
 * Nothing is stored under this key.
 *
 * <p>Usually a fault rather than an ordinary outcome. A key a database row points at was written
 * before the row and is deleted only after that row's transaction commits (see
 * {@code net.aetherealtech.storage.tx.AfterCommit}), so a pointer with nothing behind it means the
 * two stores have drifted. It is separate from a missing ENTITY — answering a caller 404 here would
 * tell them they asked for something that never existed, which is not what happened.
 *
 * <p>{@link ObjectStorage#exists} is the way to ASK, for the case where a key is derived and
 * nothing records whether an object was ever written under it.
 */
public class ObjectNotFoundException extends StorageException {

    private final ObjectKey key;

    public ObjectNotFoundException(final ObjectKey key, final Throwable cause) {
        super("No object at " + key, cause);
        this.key = key;
    }

    public ObjectKey key() {
        return key;
    }
}
