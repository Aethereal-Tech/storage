package net.aetherealtech.storage.upload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * The door. Reads what an upload actually is and refuses anything an {@link UploadRule} does not
 * allow.
 *
 * <p>Build ONE per rule and share it — it is immutable and thread-safe, and a second instance is a
 * second place for the rule to drift.
 *
 * <pre>{@code
 * UploadRule rule = UploadRule.of(5 * 1024 * 1024, 32, UploadFormat.PNG, UploadFormat.JPEG);
 * UploadInspector inspector = new UploadInspector(rule);
 *
 * InspectedUpload upload = inspector.inspect(bytes);          // throws UploadRejectedException
 * storage.put(key, bytes, upload.contentType());              // never the declared content type
 * }</pre>
 *
 * <p><b>There is no overload taking a declared content type or a filename, and there will not be.</b>
 * Both are written by whoever is uploading. A HEIC announced as {@code image/png} passes a declared
 * check, is stored, is served back as a PNG, and is rendered by nothing.
 *
 * <p>Dimensions come from the header via an {@link ImageReader}, never from {@code ImageIO.read}
 * directly — an {@code ImageReader} only has to parse the header to answer {@code getWidth}/
 * {@code getHeight}, so a header that lies about its size is caught by
 * {@link UploadRule#maxDecodedPixels()} <b>before</b> anything allocates a buffer for the pixels it
 * describes. Only once that bound has cleared does {@link #inspect(byte[])} call
 * {@code ImageIO.read} at all, and only to prove the bytes actually decode — see
 * {@link UploadRejectedException.Reason#UNDECODABLE}. A format with no {@code ImageReader} on this
 * JDK (PDF, WEBP, HEIC, AVIF) never reaches either check: there is nothing here to decode it with,
 * and that is unchanged from before this class decoded anything.
 */
public class UploadInspector {

    private final UploadRule rule;

    public UploadInspector(final UploadRule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule must not be null");
    }

    public UploadRule rule() {
        return rule;
    }

    /**
     * Reads the bytes and either describes them or refuses them.
     *
     * <p>The order of the checks is the order of their cost, and it is load-bearing in two places:
     * size comes before anything that parses, so an oversized upload is refused without a reader
     * ever touching it; and the megapixel bound comes before {@code ImageIO.read}, so a header that
     * lies about its size is refused before anything allocates a buffer for the pixels it claims.
     * The full order is size → format → dimensions/minimum edge → megapixel bound → decode.
     *
     * <p>The last two run only when {@link InspectedUpload#hasDimensions()} — a format with no
     * {@code ImageReader} on this JDK cannot be measured OR decoded here, and that is unchanged
     * behaviour, not a gap: see {@link UploadRejectedException.Reason#UNDECODABLE}.
     *
     * @throws UploadRejectedException with a {@link UploadRejectedException.Reason} and the detected
     *     format
     */
    public InspectedUpload inspect(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new UploadRejectedException(
                    UploadRejectedException.Reason.UNREADABLE,
                    UploadFormat.UNKNOWN,
                    "The file is empty.");
        }
        if (bytes.length > rule.maxBytes()) {
            throw new UploadRejectedException(
                    UploadRejectedException.Reason.TOO_LARGE,
                    UploadFormat.UNKNOWN,
                    "The file is %d bytes; at most %d are allowed."
                            .formatted(bytes.length, rule.maxBytes()));
        }
        final UploadFormat format = UploadFormat.sniff(bytes);
        if (!rule.allows(format)) {
            throw refuseFormat(format);
        }
        final int[] dimensions = readDimensions(bytes);
        final InspectedUpload upload =
                new InspectedUpload(format, bytes.length, dimensions[0], dimensions[1]);
        if (upload.hasDimensions()) {
            if (upload.longEdge() < rule.minLongEdge()) {
                throw new UploadRejectedException(
                        UploadRejectedException.Reason.TOO_SMALL,
                        format,
                        "The image is %d×%d; its longest side must be at least %d pixels."
                                .formatted(upload.width(), upload.height(), rule.minLongEdge()));
            }
            final long declaredPixels = (long) upload.width() * (long) upload.height();
            if (declaredPixels > rule.maxDecodedPixels()) {
                throw new UploadRejectedException(
                        UploadRejectedException.Reason.TOO_LARGE,
                        format,
                        "The image declares %d×%d — %d pixels; at most %d are allowed before it is"
                                        + " decoded."
                                .formatted(
                                        upload.width(),
                                        upload.height(),
                                        declaredPixels,
                                        rule.maxDecodedPixels()));
            }
            ensureDecodable(bytes, format);
        }
        return upload;
    }

    /**
     * Proves the pixels described by the header actually decode, for whatever {@code ImageIO} can
     * both measure AND decode on this JDK — which by construction is every caller of this method,
     * since {@link #inspect(byte[])} only reaches here when {@link #readDimensions(byte[])} found a
     * reader.
     *
     * <p>Read from a FRESH stream over the same bytes: the {@link ImageReader} used to measure
     * dimensions has already consumed its stream and is disposed by the time this runs.
     */
    private static void ensureDecodable(final byte[] bytes, final UploadFormat format) {
        final BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (final IOException | RuntimeException e) {
            // A corrupt or truncated raster reaches here either as an IOException from the decoder,
            // or — for some formats' plugins on genuinely malformed pixel data — as an unchecked
            // exception (an out-of-bounds array access, say). Both mean "this is not a real image",
            // never "let it through": the whole point of decoding is to catch what the header alone
            // cannot.
            throw undecodable(format);
        }
        if (decoded == null) {
            throw undecodable(format);
        }
    }

    private static UploadRejectedException undecodable(final UploadFormat format) {
        return new UploadRejectedException(
                UploadRejectedException.Reason.UNDECODABLE,
                format,
                "The file is not a decodable %s.".formatted(format));
    }

    /**
     * HEIC gets its own reason and its own sentence. Everything else shares one, because the useful
     * half of that message is the list of what IS allowed rather than the name of what was not.
     */
    private UploadRejectedException refuseFormat(final UploadFormat format) {
        if (format == UploadFormat.HEIC) {
            return new UploadRejectedException(
                    UploadRejectedException.Reason.HEIC_UNSUPPORTED,
                    format,
                    "HEIC photos are not supported — an iPhone produces them by default. Export the"
                            + " photo as a JPEG and upload that.");
        }
        return new UploadRejectedException(
                UploadRejectedException.Reason.UNSUPPORTED_FORMAT,
                format,
                "The file is %s; allowed: %s."
                        .formatted(
                                format == UploadFormat.UNKNOWN ? "not a recognised format" : format,
                                rule.allowed().stream()
                                        .map(Enum::name)
                                        .sorted()
                                        .collect(java.util.stream.Collectors.joining(", "))));
    }

    /**
     * The HEADER only, and {@code {0, 0}} whenever that cannot be read.
     *
     * <p><b>Unreadable dimensions PASS the minimum-size check</b>, which is the caller's contract
     * above: the format check has already established what this is, and refusing on an
     * {@link ImageReader} that cannot introspect a perfectly valid file would turn a library quirk
     * into a rejected upload. PNG and JPEG have readers in every JDK; the other formats depend on
     * what {@code javax.imageio} was given, so their minimum is enforced when a reader happens to
     * exist and skipped when it does not.
     */
    private static int[] readDimensions(final byte[] bytes) {
        try (ImageInputStream input =
                ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return new int[] {0, 0};
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return new int[] {0, 0};
            }
            final ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (final IOException | RuntimeException e) {
            // A truncated or malformed header reaches here as an IOException from the reader, or as
            // an IndexOutOfBounds from one of the format plugins. Both mean "no dimensions", not
            // "reject" — see the contract above.
            return new int[] {0, 0};
        }
    }
}
