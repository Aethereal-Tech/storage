package net.aetherealtech.storage;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Where an object lives in the bucket: a validated, slash-separated path.
 *
 * <p><b>A key is built from ids the product already owns, and NEVER from an uploaded filename.</b>
 * A filename is user input, and using one is a path traversal and a collision at once — two people
 * uploading {@code roof.jpg} would overwrite each other, and one uploading
 * {@code ../../other-tenant/logo} would write outside their own prefix. The validation below
 * refuses the shapes that make traversal possible, but that is a BACKSTOP: the rule is what you
 * compose the key FROM.
 *
 * <pre>{@code
 * ObjectKey.of("organizations/%d/logo".formatted(organizationId));
 * ObjectKey.join("listings", listingId.toString(), UUID.randomUUID().toString());
 * }</pre>
 *
 * <p><b>A DERIVED key overwrites; a GENERATED one does not.</b> An organization has one logo, so
 * its id answers "which object" and a URL already issued resolves to the new picture — right for a
 * logo. That is exactly why a listing photo gets a fresh {@code UUID} and its key RECORDED on the
 * row: a derived key would make two photos on a listing one photo. This class does not choose for
 * you; it only makes the key you chose expressible and safe.
 *
 * <p>Rejection is an {@link IllegalArgumentException} rather than a {@link StorageException}: a key
 * is assembled from values the caller controls, so a bad one is a programming error at the
 * composition site, not a refusal an end user can act on. The one thing a caller must NOT do is
 * pass user input here and let this class be the validation.
 *
 * @param value the key itself, exactly as it is sent to the store
 */
public record ObjectKey(String value) implements Comparable<ObjectKey> {

    /**
     * S3's own limit, in UTF-8 bytes; the check below is in chars, which is stricter for anything
     * non-ASCII and never laxer. A store with a shorter limit says so itself — a library-invented
     * number below this one would refuse keys a consumer's store accepts.
     */
    public static final int MAX_LENGTH = 1024;

    public ObjectKey {
        Objects.requireNonNull(value, "key must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "key must be at most " + MAX_LENGTH + " characters, was: " + value.length());
        }
        if (value.startsWith("/")) {
            throw new IllegalArgumentException("key must not start with '/', was: " + value);
        }
        if (value.indexOf('\\') >= 0) {
            // A backslash is a separator on one platform and an ordinary character in a key on
            // every store. Refusing it stops a Windows-shaped path from becoming a single literal
            // segment that no prefix listing will ever find.
            throw new IllegalArgumentException("key must not contain a backslash, was: " + value);
        }
        value.chars()
                .filter(Character::isISOControl)
                .findFirst()
                .ifPresent(
                        c -> {
                            throw new IllegalArgumentException(
                                    "key must not contain control characters, found U+%04X"
                                            .formatted(c));
                        });
        // -1 keeps the trailing empty segment a key like "listings/5/" would otherwise lose, so
        // that case is refused below rather than silently accepted.
        for (final String segment : value.split("/", -1)) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException(
                        "key must not contain an empty or whitespace-only segment, was: " + value);
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "key must not contain a '.' or '..' segment, was: " + value);
            }
        }
    }

    /** The key as one already-composed string. */
    public static ObjectKey of(final String value) {
        return new ObjectKey(value);
    }

    /**
     * The key as its segments, joined with {@code /}.
     *
     * <p>The form to prefer when any segment is a value rather than a literal: a segment that
     * itself contains a slash produces exactly the same key as if it had been concatenated, so the
     * validation above still sees — and refuses — a traversal smuggled inside one.
     */
    public static ObjectKey join(final String... segments) {
        Objects.requireNonNull(segments, "segments must not be null");
        if (segments.length == 0) {
            throw new IllegalArgumentException("at least one segment is required");
        }
        return new ObjectKey(
                Arrays.stream(segments)
                        .map(segment -> Objects.requireNonNull(segment, "a segment was null"))
                        .collect(Collectors.joining("/")));
    }

    /** Everything before the last {@code /}, or the empty string for a key with no prefix. */
    public String prefix() {
        final int lastSlash = value.lastIndexOf('/');
        return lastSlash < 0 ? "" : value.substring(0, lastSlash);
    }

    @Override
    public int compareTo(final ObjectKey other) {
        return value.compareTo(other.value);
    }

    /** The key itself, so a key interpolates into a log line or a message as what it is. */
    @Override
    public String toString() {
        return value;
    }
}
