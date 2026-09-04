package net.aetherealtech.storage.s3;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * What {@link S3ObjectStorage} needs to reach an S3-compatible store: where it is, which bucket, and
 * a credential.
 *
 * <p><strong>The secret key must never be logged.</strong> This record deliberately has no
 * {@code toString} override, so the record-generated one is what a stray {@code log.info(config)}
 * would print, and a record's default DOES include every component. Callers must not log this
 * object.
 *
 * <p>{@code endpoint} is what makes this vendor-neutral: the AWS SDK is the SigV4 protocol client and
 * not a commitment to AWS, and everything about which store is actually reached is decided by this
 * one field. The same code reaches Hetzner, MinIO, or any other S3-compatible service.
 *
 * <p>{@code pathStyle} defaults to {@code false} because Hetzner — and AWS itself, which has
 * deprecated path-style addressing — requires virtual-hosted addressing for a presigned URL to work
 * at all. It exists as a switch for MinIO, which is the obvious thing to develop against locally and
 * which requires the opposite.
 *
 * @param endpoint   the service endpoint, with its scheme; required
 * @param region     the signing region; required. An S3 signature covers it, so a wrong one fails at
 *                   the store rather than reaching a different one
 * @param bucket     the bucket this config writes beneath; required, and expected to already exist —
 *                   this library never creates one
 * @param accessKey  required
 * @param secretKey  required; never logged, see above
 * @param presignTtl how long a presigned read stays valid when a caller does not name a lifetime,
 *                   defaulting to fifteen minutes; must be positive
 * @param pathStyle  path-style addressing ({@code endpoint/bucket/key}) instead of virtual-hosted
 *                   ({@code bucket.endpoint/key}); defaults to {@code false}
 */
public record S3Config(
        URI endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        Duration presignTtl,
        boolean pathStyle) {

    private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(15);

    public S3Config {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        region = required(region, "region");
        // A blank bucket fails HERE, by name, rather than as a 403 the first time a request reaches
        // the store — the two are indistinguishable at the call site, and only this constructor knows
        // which field was left empty.
        bucket = required(bucket, "bucket");
        accessKey = required(accessKey, "accessKey");
        secretKey = required(secretKey, "secretKey");
        presignTtl = presignTtl == null ? DEFAULT_PRESIGN_TTL : presignTtl;
        if (presignTtl.isNegative() || presignTtl.isZero()) {
            throw new IllegalArgumentException("presignTtl must be positive, was: " + presignTtl);
        }
    }

    /** The minimum a config needs; {@code presignTtl} and {@code pathStyle} take their defaults. */
    public static S3Config of(
            final URI endpoint,
            final String region,
            final String bucket,
            final String accessKey,
            final String secretKey) {
        return new S3Config(endpoint, region, bucket, accessKey, secretKey, null, false);
    }

    public S3Config withPresignTtl(final Duration presignTtl) {
        return new S3Config(endpoint, region, bucket, accessKey, secretKey, presignTtl, pathStyle);
    }

    public S3Config withPathStyle(final boolean pathStyle) {
        return new S3Config(endpoint, region, bucket, accessKey, secretKey, presignTtl, pathStyle);
    }

    private static String required(final String value, final String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
