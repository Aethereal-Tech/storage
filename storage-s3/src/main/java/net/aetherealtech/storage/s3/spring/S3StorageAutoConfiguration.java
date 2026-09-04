package net.aetherealtech.storage.s3.spring;

import java.net.URI;

import net.aetherealtech.storage.ObjectStorage;
import net.aetherealtech.storage.s3.S3Config;
import net.aetherealtech.storage.s3.S3ObjectStorage;
import net.aetherealtech.storage.spring.StorageAutoConfiguration;
import net.aetherealtech.storage.spring.StorageProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Registers an {@link S3ObjectStorage} when {@code storage.s3.access-key} and
 * {@code storage.s3.secret-key} are both set.
 *
 * <p>Ordered {@code before} {@link StorageAutoConfiguration}, which is the whole mechanism behind "S3
 * wins": this bean definition is registered FIRST, and the core module's own {@code ObjectStorage}
 * bean is {@code @ConditionalOnMissingBean(ObjectStorage.class)}, so it steps aside once this one has
 * registered. A deployment with credentials configured gets S3 deterministically, rather than
 * whichever autoconfiguration the classpath happened to order first.
 *
 * <p>The properties are the core module's {@link StorageProperties} — one class describing the whole
 * {@code storage.*} namespace, so an operator setting environment variables never has to know which
 * artifact reads which key.
 *
 * <p>On a classpath without Spring, nothing loads this: it is named only from the Spring Boot
 * registration file that Spring Boot alone reads.
 */
@AutoConfiguration(before = StorageAutoConfiguration.class)
@EnableConfigurationProperties(StorageProperties.class)
public class S3StorageAutoConfiguration {

    private static final System.Logger LOGGER =
            System.getLogger(S3StorageAutoConfiguration.class.getName());

    /**
     * {@code destroyMethod = "close"} releases the SDK's HTTP connection pool and the presigner
     * along with the context, rather than leaking them past a shutdown or a test's context close.
     */
    @Bean(destroyMethod = "close")
    @Conditional(S3ConfiguredCondition.class)
    @ConditionalOnMissingBean(ObjectStorage.class)
    public ObjectStorage objectStorage(final StorageProperties properties) {
        final StorageProperties.S3 s3 = properties.getS3();
        // Never the credentials: an operator reading this line needs to know WHICH bucket and store
        // came up, not to see the secret key repeated back in the log a deployment already has.
        LOGGER.log(
                System.Logger.Level.INFO,
                "Object storage configured: bucket {0} at {1} ({2} addressing)",
                s3.getBucket(),
                s3.getEndpoint(),
                s3.isPathStyle() ? "path-style" : "virtual-hosted");
        return new S3ObjectStorage(s3Config(s3));
    }

    private S3Config s3Config(final StorageProperties.S3 s3) {
        return S3Config.of(
                        URI.create(s3.getEndpoint()),
                        s3.getRegion(),
                        s3.getBucket(),
                        s3.getAccessKey(),
                        s3.getSecretKey())
                .withPresignTtl(s3.getPresignTtl())
                .withPathStyle(s3.isPathStyle());
    }
}
