package net.aetherealtech.storage.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UploadFormatTest {

    @Nested
    class RealPngBytes {

        @Test
        void aOneByOnePngSniffsAsPng() {
            assertThat(UploadFormat.sniff(png(1, 1))).isEqualTo(UploadFormat.PNG);
        }

        @Test
        void aThirtyOneByFortyPngSniffsAsPng() {
            assertThat(UploadFormat.sniff(png(31, 40))).isEqualTo(UploadFormat.PNG);
        }
    }

    @Nested
    class RealJpegBytes {

        @Test
        void aOneByOneJpegSniffsAsJpeg() {
            assertThat(UploadFormat.sniff(jpeg(1, 1))).isEqualTo(UploadFormat.JPEG);
        }

        @Test
        void aThirtyOneByFortyJpegSniffsAsJpeg() {
            assertThat(UploadFormat.sniff(jpeg(31, 40))).isEqualTo(UploadFormat.JPEG);
        }
    }

    @Nested
    class HandWrittenHeaders {

        @Test
        void gif89aSniffsAsGif() {
            assertThat(UploadFormat.sniff(ascii("GIF89a"))).isEqualTo(UploadFormat.GIF);
        }

        @Test
        void aWebpRiffContainerSniffsAsWebp() {
            final byte[] bytes = concat(ascii("RIFF"), new byte[] {0, 0, 0, 0}, ascii("WEBP"));

            assertThat(UploadFormat.sniff(bytes)).isEqualTo(UploadFormat.WEBP);
        }

        @Test
        void bmSniffsAsBmp() {
            final byte[] bytes = concat(ascii("BM"), new byte[] {0, 0, 0, 0, 0, 0});

            assertThat(UploadFormat.sniff(bytes)).isEqualTo(UploadFormat.BMP);
        }

        @Test
        void littleEndianTiffSniffsAsTiff() {
            final byte[] bytes = {'I', 'I', 0x2A, 0x00};

            assertThat(UploadFormat.sniff(bytes)).isEqualTo(UploadFormat.TIFF);
        }

        @Test
        void bigEndianTiffSniffsAsTiff() {
            final byte[] bytes = {'M', 'M', 0x00, 0x2A};

            assertThat(UploadFormat.sniff(bytes)).isEqualTo(UploadFormat.TIFF);
        }

        @Test
        void pdfHeaderSniffsAsPdf() {
            assertThat(UploadFormat.sniff(ascii("%PDF-1.7"))).isEqualTo(UploadFormat.PDF);
        }
    }

    @Nested
    class Heic {

        @ParameterizedTest
        @ValueSource(strings = {"heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1"})
        void everyHeicBrandSniffsAsHeic(final String brand) {
            assertThat(UploadFormat.sniff(isoBaseMedia(brand))).isEqualTo(UploadFormat.HEIC);
        }

        @Test
        @DisplayName("brand matching is case-insensitive")
        void anUpperCaseBrandIsAlsoRecognised() {
            assertThat(UploadFormat.sniff(isoBaseMedia("HEIC"))).isEqualTo(UploadFormat.HEIC);
        }
    }

    @Nested
    class Avif {

        @ParameterizedTest
        @ValueSource(strings = {"avif", "avis"})
        void everyAvifBrandSniffsAsAvif(final String brand) {
            assertThat(UploadFormat.sniff(isoBaseMedia(brand))).isEqualTo(UploadFormat.AVIF);
        }
    }

    @Nested
    class Unknown {

        @Test
        void nullBytesAreUnknown() {
            assertThat(UploadFormat.sniff(null)).isEqualTo(UploadFormat.UNKNOWN);
        }

        @Test
        void emptyBytesAreUnknown() {
            assertThat(UploadFormat.sniff(new byte[0])).isEqualTo(UploadFormat.UNKNOWN);
        }

        @Test
        void threeRandomBytesAreUnknown() {
            assertThat(UploadFormat.sniff(new byte[] {1, 2, 3})).isEqualTo(UploadFormat.UNKNOWN);
        }

        @Test
        @DisplayName("an ftyp container with an unrecognised brand is unknown, not HEIC or AVIF")
        void anUnrecognisedFtypBrandIsUnknown() {
            assertThat(UploadFormat.sniff(isoBaseMedia("qt  "))).isEqualTo(UploadFormat.UNKNOWN);
        }

        @Test
        @DisplayName("a RIFF container that is not WEBP (a WAV, say) is unknown")
        void aNonWebpRiffContainerIsUnknown() {
            final byte[] bytes = concat(ascii("RIFF"), new byte[] {0, 0, 0, 0}, ascii("WAVE"));

            assertThat(UploadFormat.sniff(bytes)).isEqualTo(UploadFormat.UNKNOWN);
        }
    }

    @Nested
    class SignatureBoundaries {

        @Test
        @DisplayName("each signature checks only the bytes IT needs — an eight-byte PNG stub still sniffs as PNG")
        void anEightBytePngStubStillSniffsAsPng() {
            final byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};

            assertThat(UploadFormat.sniff(bytes)).isEqualTo(UploadFormat.PNG);
        }
    }

    @Nested
    class ContentTypes {

        @ParameterizedTest
        @EnumSource(UploadFormat.class)
        void everyFormatReportsItsContentType(final UploadFormat format) {
            final String expected =
                    switch (format) {
                        case PNG -> "image/png";
                        case JPEG -> "image/jpeg";
                        case GIF -> "image/gif";
                        case WEBP -> "image/webp";
                        case BMP -> "image/bmp";
                        case TIFF -> "image/tiff";
                        case PDF -> "application/pdf";
                        case HEIC -> "image/heic";
                        case AVIF -> "image/avif";
                        case UNKNOWN -> "application/octet-stream";
                    };

            assertThat(format.contentType()).isEqualTo(expected);
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
