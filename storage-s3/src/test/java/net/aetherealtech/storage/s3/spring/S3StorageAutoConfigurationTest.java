package net.aetherealtech.storage.s3.spring;

import static org.assertj.core.api.Assertions.assertThat;

import net.aetherealtech.storage.ObjectStorage;
import net.aetherealtech.storage.UnavailableObjectStorage;
import net.aetherealtech.storage.s3.S3ObjectStorage;
import net.aetherealtech.storage.spring.StorageAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The decision matrix: whether {@link S3StorageAutoConfiguration} contributes an
 * {@link S3ObjectStorage}, and how it defers to the core module's mode-driven fallback and to a
 * consumer's own bean.
 *
 * <p>Proven here rather than chased to a coverage percentage over property accessors — see this
 * module's pom for why {@code net.aetherealtech.storage.s3.spring} sits outside the JaCoCo gate. What
 * this class must get right is exactly the same shape of question
 * {@code net.aetherealtech.mail.spring.MailAutoConfigurationTest} answers for mail: which
 * implementation a given set of properties produces.
 */
class S3StorageAutoConfigurationTest {

    // A real endpoint is never dialled by any of these tests: building an S3Client is pure object
    // construction, and only an actual put/get/exists/delete call would open a socket. Present so
    // S3Config's own validation — endpoint and bucket both required — does not fail bean creation
    // before the condition under test even runs.
    private static final String ENDPOINT = "storage.s3.endpoint=http://localhost:1";
    private static final String REGION = "storage.s3.region=eu-central-1";
    private static final String BUCKET = "storage.s3.bucket=kapar-photos";
    private static final String ACCESS_KEY = "storage.s3.access-key=AKIAEXAMPLE";
    private static final String SECRET_KEY = "storage.s3.secret-key=secret";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(S3StorageAutoConfiguration.class));

    @Nested
    @DisplayName("credentials present")
    class CredentialsPresent {

        @Test
        void selectsS3ObjectStorage() {
            runner.withPropertyValues(ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY)
                    .run(
                            context ->
                                    assertThat(context)
                                            .hasSingleBean(ObjectStorage.class)
                                            .getBean(ObjectStorage.class)
                                            .isInstanceOf(S3ObjectStorage.class));
        }
    }

    @Nested
    @DisplayName("credentials absent")
    class CredentialsAbsent {

        @Test
        @DisplayName("run alone, this autoconfiguration contributes no bean at all")
        void aloneContributesNothing() {
            runner.run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
        }

        @Test
        @DisplayName("run together with the core autoconfiguration, its mode-driven fallback wins")
        void coreFallbackWinsWhenRunTogether() {
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    S3StorageAutoConfiguration.class,
                                    StorageAutoConfiguration.class))
                    .withPropertyValues("storage.mode=optional")
                    .run(
                            context ->
                                    assertThat(context)
                                            .hasSingleBean(ObjectStorage.class)
                                            .getBean(ObjectStorage.class)
                                            .isInstanceOf(UnavailableObjectStorage.class));
        }
    }

    @Nested
    @DisplayName("present-but-blank, which is what an unset environment variable actually looks like")
    class PresentButBlank {

        @Test
        @DisplayName("a blank access-key behaves as absent")
        void blankAccessKeyBehavesAsAbsent() {
            runner.withPropertyValues(
                            ENDPOINT, REGION, BUCKET, "storage.s3.access-key=", SECRET_KEY)
                    .run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
        }

        @Test
        @DisplayName("a blank secret-key behaves as absent")
        void blankSecretKeyBehavesAsAbsent() {
            runner.withPropertyValues(
                            ENDPOINT, REGION, BUCKET, ACCESS_KEY, "storage.s3.secret-key=")
                    .run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
        }

        @Test
        @DisplayName("blank credentials fall back to the core module's UnavailableObjectStorage")
        void blankCredentialsFallBackWhenRunTogether() {
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    S3StorageAutoConfiguration.class,
                                    StorageAutoConfiguration.class))
                    .withPropertyValues(
                            "storage.mode=optional",
                            "storage.s3.access-key=",
                            "storage.s3.secret-key=")
                    .run(
                            context ->
                                    assertThat(context)
                                            .getBean(ObjectStorage.class)
                                            .isInstanceOf(UnavailableObjectStorage.class));
        }
    }

    @Nested
    class Wiring {

        @Test
        @DisplayName("a consumer's own ObjectStorage wins over both S3 and the core fallback")
        void consumerBeanWinsOverEverything() {
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    S3StorageAutoConfiguration.class,
                                    StorageAutoConfiguration.class))
                    .withUserConfiguration(OwnObjectStorageConfiguration.class)
                    .withPropertyValues(ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY)
                    .run(
                            context ->
                                    assertThat(context)
                                            .hasSingleBean(ObjectStorage.class)
                                            .getBean(ObjectStorage.class)
                                            .isSameAs(OwnObjectStorageConfiguration.INSTANCE));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnObjectStorageConfiguration {

        static final ObjectStorage INSTANCE = new UnavailableObjectStorage("the consumer's own");

        @Bean
        ObjectStorage ownObjectStorage() {
            return INSTANCE;
        }
    }
}
