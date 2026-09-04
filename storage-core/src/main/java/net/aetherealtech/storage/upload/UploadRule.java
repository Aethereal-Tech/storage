package net.aetherealtech.storage.upload;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What an upload is allowed to be: which formats, how large, and how small.
 *
 * <p><b>ONE rule at every door.</b> The value of this type is that a consumer builds it once and
 * hands the same instance to every upload endpoint — and to the test that asserts its own file
 * picker agrees with it. Two copies of the check is two places for one to drift, and the drift is
 * invisible until somebody uploads through the door that was not updated.
 *
 * <p>No defaults, and no presets. Five megabytes and thirty-two pixels are one product's policy, and
 * a library constant would make it three products' policy by accident.
 *
 * @param allowed          the formats an upload may be; must not be empty. Include
 *                         {@link UploadFormat#UNKNOWN} only if arbitrary bytes really are
 *                         acceptable — it means "whatever nothing recognised"
 * @param maxBytes         the largest upload accepted, in bytes; must be positive. Keep a
 *                         consumer's own transport limit (a servlet container's
 *                         {@code max-file-size}, say) ABOVE this, or the container refuses the
 *                         request first and the caller gets a generic failure instead of a sentence
 *                         saying what to do
 * @param minLongEdge      the smallest acceptable long edge in pixels, or {@code 0} for no minimum.
 *                         A junk floor rather than a quality bar — it exists to refuse the 8×8 that
 *                         renders as a grey square
 * @param maxDecodedPixels the largest {@code width × height} the header may DECLARE before
 *                         {@link UploadInspector} will hand it to {@code ImageIO.read}; must be
 *                         positive. A header is free to lie about its dimensions, and
 *                         {@code ImageIO.read} allocates a buffer for the pixels it is told about
 *                         before it has verified a single one of them — a hundred-byte PNG can
 *                         declare 100000×100000 and turn one upload into a multi-gigabyte
 *                         allocation. Checked BEFORE decoding, against the header read for
 *                         {@link InspectedUpload#width()}/{@link InspectedUpload#height()}, so it
 *                         costs nothing beyond the dimension read every upload already pays for
 */
public record UploadRule(
        Set<UploadFormat> allowed, long maxBytes, int minLongEdge, long maxDecodedPixels) {

    /** 50 megapixels — comfortably above any real photo, and nowhere near what a bomb declares. */
    public static final long DEFAULT_MAX_DECODED_PIXELS = 50_000_000L;

    public UploadRule {
        Objects.requireNonNull(allowed, "allowed must not be null");
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("at least one allowed format is required");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive, was: " + maxBytes);
        }
        if (minLongEdge < 0) {
            throw new IllegalArgumentException(
                    "minLongEdge must not be negative, was: " + minLongEdge);
        }
        if (maxDecodedPixels <= 0) {
            throw new IllegalArgumentException(
                    "maxDecodedPixels must be positive, was: " + maxDecodedPixels);
        }
        // Copied into an immutable EnumSet so a caller's mutable set cannot change the rule after
        // the doors have been built from it — which is the whole point of building it once.
        allowed = Set.copyOf(EnumSet.copyOf(allowed));
    }

    /**
     * The readable form at a call site: {@code UploadRule.of(5 * 1024 * 1024, 32, PNG, JPEG)}. The
     * decode bound is {@link #DEFAULT_MAX_DECODED_PIXELS} — use the canonical constructor to set a
     * different one.
     */
    public static UploadRule of(
            final long maxBytes, final int minLongEdge, final UploadFormat... allowed) {
        Objects.requireNonNull(allowed, "allowed must not be null");
        return new UploadRule(
                Arrays.stream(allowed)
                        .map(format -> Objects.requireNonNull(format, "a format was null"))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                maxBytes,
                minLongEdge,
                DEFAULT_MAX_DECODED_PIXELS);
    }

    public boolean allows(final UploadFormat format) {
        return allowed.contains(format);
    }
}
