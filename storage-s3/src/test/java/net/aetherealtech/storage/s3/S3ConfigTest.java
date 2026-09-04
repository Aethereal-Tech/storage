package net.aetherealtech.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class S3ConfigTest {

    private static final URI ENDPOINT = URI.create("https://nbg1.your-objectstorage.com");

    @Test
    void ofBuildsAConfigWithDefaults() {
        final S3Config config = S3Config.of(ENDPOINT, "nbg1", "kapar-photos", "AK", "SECRET");

        assertThat(config.endpoint()).isEqualTo(ENDPOINT);
        assertThat(config.region()).isEqualTo("nbg1");
        assertThat(config.bucket()).isEqualTo("kapar-photos");
        assertThat(config.accessKey()).isEqualTo("AK");
        assertThat(config.secretKey()).isEqualTo("SECRET");
        assertThat(config.presignTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(config.pathStyle()).isFalse();
    }

    @Test
    void endpointMustNotBeNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> S3Config.of(null, "nbg1", "bucket", "AK", "SECRET"))
                .withMessageContaining("endpoint");
    }

    @Test
    void regionMustNotBeNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, null, "bucket", "AK", "SECRET"))
                .withMessageContaining("region");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void regionMustNotBeBlank(final String region) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, region, "bucket", "AK", "SECRET"))
                .withMessageContaining("region");
    }

    @Test
    void bucketMustNotBeNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, "nbg1", null, "AK", "SECRET"))
                .withMessageContaining("bucket");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void bucketMustNotBeBlank(final String bucket) {
        // Named explicitly by the CLAUDE.md convention this record follows: a blank bucket must fail
        // BY NAME, distinct from a blank region or a blank key, since all four look identical as an
        // IllegalArgumentException unless the message says which field was empty.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, "nbg1", bucket, "AK", "SECRET"))
                .withMessageContaining("bucket");
    }

    @Test
    void accessKeyMustNotBeNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, "nbg1", "bucket", null, "SECRET"))
                .withMessageContaining("accessKey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void accessKeyMustNotBeBlank(final String accessKey) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, "nbg1", "bucket", accessKey, "SECRET"))
                .withMessageContaining("accessKey");
    }

    @Test
    void secretKeyMustNotBeNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, "nbg1", "bucket", "AK", null))
                .withMessageContaining("secretKey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void secretKeyMustNotBeBlank(final String secretKey) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> S3Config.of(ENDPOINT, "nbg1", "bucket", "AK", secretKey))
                .withMessageContaining("secretKey");
    }

    @Test
    void presignTtlDefaultsToFifteenMinutes() {
        final S3Config config =
                new S3Config(ENDPOINT, "nbg1", "bucket", "AK", "SECRET", null, false);

        assertThat(config.presignTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void presignTtlMustBePositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new S3Config(
                                        ENDPOINT,
                                        "nbg1",
                                        "bucket",
                                        "AK",
                                        "SECRET",
                                        Duration.ZERO,
                                        false))
                .withMessageContaining("presignTtl");
    }

    @Test
    void presignTtlMustNotBeNegative() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new S3Config(
                                        ENDPOINT,
                                        "nbg1",
                                        "bucket",
                                        "AK",
                                        "SECRET",
                                        Duration.ofSeconds(-1),
                                        false))
                .withMessageContaining("presignTtl");
    }

    @Test
    void withPresignTtlReplacesOnlyThatField() {
        final S3Config config =
                S3Config.of(ENDPOINT, "nbg1", "bucket", "AK", "SECRET")
                        .withPresignTtl(Duration.ofHours(1));

        assertThat(config.presignTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(config.bucket()).isEqualTo("bucket");
        assertThat(config.pathStyle()).isFalse();
    }

    @Test
    void withPathStyleReplacesOnlyThatField() {
        final S3Config config =
                S3Config.of(ENDPOINT, "nbg1", "bucket", "AK", "SECRET").withPathStyle(true);

        assertThat(config.pathStyle()).isTrue();
        assertThat(config.bucket()).isEqualTo("bucket");
        assertThat(config.presignTtl()).isEqualTo(Duration.ofMinutes(15));
    }
}
