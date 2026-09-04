package net.aetherealtech.storage.s3.spring;

import net.aetherealtech.storage.spring.StorageProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Whether {@code storage.s3.access-key} and {@code storage.s3.secret-key} are both set to something.
 *
 * <p>A {@link Condition} rather than {@code @ConditionalOnProperty}, because
 * {@code @ConditionalOnProperty} treats a property that is PRESENT AND EMPTY as present — and
 * present-and-empty is exactly what a deployment writes. {@code access-key: ${STORAGE_S3_ACCESS_KEY:}}
 * in a YAML file gives an unset environment variable a blank default, which satisfies
 * {@code @ConditionalOnProperty} on every machine that never configured storage. Binding the
 * properties and asking {@link StorageProperties#isConfigured()} is the same non-blank question an
 * operator means.
 *
 * <p>A {@link Condition} rather than a {@code null} return from the bean method, too, and that one is
 * subtler: {@code @ConditionalOnMissingBean} matches on bean DEFINITIONS, whose type comes from the
 * factory method's declared return type long before the method runs. A bean method that returned
 * {@code null} when S3 was unconfigured would still have advertised an {@code ObjectStorage}, and the
 * core module's fallback — which steps aside for exactly that advertisement — would never register
 * the mode-appropriate implementation that should have taken over. The definition has to not exist.
 */
public class S3ConfiguredCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
        return Binder.get(context.getEnvironment())
                .bind("storage", StorageProperties.class)
                .orElseGet(StorageProperties::new)
                .isConfigured();
    }
}
