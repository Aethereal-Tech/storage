package net.aetherealtech.storage.upload;

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
 * <p>Nothing here DECODES an image. Dimensions come from the header via an {@link ImageReader},
 * never from {@code ImageIO.read}, which would expand the pixels — a few hundred kilobytes becoming
 * hundreds of megabytes of heap is exactly the decompression bomb an upload endpoint must not be
 * open to.
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
     * <p>The order of the checks is the order of their cost, and it is load-bearing in one place:
     * size comes before anything that parses, so an oversized upload is refused without a reader
     * ever touching it.
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
        if (upload.hasDimensions() && upload.longEdge() < rule.minLongEdge()) {
            throw new UploadRejectedException(
                    UploadRejectedException.Reason.TOO_SMALL,
                    format,
                    "The image is %d×%d; its longest side must be at least %d pixels."
                            .formatted(upload.width(), upload.height(), rule.minLongEdge()));
        }
        return upload;
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
