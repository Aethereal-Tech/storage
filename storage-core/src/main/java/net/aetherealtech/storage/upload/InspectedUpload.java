package net.aetherealtech.storage.upload;

import java.util.Objects;

/**
 * What an upload turned out to be, once {@link UploadInspector} accepted it.
 *
 * <p>{@link #contentType()} is the value to store the object under. It comes from the sniffed
 * format, never from anything the uploader declared, and that is the whole reason this type exists
 * rather than the inspector returning {@code void}: a caller with only a boolean would go back to
 * the multipart header for the content type and undo the check it just passed.
 *
 * @param format the format read from the leading bytes
 * @param size   how many bytes
 * @param width  in pixels, or {@code 0} when the dimensions could not be determined
 * @param height in pixels, or {@code 0} when the dimensions could not be determined
 */
public record InspectedUpload(UploadFormat format, int size, int width, int height) {

    public InspectedUpload {
        Objects.requireNonNull(format, "format must not be null");
    }

    /** What to store the object as, and to serve it back with. */
    public String contentType() {
        return format.contentType();
    }

    /** Whether {@link #width} and {@link #height} mean anything. */
    public boolean hasDimensions() {
        return width > 0 && height > 0;
    }

    /** The dimension a minimum is checked against, or {@code 0} when there are none. */
    public int longEdge() {
        return Math.max(width, height);
    }
}
