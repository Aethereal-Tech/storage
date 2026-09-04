package net.aetherealtech.storage.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadInspectorTest {

    private static final UploadRule PNG_JPEG_RULE = UploadRule.of(5 * 1024 * 1024, 32, UploadFormat.PNG, UploadFormat.JPEG);

    @Nested
    class Unreadable {

        @Test
        void nullBytesAreUnreadable() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(null))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> {
                        final UploadRejectedException rejected = (UploadRejectedException) ex;
                        assertThat(rejected.reason()).isEqualTo(UploadRejectedException.Reason.UNREADABLE);
                        assertThat(rejected.format()).isEqualTo(UploadFormat.UNKNOWN);
                    });
        }

        @Test
        void emptyBytesAreUnreadable() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(new byte[0]))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).reason())
                            .isEqualTo(UploadRejectedException.Reason.UNREADABLE));
        }
    }

    @Nested
    class TooLarge {

        @Test
        void bytesOverTheLimitAreRejected() {
            final UploadInspector inspector = new UploadInspector(UploadRule.of(10, 0, UploadFormat.PNG));

            assertThatThrownBy(() -> inspector.inspect(png(1, 1)))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).reason())
                            .isEqualTo(UploadRejectedException.Reason.TOO_LARGE));
        }

        @Test
        @DisplayName("size is checked BEFORE the format — an oversized, unsupported-format upload is still TOO_LARGE")
        void sizeIsCheckedBeforeFormat() {
            final UploadInspector inspector = new UploadInspector(UploadRule.of(4, 0, UploadFormat.PNG));

            assertThatThrownBy(() -> inspector.inspect(ascii("%PDF-1.7")))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).reason())
                            .isEqualTo(UploadRejectedException.Reason.TOO_LARGE));
        }
    }

    @Nested
    class UnsupportedFormat {

        @Test
        void aGifAgainstAPngJpegRuleIsRejectedNamingTheDetectedFormat() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(ascii("GIF89a")))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> {
                        final UploadRejectedException rejected = (UploadRejectedException) ex;
                        assertThat(rejected.reason()).isEqualTo(UploadRejectedException.Reason.UNSUPPORTED_FORMAT);
                        assertThat(rejected.format()).isEqualTo(UploadFormat.GIF);
                    });
        }

        @Test
        void aWebpAgainstAPngJpegRuleIsRejected() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);
            final byte[] webp = concat(ascii("RIFF"), new byte[] {0, 0, 0, 0}, ascii("WEBP"));

            assertThatThrownBy(() -> inspector.inspect(webp))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).format()).isEqualTo(UploadFormat.WEBP));
        }

        @Test
        void aPdfAgainstAPngJpegRuleIsRejected() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(ascii("%PDF-1.7")))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).format()).isEqualTo(UploadFormat.PDF));
        }

        @Test
        void anUnknownFormatAgainstAPngJpegRuleIsRejected() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(new byte[] {1, 2, 3}))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).format()).isEqualTo(UploadFormat.UNKNOWN));
        }
    }

    @Nested
    class HeicUnsupported {

        @Test
        @DisplayName("HEIC against a PNG+JPEG rule gets its own reason and a message naming JPEG as the way out")
        void heicAgainstAPngJpegRuleIsRejectedWithItsOwnReason() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(isoBaseMedia("heic")))
                    .isInstanceOf(UploadRejectedException.class)
                    .hasMessageContaining("JPEG")
                    .satisfies(ex -> {
                        final UploadRejectedException rejected = (UploadRejectedException) ex;
                        assertThat(rejected.reason()).isEqualTo(UploadRejectedException.Reason.HEIC_UNSUPPORTED);
                        assertThat(rejected.reason()).isNotEqualTo(UploadRejectedException.Reason.UNSUPPORTED_FORMAT);
                    });
        }
    }

    @Nested
    class TooSmall {

        @Test
        void aOneByOnePngUnderTheMinimumIsRejected() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(png(1, 1)))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).reason())
                            .isEqualTo(UploadRejectedException.Reason.TOO_SMALL));
        }

        @Test
        void aOneByOneJpegUnderTheMinimumIsRejected() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThatThrownBy(() -> inspector.inspect(jpeg(1, 1)))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).reason())
                            .isEqualTo(UploadRejectedException.Reason.TOO_SMALL));
        }
    }

    @Nested
    class TooLargeDeclaredPixels {

        @Test
        @DisplayName("a header declaring 20000×20000 is refused before ImageIO.read ever runs, naming pixels")
        void aHandBuiltHeaderDeclaringABombIsRejectedBeforeDecoding() {
            final UploadInspector inspector = new UploadInspector(UploadRule.of(1024, 0, UploadFormat.PNG));
            final byte[] bomb = pngHeaderDeclaring(20_000, 20_000);

            assertThatThrownBy(() -> inspector.inspect(bomb))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> {
                        final UploadRejectedException rejected = (UploadRejectedException) ex;
                        assertThat(rejected.reason()).isEqualTo(UploadRejectedException.Reason.TOO_LARGE);
                        assertThat(rejected.getMessage()).contains("pixels");
                    });
        }

        @Test
        @DisplayName("a header declaring EXACTLY the bound clears the pixel check — \"over\", not \"at or over\"")
        void aHeaderAtExactlyTheBoundClearsThePixelCheck() {
            // The header-only bytes below have no IDAT/IEND, so they cannot decode either way; what
            // this proves is which check is REACHED. TOO_LARGE would mean the bound is "at or over";
            // UNDECODABLE proves 100 pixels against a bound of 100 cleared the pixel check and fell
            // through to the decode this rule's job is to guard.
            final UploadRule rule = new UploadRule(java.util.Set.of(UploadFormat.PNG), 1024 * 1024, 0, 100L);
            final UploadInspector inspector = new UploadInspector(rule);
            final byte[] atBound = pngHeaderDeclaring(10, 10);

            assertThatThrownBy(() -> inspector.inspect(atBound))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> assertThat(((UploadRejectedException) ex).reason())
                            .isEqualTo(UploadRejectedException.Reason.UNDECODABLE));
        }
    }

    @Nested
    class Undecodable {

        @Test
        @DisplayName("a PNG with a valid signature and header but a corrupt IDAT is UNDECODABLE, not accepted")
        void aPngWithAValidHeaderButCorruptPixelDataIsRejected() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);
            final byte[] corrupt = corruptPixelPng();

            assertThatThrownBy(() -> inspector.inspect(corrupt))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> {
                        final UploadRejectedException rejected = (UploadRejectedException) ex;
                        assertThat(rejected.reason()).isEqualTo(UploadRejectedException.Reason.UNDECODABLE);
                        assertThat(rejected.format()).isEqualTo(UploadFormat.PNG);
                    });
        }

        @Test
        @DisplayName("a JPEG whose header reads fine but whose scan data is corrupt is UNDECODABLE")
        void aJpegWithAValidHeaderButCorruptScanDataIsRejected() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);
            final byte[] corrupt = corruptScanJpeg(64, 48);

            assertThatThrownBy(() -> inspector.inspect(corrupt))
                    .isInstanceOf(UploadRejectedException.class)
                    .satisfies(ex -> {
                        final UploadRejectedException rejected = (UploadRejectedException) ex;
                        assertThat(rejected.reason()).isEqualTo(UploadRejectedException.Reason.UNDECODABLE);
                        assertThat(rejected.format()).isEqualTo(UploadFormat.JPEG);
                    });
        }
    }

    @Nested
    class HappyPath {

        @Test
        @DisplayName("the LONG edge is what counts, not the short one — 31x40 clears a minimum of 32")
        void aPngAtTheMinimumLongEdgePasses() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);
            final byte[] bytes = png(31, 40);

            final InspectedUpload upload = inspector.inspect(bytes);

            assertThat(upload.format()).isEqualTo(UploadFormat.PNG);
            assertThat(upload.contentType()).isEqualTo("image/png");
            assertThat(upload.size()).isEqualTo(bytes.length);
            assertThat(upload.width()).isEqualTo(31);
            assertThat(upload.height()).isEqualTo(40);
            assertThat(upload.hasDimensions()).isTrue();
            assertThat(upload.longEdge()).isEqualTo(40);
        }

        @Test
        void aJpegAtTheMinimumLongEdgePasses() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);
            final byte[] bytes = jpeg(31, 40);

            final InspectedUpload upload = inspector.inspect(bytes);

            assertThat(upload.format()).isEqualTo(UploadFormat.JPEG);
            assertThat(upload.contentType()).isEqualTo("image/jpeg");
            assertThat(upload.size()).isEqualTo(bytes.length);
            assertThat(upload.width()).isEqualTo(31);
            assertThat(upload.height()).isEqualTo(40);
            assertThat(upload.hasDimensions()).isTrue();
            assertThat(upload.longEdge()).isEqualTo(40);
        }

        @Test
        void aHeicAgainstARuleThatAllowsItPasses() {
            final UploadInspector inspector = new UploadInspector(UploadRule.of(1024, 0, UploadFormat.HEIC));

            final InspectedUpload upload = inspector.inspect(isoBaseMedia("heic"));

            assertThat(upload.format()).isEqualTo(UploadFormat.HEIC);
        }

        @Test
        @DisplayName("unreadable dimensions PASS the minimum-size check — a library quirk must not become a rejected upload")
        void unreadableDimensionsPassTheMinimum() {
            final UploadInspector inspector = new UploadInspector(UploadRule.of(1024, 32, UploadFormat.WEBP));
            final byte[] webp = concat(ascii("RIFF"), new byte[] {0, 0, 0, 0}, ascii("WEBP"));

            final InspectedUpload upload = inspector.inspect(webp);

            assertThat(upload.format()).isEqualTo(UploadFormat.WEBP);
            assertThat(upload.width()).isZero();
            assertThat(upload.height()).isZero();
            assertThat(upload.hasDimensions()).isFalse();
        }

        @Test
        void unreadableDimensionsPassTheMinimumForPdfToo() {
            final UploadInspector inspector = new UploadInspector(UploadRule.of(1024, 32, UploadFormat.PDF));

            final InspectedUpload upload = inspector.inspect(ascii("%PDF-1.7"));

            assertThat(upload.hasDimensions()).isFalse();
        }

        @Test
        @DisplayName("a truncated PNG (a valid 8-byte signature and nothing else) is accepted, not TOO_SMALL")
        void aTruncatedPngIsAcceptedNotTooSmall() {
            final UploadInspector inspector = new UploadInspector(UploadRule.of(1024, 32, UploadFormat.PNG));
            final byte[] truncated = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};

            final InspectedUpload upload = inspector.inspect(truncated);

            assertThat(upload.format()).isEqualTo(UploadFormat.PNG);
            assertThat(upload.hasDimensions()).isFalse();
        }
    }

    @Nested
    class Construction {

        @Test
        void nullRuleThrows() {
            assertThatThrownBy(() -> new UploadInspector(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("rule");
        }

        @Test
        void ruleReturnsTheRuleItWasBuiltWith() {
            final UploadInspector inspector = new UploadInspector(PNG_JPEG_RULE);

            assertThat(inspector.rule()).isSameAs(PNG_JPEG_RULE);
        }
    }

    private static byte[] png(final int width, final int height) {
        return image(width, height, "png");
    }

    private static byte[] jpeg(final int width, final int height) {
        return image(width, height, "jpg");
    }

    private static byte[] image(final int width, final int height, final String formatName) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, formatName, out);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * A PNG signature plus a bare {@code IHDR} chunk declaring {@code width}×{@code height}, and
     * nothing else — no {@code IDAT}, no {@code IEND}. The header CRC is zeroed rather than
     * computed: {@code ImageIO}'s {@code ImageReader.getWidth}/{@code getHeight} reads the chunk's
     * declared length and type and trusts the dimensions without verifying the checksum, which is
     * exactly the gap {@link UploadRule#maxDecodedPixels()} exists to close — a header this cheap to
     * forge must not be trusted to size a buffer.
     */
    private static byte[] pngHeaderDeclaring(final int width, final int height) {
        final byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        final ByteArrayOutputStream ihdrData = new ByteArrayOutputStream();
        ihdrData.writeBytes(bigEndianInt(width));
        ihdrData.writeBytes(bigEndianInt(height));
        ihdrData.writeBytes(new byte[] {8, 2, 0, 0, 0}); // bit depth, RGB, compression/filter/interlace
        final byte[] ihdrChunkData = ihdrData.toByteArray();
        return concat(
                signature,
                bigEndianInt(ihdrChunkData.length),
                ascii("IHDR"),
                ihdrChunkData,
                new byte[] {0, 0, 0, 0}); // CRC, never checked for a header-only read
    }

    private static byte[] bigEndianInt(final int value) {
        return new byte[] {
            (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        };
    }

    /**
     * Loaded from disk rather than built here: this is the exact 100-byte file a real browser
     * reported as a broken image — a valid signature, a valid 64×48 {@code IHDR}, and an
     * {@code IDAT} whose zlib stream fails with "invalid distance too far back". The point of using
     * the real artifact rather than a synthesised one is that this inspector must refuse precisely
     * what actually reached a user, not a stand-in for it.
     */
    private static byte[] corruptPixelPng() {
        try (InputStream resource =
                Objects.requireNonNull(
                        UploadInspectorTest.class.getResourceAsStream("/pixel-undecodable.png"),
                        "test resource pixel-undecodable.png is missing")) {
            return resource.readAllBytes();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A real, ImageIO-written JPEG whose header (SOF0, carrying the dimensions, and the SOS marker
     * that follows it) is left completely intact, truncated immediately after that header with a
     * stray {@code FF D8} — a second SOI marker — standing in for its entropy-coded scan data.
     *
     * <p>Plain truncation of the scan data does not reproduce the defect: the JDK's JPEG decoder is
     * lenient about a short or missing scan and decodes anyway. A stray marker where compressed
     * pixel data belongs is what a genuinely corrupted transfer looks like once it reaches the
     * decoder, and it is what reliably makes {@code ImageIO.read} throw while {@code getWidth}/
     * {@code getHeight} — which only has to parse as far as SOS — still succeed.
     */
    private static byte[] corruptScanJpeg(final int width, final int height) {
        final byte[] full = jpeg(width, height);
        int sosIndex = -1;
        for (int i = 0; i < full.length - 1; i++) {
            if ((full[i] & 0xFF) == 0xFF && (full[i + 1] & 0xFF) == 0xDA) {
                sosIndex = i;
                break;
            }
        }
        if (sosIndex < 0) {
            throw new IllegalStateException("no SOS marker found in a freshly written JPEG");
        }
        final int segmentLength = ((full[sosIndex + 2] & 0xFF) << 8) | (full[sosIndex + 3] & 0xFF);
        final int entropyStart = sosIndex + 2 + segmentLength;
        final byte[] truncated = java.util.Arrays.copyOf(full, entropyStart + 2);
        truncated[entropyStart] = (byte) 0xFF;
        truncated[entropyStart + 1] = (byte) 0xD8;
        return truncated;
    }

    /** The {@code ....ftyp<brand>} container HEIC and AVIF share; the leading four bytes are a box size the sniffer ignores. */
    private static byte[] isoBaseMedia(final String brand) {
        return concat(new byte[] {0, 0, 0, 24}, ascii("ftyp"), ascii(brand));
    }

    private static byte[] ascii(final String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(final byte[]... parts) {
        int length = 0;
        for (final byte[] part : parts) {
            length += part.length;
        }
        final byte[] result = new byte[length];
        int offset = 0;
        for (final byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
