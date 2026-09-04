package net.aetherealtech.storage.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

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
