package net.aetherealtech.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoredObjectTest {

    private static final String CONTENT_TYPE = "image/png";

    @Nested
    class DefensiveCopying {

        @Test
        @DisplayName("mutating the array passed to the constructor does not change the object")
        void mutatingTheSourceArrayDoesNotReachTheObject() {
            final byte[] source = {1, 2, 3};
            final StoredObject stored = new StoredObject(source, CONTENT_TYPE);

            source[0] = 99;

            assertThat(stored.bytes()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("mutating what bytes() returned does not change the object either")
        void mutatingTheReturnedArrayDoesNotReachTheObject() {
            final StoredObject stored = new StoredObject(new byte[] {1, 2, 3}, CONTENT_TYPE);

            stored.bytes()[0] = 99;

            assertThat(stored.bytes()).containsExactly(1, 2, 3);
        }
    }

    @Nested
    class EqualsAndHashCode {

        @Test
        void equalWhenContentTypeAndBytesMatch() {
            final StoredObject a = new StoredObject(new byte[] {1, 2, 3}, CONTENT_TYPE);
            final StoredObject b = new StoredObject(new byte[] {1, 2, 3}, CONTENT_TYPE);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void unequalForDifferentBytes() {
            final StoredObject a = new StoredObject(new byte[] {1, 2, 3}, CONTENT_TYPE);
            final StoredObject b = new StoredObject(new byte[] {1, 2, 4}, CONTENT_TYPE);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void unequalForDifferentContentType() {
            final StoredObject a = new StoredObject(new byte[] {1, 2, 3}, CONTENT_TYPE);
            final StoredObject b = new StoredObject(new byte[] {1, 2, 3}, "image/jpeg");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void notEqualToAnUnrelatedType() {
            final StoredObject a = new StoredObject(new byte[] {1, 2, 3}, CONTENT_TYPE);

            assertThat(a).isNotEqualTo("not a StoredObject");
        }
    }

    @Nested
    class ToStringRepresentation {

        @Test
        void containsTheSize() {
            final StoredObject stored = new StoredObject(new byte[] {1, 2, 3}, CONTENT_TYPE);

            assertThat(stored).hasToString("StoredObject[contentType=image/png, size=3]");
        }

        @Test
        void doesNotContainTheByteContent() {
            final StoredObject stored =
                    new StoredObject("secret bytes".getBytes(StandardCharsets.UTF_8), CONTENT_TYPE);

            assertThat(stored.toString()).doesNotContain("secret bytes");
        }
    }

    @Test
    void sizeReturnsTheByteCount() {
        final StoredObject stored = new StoredObject(new byte[] {1, 2, 3, 4}, CONTENT_TYPE);

        assertThat(stored.size()).isEqualTo(4);
    }

    @Nested
    class Nulls {

        @Test
        void nullBytesThrows() {
            assertThatThrownBy(() -> new StoredObject(null, CONTENT_TYPE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("bytes");
        }

        @Test
        void nullContentTypeThrows() {
            assertThatThrownBy(() -> new StoredObject(new byte[] {1}, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("contentType");
        }
    }
}
