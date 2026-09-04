package net.aetherealtech.storage;

/**
 * The store answered, and the answer was a refusal.
 *
 * <p>Distinct from {@link StorageUnavailableException}, which says nothing answered at all. This
 * one carries the provider's own status and error code because they are the only thing that
 * separates a wrong credential from a missing bucket from a policy that forbids the operation — all
 * of which look identical from the call site, and none of which the library can tell apart without
 * guessing at strings that differ per vendor.
 *
 * <p>A 403 from an S3-compatible store is the usual one, and it is worth reading it twice: it is
 * what both a bad secret key and a bucket that does not exist produce, because the store will not
 * confirm the existence of a bucket to a caller it has not authenticated.
 */
public class StorageAccessException extends StorageException {

    private final int statusCode;

    private final String errorCode;

    /**
     * @param statusCode the HTTP status the store answered, or {@code 0} if there was none
     * @param errorCode  the provider's own code ({@code AccessDenied}, {@code NoSuchBucket}, …), or
     *                   {@code null} if the response carried none
     */
    public StorageAccessException(
            final String message,
            final int statusCode,
            final String errorCode,
            final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
