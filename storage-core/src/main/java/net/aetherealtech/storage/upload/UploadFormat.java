package net.aetherealtech.storage.upload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * A format this library can RECOGNISE from an upload's leading bytes.
 *
 * <p><b>Recognising a format and accepting it are different things</b>, and keeping them apart is
 * the point of this enum. What an upload may BE is {@link UploadRule}'s decision; what an upload IS
 * has one answer, and knowing it is what lets a refusal say "that is a HEIC, export it as a JPEG"
 * instead of "unsupported file".
 *
 * <p>The list is deliberately wider than any one consumer allows. A format nobody accepts still
 * earns its place here if people upload it by accident — which is the entire case for
 * {@link #HEIC}, and most of the case for {@link #WEBP} and {@link #PDF}.
 */
public enum UploadFormat {

    /** {@code 89 50 4E 47}. */
    PNG("image/png"),

    /** {@code FF D8 FF}. Only the SOI marker and the first byte of the next one are checked. */
    JPEG("image/jpeg"),

    /** {@code GIF8}, covering both {@code GIF87a} and {@code GIF89a}. */
    GIF("image/gif"),

    /** {@code RIFF} at 0 and {@code WEBP} at 8 — the marker is not at the start. */
    WEBP("image/webp"),

    /** {@code BM}. Two bytes, which is why it is checked last among the fixed signatures. */
    BMP("image/bmp"),

    /** {@code II*\0} little-endian or {@code MM\0*} big-endian. */
    TIFF("image/tiff"),

    /** {@code %PDF}. Not an image, and recognised precisely so a refusal can say which it is. */
    PDF("application/pdf"),

    /**
     * ISO base media with a HEIF brand — what an iPhone produces by DEFAULT.
     *
     * <p>Named rather than lumped in with the unrecognised, because it is the one wrong format that
     * arrives by accident rather than by intent: no browser displays it, so storing one is a silent
     * failure that surfaces much later as a listing whose photos are blank for everybody. See
     * {@link UploadRejectedException.Reason#HEIC_UNSUPPORTED}.
     */
    HEIC("image/heic"),

    /** ISO base media with an AVIF brand. Its own value because browser support differs from HEIC's. */
    AVIF("image/avif"),

    /**
     * Nothing recognised it.
     *
     * <p>Its content type is {@code application/octet-stream} so that a consumer which allows this
     * value — storing arbitrary attachments, say — still hands the store something honest rather
     * than a guess.
     */
    UNKNOWN("application/octet-stream");

    /**
     * The brands that mean HEIF. {@code mif1} and {@code msf1} are the generic image and image
     * sequence brands rather than HEVC-specific ones, and a photo carrying one still opens nowhere
     * a JPEG opens, so they belong with the rest.
     */
    private static final Set<String> HEIC_BRANDS =
            Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1");

    private static final Set<String> AVIF_BRANDS = Set.of("avif", "avis");

    private final String contentType;

    UploadFormat(final String contentType) {
        this.contentType = contentType;
    }

    /** The media type to STORE the object under, and to serve it back with. */
    public String contentType() {
        return contentType;
    }

    /**
     * What these bytes actually are, read from the leading MAGIC BYTES.
     *
     * <p><b>Never from a declared content type or a filename extension.</b> Both are written by
     * whoever is uploading; a HEIC announced as {@code image/png} would otherwise sail straight
     * through, be stored, be served back as a PNG, and be rendered by nothing.
     *
     * <p>Each signature checks only the bytes IT needs. A single length floor of twelve — WebP's,
     * because its marker sits at offset 8 — would make a valid eight-byte PNG stub come back
     * {@link #UNKNOWN}.
     */
    public static UploadFormat sniff(final byte[] bytes) {
        if (bytes == null) {
            return UNKNOWN;
        }
        if (startsWith(bytes, 0x89, 'P', 'N', 'G')) {
            return PNG;
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (startsWith(bytes, 'G', 'I', 'F', '8')) {
            return GIF;
        }
        if (startsWith(bytes, '%', 'P', 'D', 'F')) {
            return PDF;
        }
        if (startsWith(bytes, 'I', 'I', 0x2A, 0x00) || startsWith(bytes, 'M', 'M', 0x00, 0x2A)) {
            return TIFF;
        }
        if (bytes.length >= 12
                && startsWith(bytes, 'R', 'I', 'F', 'F')
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return WEBP;
        }
        final UploadFormat isoBaseMedia = sniffIsoBaseMedia(bytes);
        if (isoBaseMedia != UNKNOWN) {
            return isoBaseMedia;
        }
        // Last, and only two bytes long: 'B','M' is common enough as the start of some other file
        // that anything with a longer, more specific signature must have had its chance first.
        if (startsWith(bytes, 'B', 'M')) {
            return BMP;
        }
        return UNKNOWN;
    }

    /**
     * {@code ....ftyp} then a four-character brand — the container HEIC and AVIF share. The first
     * four bytes are the box size and carry no signature of their own, which is why the marker is
     * at offset 4 rather than 0.
     */
    private static UploadFormat sniffIsoBaseMedia(final byte[] bytes) {
        if (bytes.length < 12
                || bytes[4] != 'f'
                || bytes[5] != 't'
                || bytes[6] != 'y'
                || bytes[7] != 'p') {
            return UNKNOWN;
        }
        final String brand =
                new String(bytes, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        if (HEIC_BRANDS.contains(brand)) {
            return HEIC;
        }
        return AVIF_BRANDS.contains(brand) ? AVIF : UNKNOWN;
    }

    private static boolean startsWith(final byte[] bytes, final int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != (byte) signature[i]) {
                return false;
            }
        }
        return true;
    }
}
