package net.aetherealtech.storage.spring;

import net.aetherealtech.storage.ObjectStorage;
import net.aetherealtech.storage.StorageUnavailableException;
import net.aetherealtech.storage.UnavailableObjectStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers an {@link ObjectStorage} for the case where the real one could not be built.
 *
 * <p>Order of preference, highest first:
 *
 * <ol>
 *   <li>an {@link ObjectStorage} the consumer defined themselves — always wins;
 *   <li>{@code S3ObjectStorage}, if {@code storage.s3.access-key} and {@code storage.s3.secret-key}
 *       are set AND the {@code net.aetherealtech:storage-s3} artifact is on the classpath (that
 *       artifact carries its own autoconfiguration, ordered before this one);
 *   <li>whatever {@code storage.mode} says to do about having neither.
 * </ol>
 *
 * <p>That last step is the whole decision, and it is a MODE rather than a default because two
 * consumers of this library disagree about it and both are right — see {@link StorageMode}.
 *
 * <h2>Why the decision is a bean method and not {@code @ConditionalOnProperty}</h2>
 *
 * <p>Because a property that is PRESENT AND EMPTY defeats it. Deployments almost always write
 * {@code access-key: ${STORAGE_S3_ACCESS_KEY:}} in their YAML so an unset environment variable has a
 * default — which makes the property present, empty, and {@code @ConditionalOnProperty} satisfied.
 * The condition would match on every machine that had never configured storage, and the failure
 * would surface as a 403 from the store at the first upload rather than as anything a reader could
 * see. Binding the properties and asking a non-blank question is the only reading that matches what
 * an operator means.
 *
 * <h2>Credentials set and no S3 artifact</h2>
 *
 * <p>Reaching this class with {@link StorageProperties#isConfigured()} true means the S3
 * autoconfiguration did not register a bean, and the overwhelmingly likely reason is that
 * {@code storage-s3} is not on the classpath. It is treated as the same failure as having no
 * credentials, with a message that says so — a half-configuration deserves a sentence naming the
 * missing half, not a silent fallback.
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    private static final System.Logger LOGGER =
            System.getLogger(StorageAutoConfiguration.class.getName());

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    public ObjectStorage objectStorage(final StorageProperties properties) {
        final String problem = describeWhatIsMissing(properties);
        if (properties.getMode() == StorageMode.REQUIRED) {
            // Thrown while BUILDING the bean, so the context never comes up and no request is
            // served. That is the point of the mode: a deployment whose every image lives in the
            // bucket is broken without one, and finding out at startup costs a failed deploy
            // instead of a page of broken images.
            throw new StorageUnavailableException(problem);
        }
        LOGGER.log(System.Logger.Level.WARNING, problem);
        return new UnavailableObjectStorage(problem);
    }

    /**
     * The two ways of getting here are not equally intentional, and the message has to tell them
     * apart: one operator forgot to configure anything, the other configured everything and left
     * out a dependency.
     */
    private String describeWhatIsMissing(final StorageProperties properties) {
        if (properties.isConfigured()) {
            return "Object storage credentials are set but no ObjectStorage could be built. Add the"
                    + " net.aetherealtech:storage-s3 artifact to the classpath — the S3 adapter"
                    + " lives in its own module.";
        }
        return "Object storage is not configured. Set storage.s3.access-key and"
                + " storage.s3.secret-key (STORAGE_S3_ACCESS_KEY and STORAGE_S3_SECRET_KEY), along"
                + " with storage.s3.endpoint, storage.s3.region and storage.s3.bucket. The bucket"
                + " must already exist; this library never creates one.";
    }
}
