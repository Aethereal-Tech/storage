package net.aetherealtech.storage;

import java.util.Arrays;
import java.util.Objects;

/**
 * What came back out of the store: the bytes, and the content type they were stored under.
 *
 * <p>The bytes are copied in and copied out, so nothing a caller keeps a reference to can change
 * what another caller reads. Bytes rather than a stream because a stream is single-use: it could
 * not be handed to two callers, asserted on twice, or re-read after a retry — and the size is
 * already bounded by whatever {@link net.aetherealtech.storage.upload.UploadRule} let in. Streaming
 * an object too large to hold in memory is a separate method this library does not yet have; see
 * {@code openspec/changes/streaming-reads-and-multipart-uploads}.
 *
 * @param bytes       the object's contents
 * @param contentType the media type recorded when it was stored, never the one a caller declared at
 *                    upload — see {@link net.aetherealtech.storage.upload.UploadInspector}
 */
public record StoredObject(byte[] bytes, String contentType) {

    public StoredObject {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /** How many bytes, without copying them to find out. */
    public int size() {
        return bytes.length;
    }

    /**
     * By VALUE. A record's generated {@code equals} compares an array by identity, which would make
     * two objects holding the same bytes unequal and quietly weaken every assertion written against
     * this type.
     */
    @Override
    public boolean equals(final Object other) {
        return other instanceof StoredObject stored
                && contentType.equals(stored.contentType)
                && Arrays.equals(bytes, stored.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * contentType.hashCode() + Arrays.hashCode(bytes);
    }

    /** The size, not the bytes: the generated one would print an array's identity hash. */
    @Override
    public String toString() {
        return "StoredObject[contentType=%s, size=%d]".formatted(contentType, bytes.length);
    }
}
