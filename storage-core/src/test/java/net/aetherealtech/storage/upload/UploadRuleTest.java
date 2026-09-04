package net.aetherealtech.storage.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadRuleTest {

    @Nested
    class Validation {

        @Test
        void emptyAllowedThrows() {
            assertThatThrownBy(() -> new UploadRule(Set.of(), 1024, 0, UploadRule.DEFAULT_MAX_DECODED_PIXELS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one allowed format");
        }

        @Test
        void nullAllowedThrows() {
            assertThatThrownBy(() -> new UploadRule(null, 1024, 0, UploadRule.DEFAULT_MAX_DECODED_PIXELS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("allowed");
        }

        @Test
        void zeroMaxBytesThrows() {
            assertThatThrownBy(() -> new UploadRule(Set.of(UploadFormat.PNG), 0, 0, UploadRule.DEFAULT_MAX_DECODED_PIXELS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxBytes must be positive");
        }

        @Test
        void negativeMaxBytesThrows() {
            assertThatThrownBy(() -> new UploadRule(Set.of(UploadFormat.PNG), -1, 0, UploadRule.DEFAULT_MAX_DECODED_PIXELS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxBytes must be positive");
        }

        @Test
        void negativeMinLongEdgeThrows() {
            assertThatThrownBy(() -> new UploadRule(Set.of(UploadFormat.PNG), 1024, -1, UploadRule.DEFAULT_MAX_DECODED_PIXELS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minLongEdge must not be negative");
        }

        @Test
        void zeroMinLongEdgeIsLegal() {
            final UploadRule rule = new UploadRule(Set.of(UploadFormat.PNG), 1024, 0, UploadRule.DEFAULT_MAX_DECODED_PIXELS);

            assertThat(rule.minLongEdge()).isZero();
        }

        @Test
        void zeroMaxDecodedPixelsThrows() {
            assertThatThrownBy(() -> new UploadRule(Set.of(UploadFormat.PNG), 1024, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxDecodedPixels must be positive");
        }

        @Test
        void negativeMaxDecodedPixelsThrows() {
            assertThatThrownBy(() -> new UploadRule(Set.of(UploadFormat.PNG), 1024, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxDecodedPixels must be positive");
        }
    }

    @Test
    void theAllowedSetIsCopiedSoALaterMutationOfTheCallersSetDoesNotReachTheRule() {
        final Set<UploadFormat> source = EnumSet.of(UploadFormat.PNG);
        final UploadRule rule = new UploadRule(source, 1024, 0, UploadRule.DEFAULT_MAX_DECODED_PIXELS);

        source.add(UploadFormat.JPEG);

        assertThat(rule.allowed()).containsExactly(UploadFormat.PNG);
    }

    @Nested
    class Of {

        @Test
        void buildsARuleFromVarargs() {
            final UploadRule rule = UploadRule.of(5 * 1024 * 1024, 32, UploadFormat.PNG, UploadFormat.JPEG);

            assertThat(rule.maxBytes()).isEqualTo(5 * 1024 * 1024);
            assertThat(rule.minLongEdge()).isEqualTo(32);
            assertThat(rule.allowed()).containsExactlyInAnyOrder(UploadFormat.PNG, UploadFormat.JPEG);
        }

        @Test
        @DisplayName("the decode bound defaults to 50 megapixels — a source-compatible addition, not a required argument")
        void defaultsTheDecodeBoundTo50Megapixels() {
            final UploadRule rule = UploadRule.of(5 * 1024 * 1024, 32, UploadFormat.PNG);

            assertThat(rule.maxDecodedPixels()).isEqualTo(50_000_000L);
            assertThat(rule.maxDecodedPixels()).isEqualTo(UploadRule.DEFAULT_MAX_DECODED_PIXELS);
        }
    }

    @Nested
    class Allows {

        @Test
        void trueForAnAllowedFormat() {
            final UploadRule rule = UploadRule.of(1024, 0, UploadFormat.PNG);

            assertThat(rule.allows(UploadFormat.PNG)).isTrue();
        }

        @Test
        void falseForAFormatNotOnTheList() {
            final UploadRule rule = UploadRule.of(1024, 0, UploadFormat.PNG);

            assertThat(rule.allows(UploadFormat.JPEG)).isFalse();
        }
    }
}
