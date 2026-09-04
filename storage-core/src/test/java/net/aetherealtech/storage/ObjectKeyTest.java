package net.aetherealtech.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectKeyTest {

    @Nested
    class Accepted {

        @ParameterizedTest
        @ValueSource(strings = {
                "logo",
                "organizations/17/logo",
                "listings/5/9a7f-photo",
                "file.name.png",
                "организации/17/лого",
        })
        void validKeysAreAccepted(final String value) {
            assertThat(ObjectKey.of(value).value()).isEqualTo(value);
        }

        @Test
        void aKeyAtTheMaximumLengthIsAccepted() {
            final String value = "a".repeat(ObjectKey.MAX_LENGTH);

            assertThat(ObjectKey.of(value).value()).hasSize(ObjectKey.MAX_LENGTH);
        }

        @Test
        @DisplayName("\"..\" INSIDE a segment is fine — only a whole segment is traversal")
        void dotDotInsideASegmentIsAccepted() {
            assertThat(ObjectKey.of("a/..b/c").value()).isEqualTo("a/..b/c");
        }
    }

    @Nested
    class Rejected {

        @Test
        void nullValueThrows() {
            assertThatThrownBy(() -> ObjectKey.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("key");
        }

        @Test
        void emptyValueThrows() {
            assertThatThrownBy(() -> ObjectKey.of(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        void tooLongValueThrows() {
            final String value = "a".repeat(ObjectKey.MAX_LENGTH + 1);

            assertThatThrownBy(() -> ObjectKey.of(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1024");
        }

        @Test
        void leadingSlashThrows() {
            assertThatThrownBy(() -> ObjectKey.of("/logo"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("start with '/'");
        }

        @Test
        void backslashAnywhereThrows() {
            assertThatThrownBy(() -> ObjectKey.of("a\\b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("backslash");
        }

        @Test
        void controlCharacterThrows() {
            assertThatThrownBy(() -> ObjectKey.of("a\nb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("control characters");
        }

        @Test
        void dotDotSegmentThrows() {
            assertThatThrownBy(() -> ObjectKey.of("a/../b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'.' or '..'");
        }

        @Test
        void dotSegmentThrows() {
            assertThatThrownBy(() -> ObjectKey.of("a/./b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'.' or '..'");
        }

        @Test
        void emptySegmentThrows() {
            assertThatThrownBy(() -> ObjectKey.of("a//b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty or whitespace-only segment");
        }

        @Test
        void whitespaceOnlySegmentThrows() {
            assertThatThrownBy(() -> ObjectKey.of("a/ /b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty or whitespace-only segment");
        }

        @Test
        void trailingSlashThrows() {
            assertThatThrownBy(() -> ObjectKey.of("listings/5/"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty or whitespace-only segment");
        }
    }

    @Nested
    class Join {

        @Test
        void joinsSegmentsWithASlash() {
            assertThat(ObjectKey.join("listings", "5", "photo").value())
                    .isEqualTo("listings/5/photo");
        }

        @Test
        @DisplayName("a segment carrying its own '/' still passes through the full validation")
        void aSegmentContainingASlashIsStillValidated() {
            assertThatThrownBy(() -> ObjectKey.join("a", "../b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'.' or '..'");
        }

        @Test
        void nullSegmentThrows() {
            assertThatThrownBy(() -> ObjectKey.join("a", null, "b"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("segment was null");
        }

        @Test
        void nullSegmentsArrayThrows() {
            assertThatThrownBy(() -> ObjectKey.join((String[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("segments");
        }

        @Test
        void zeroSegmentsThrows() {
            assertThatThrownBy(ObjectKey::join)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one segment");
        }
    }

    @Nested
    class Prefix {

        @Test
        void aKeyWithASlashReturnsEverythingBeforeTheLastOne() {
            assertThat(ObjectKey.of("organizations/17/logo").prefix())
                    .isEqualTo("organizations/17");
        }

        @Test
        void aKeyWithNoSlashHasAnEmptyPrefix() {
            assertThat(ObjectKey.of("logo").prefix()).isEmpty();
        }
    }

    @Nested
    class ToStringAndOrdering {

        @Test
        void toStringReturnsTheRawValue() {
            assertThat(ObjectKey.of("organizations/17/logo")).hasToString("organizations/17/logo");
        }

        @Test
        void compareToOrdersByTheUnderlyingValue() {
            final ObjectKey a = ObjectKey.of("a");
            final ObjectKey b = ObjectKey.of("b");

            assertThat(a.compareTo(b)).isNegative();
            assertThat(b.compareTo(a)).isPositive();
            assertThat(a.compareTo(ObjectKey.of("a"))).isZero();
        }
    }
}
