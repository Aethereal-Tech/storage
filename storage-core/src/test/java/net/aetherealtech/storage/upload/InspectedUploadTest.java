package net.aetherealtech.storage.upload;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectedUploadTest {

    @Test
    void contentTypeDelegatesToTheFormat() {
        final InspectedUpload upload = new InspectedUpload(UploadFormat.PNG, 100, 10, 10);

        assertThat(upload.contentType()).isEqualTo("image/png");
    }

    @Nested
    class HasDimensions {

        @Test
        void trueWhenBothWidthAndHeightArePositive() {
            assertThat(new InspectedUpload(UploadFormat.PNG, 100, 10, 20).hasDimensions()).isTrue();
        }

        @Test
        void falseWhenWidthIsZero() {
            assertThat(new InspectedUpload(UploadFormat.PNG, 100, 0, 20).hasDimensions()).isFalse();
        }

        @Test
        void falseWhenHeightIsZero() {
            assertThat(new InspectedUpload(UploadFormat.PNG, 100, 10, 0).hasDimensions()).isFalse();
        }

        @Test
        void falseWhenBothAreZero() {
            assertThat(new InspectedUpload(UploadFormat.PNG, 100, 0, 0).hasDimensions()).isFalse();
        }
    }

    @Nested
    class LongEdge {

        @Test
        void isTheWidthWhenWidthIsLarger() {
            assertThat(new InspectedUpload(UploadFormat.PNG, 100, 40, 10).longEdge()).isEqualTo(40);
        }

        @Test
        void isTheHeightWhenHeightIsLarger() {
            assertThat(new InspectedUpload(UploadFormat.PNG, 100, 10, 40).longEdge()).isEqualTo(40);
        }
    }

    @Test
    void nullFormatIsRejected() {
        assertThatThrownBy(() -> new InspectedUpload(null, 100, 10, 10))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("format");
    }
}
