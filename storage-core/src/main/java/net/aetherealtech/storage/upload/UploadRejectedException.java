package net.aetherealtech.storage.upload;

import net.aetherealtech.storage.StorageException;

/**
 * An upload was refused before it reached the store.
 *
 * <p>The one exception here a consumer should MAP rather than log: it is the only failure in this
 * library an end user caused and can fix, so it becomes a 400 with a sentence the uploader can act
 * on. Which sentence is the consumer's — this library ships no product wording — and
 * {@link #reason()} plus {@link #format()} are what it writes that sentence from.
 */
public class UploadRejectedException extends StorageException {

    /**
     * Why. Distinct values because the sentence a user needs differs for each, not because the
     * library does anything different with them.
     */
    public enum Reason {

        /** The format is not on the rule's list. {@link #format()} says what it actually was. */
        UNSUPPORTED_FORMAT,

        /**
         * Its own value, separate from {@link #UNSUPPORTED_FORMAT}, because HEIC is what an iPhone
         * produces by DEFAULT.
         *
         * <p>It WILL be tried, by people who have no idea their camera does anything unusual, and
         * the only useful message names the format and says to export as a JPEG. "Unsupported file
         * type" sends that person back to try the same photo again.
         */
        HEIC_UNSUPPORTED,

        /** More bytes than the rule allows. */
        TOO_LARGE,

        /**
         * Smaller than the rule's minimum long edge. Raised only on a dimension actually READ —
         * dimensions that could not be determined pass, see {@link UploadInspector}.
         */
        TOO_SMALL,

        /** No bytes at all. Not "a format nothing recognised" — that is {@link #UNSUPPORTED_FORMAT}. */
        UNREADABLE
    }

    private final Reason reason;

    private final UploadFormat format;

    public UploadRejectedException(
            final Reason reason, final UploadFormat format, final String message) {
        super(message);
        this.reason = reason;
        this.format = format;
    }

    public Reason reason() {
        return reason;
    }

    /** What the bytes turned out to be, which is {@link UploadFormat#UNKNOWN} when nothing matched. */
    public UploadFormat format() {
        return format;
    }
}
