package net.aetherealtech.storage.spring;

import net.aetherealtech.storage.ObjectKey;
import net.aetherealtech.storage.ObjectStorage;
import net.aetherealtech.storage.StorageUnavailableException;
import net.aetherealtech.storage.UnavailableObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The decision matrix: which {@link ObjectStorage} a given mode and set of credentials produces.
 *
 * <p>This is what the {@code spring} package has to get right, and why it is proven here rather
 * than chased to a coverage percentage over property accessors — see {@code storage-core/pom.xml}.
 */
class StorageAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class));

    @Nested
    @DisplayName("decision matrix")
    class DecisionMatrix {

        @Test
        @DisplayName("default mode, no credentials: fails startup, naming both properties")
        void defaultModeWithNoCredentialsFailsStartup() {
            runner.run(
                    context ->
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(StorageUnavailableException.class)
                                    .hasMessageContaining("storage.s3.access-key")
                                    .hasMessageContaining("storage.s3.secret-key"));
        }

        @Test
        @DisplayName("required mode, no credentials: fails startup the same way")
        void requiredModeWithNoCredentialsFailsStartup() {
            runner.withPropertyValues("storage.mode=required")
                    .run(
                            context ->
                                    assertThat(context.getStartupFailure())
                                            .rootCause()
                                            .isInstanceOf(StorageUnavailableException.class)
                                            .hasMessageContaining("storage.s3.access-key")
                                            .hasMessageContaining("storage.s3.secret-key"));
        }

        @Test
        @DisplayName("optional mode, no credentials: starts with UnavailableObjectStorage")
        void optionalModeWithNoCredentialsStartsUnavailable() {
            runner.withPropertyValues("storage.mode=optional")
                    .run(
                            context ->
                                    assertThat(context)
                                            .getBean(ObjectStorage.class)
                                            .isInstanceOf(UnavailableObjectStorage.class));
        }

        @Test
        @DisplayName("required mode, credentials present, no S3 artifact: fails startup naming it")
        void requiredModeWithCredentialsFailsNamingTheArtifact() {
            runner.withPropertyValues(
                            "storage.mode=required",
                            "storage.s3.access-key=access",
                            "storage.s3.secret-key=secret")
                    .run(
                            context ->
                                    assertThat(context.getStartupFailure())
                                            .rootCause()
                                            .isInstanceOf(StorageUnavailableException.class)
                                            .hasMessageContaining("net.aetherealtech:storage-s3"));
        }

        @Test
        @DisplayName("optional mode, credentials present, no S3 artifact: starts unavailable, message names the artifact")
        void optionalModeWithCredentialsStartsUnavailableNamingTheArtifact() {
            runner.withPropertyValues(
                            "storage.mode=optional",
                            "storage.s3.access-key=access",
                            "storage.s3.secret-key=secret")
                    .run(
                            context -> {
                                final ObjectStorage storage = context.getBean(ObjectStorage.class);
                                assertThat(storage).isInstanceOf(UnavailableObjectStorage.class);
                                assertThatThrownBy(() -> storage.exists(ObjectKey.of("x")))
                                        .hasMessageContaining("net.aetherealtech:storage-s3");
                            });
        }

        @Test
        @DisplayName("a consumer's own ObjectStorage bean always wins")
        void consumersOwnBeanWins() {
            runner.withUserConfiguration(OwnObjectStorageConfiguration.class)
                    .run(
                            context ->
                                    assertThat(context)
                                            .hasSingleBean(ObjectStorage.class)
                                            .getBean(ObjectStorage.class)
                                            .isSameAs(OwnObjectStorageConfiguration.STORAGE));
        }
    }

    @Nested
    @DisplayName("present-but-empty, which is what an unset environment variable actually looks like")
    class PresentButEmpty {

        @Test
        @DisplayName("blank access-key and secret-key behave exactly like absent ones")
        void blankCredentialsBehaveLikeAbsentOnes() {
            runner.withPropertyValues("storage.s3.access-key=", "storage.s3.secret-key=")
                    .run(
                            context ->
                                    assertThat(context.getStartupFailure())
                                            .rootCause()
                                            .isInstanceOf(StorageUnavailableException.class)
                                            .hasMessageContaining("storage.s3.access-key")
                                            .hasMessageContaining("storage.s3.secret-key"));
        }
    }

    @Nested
    class PropertiesBinding {

        // Bound with mode=optional throughout so the context always starts, even with no
        // credentials — the failing paths are already proven by the decision matrix above, and a
        // failed context has no StorageProperties bean to inspect.
        private final ApplicationContextRunner optionalRunner =
                runner.withPropertyValues("storage.mode=optional");

        @Test
        void isConfiguredIsFalseWhenBothKeysAreAbsent() {
            optionalRunner.run(
                    context ->
                            assertThat(context.getBean(StorageProperties.class).isConfigured())
                                    .isFalse());
        }

        @Test
        void isConfiguredIsFalseWhenTheKeysAreWhitespaceOnly() {
            optionalRunner
                    .withPropertyValues("storage.s3.access-key=   ", "storage.s3.secret-key=   ")
                    .run(
                            context ->
                                    assertThat(context.getBean(StorageProperties.class).isConfigured())
                                            .isFalse());
        }

        @Test
        void isConfiguredIsTrueWhenBothKeysAreSet() {
            optionalRunner
                    .withPropertyValues("storage.s3.access-key=access", "storage.s3.secret-key=secret")
                    .run(
                            context ->
                                    assertThat(context.getBean(StorageProperties.class).isConfigured())
                                            .isTrue());
        }

        @Test
        void presignTtlDefaultsToFifteenMinutes() {
            optionalRunner.run(
                    context ->
                            assertThat(context.getBean(StorageProperties.class).getS3().getPresignTtl())
                                    .isEqualTo(Duration.ofMinutes(15)));
        }

        @Test
        void pathStyleDefaultsToFalse() {
            optionalRunner.run(
                    context ->
                            assertThat(context.getBean(StorageProperties.class).getS3().isPathStyle())
                                    .isFalse());
        }

        @Test
        @DisplayName("storage.mode=optional binds to StorageMode.OPTIONAL case-insensitively")
        void modeBindsCaseInsensitively() {
            optionalRunner.run(
                    context ->
                            assertThat(context.getBean(StorageProperties.class).getMode())
                                    .isEqualTo(StorageMode.OPTIONAL));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnObjectStorageConfiguration {

        static final ObjectStorage STORAGE = new UnavailableObjectStorage("consumer-provided");

        @Bean
        ObjectStorage ownObjectStorage() {
            return STORAGE;
        }
    }
}
