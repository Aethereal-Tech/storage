package net.aetherealtech.storage.spring;

/**
 * What an application without storage credentials should do.
 *
 * <p>The one design point this library carries as a MODE, because its consumers genuinely disagree
 * and both are right about their own product. Neither is the default in any moral sense; the
 * default below is only the safer one to be wrong about.
 */
public enum StorageMode {

    /**
     * No credentials means the application does not start.
     *
     * <p>For a product where every image lives in the bucket: a deployment without one has no photo
     * on any listing and no logo on any agency — broken rather than reduced. A misconfiguration, and
     * the cheapest place to find one is at startup.
     *
     * <p>The default, because being wrong this way is a failure at deploy time with a message naming
     * the missing properties, and being wrong the other way is a feature that quietly does not work.
     */
    REQUIRED,

    /**
     * No credentials means an {@link net.aetherealtech.storage.UnavailableObjectStorage}, and every
     * storage call refuses at request time.
     *
     * <p>For a product where the asset is decorative — a tenant with no logo is an ordinary tenant.
     * A developer, CI and the integration tier then all run with no bucket and no setup, and the
     * only thing that does not work is uploading one.
     *
     * <p>Revisit it when that stops being true. Once something the product cannot render without
     * lives in the bucket, this mode turns a misconfiguration into a feature that silently does not
     * work, which is exactly what {@link #REQUIRED} exists to prevent.
     */
    OPTIONAL
}
