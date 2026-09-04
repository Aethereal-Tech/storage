package net.aetherealtech.storage.spring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The whole {@code storage.*} property namespace, in one class.
 *
 * <p>Including the S3 keys, which nothing in THIS artifact reads — {@code storage-s3}'s own
 * autoconfiguration does. Splitting them across the two artifacts would be tidier internally and
 * worse for the only person who matters here: an operator setting environment variables should not
 * have to know which jar owns which key, and a properties class describing half a namespace makes
 * the IDE's completion and the generated metadata lie by omission.
 *
 * <p><b>Nothing here has a vendor default.</b> Not the endpoint, not the region, not the bucket. A
 * library that defaulted them would let a deployment that forgot one still start, sign requests for
 * somewhere nobody chose, and fail with an authentication error a long way from the cause. The
 * exceptions are {@link S3#getPresignTtl()}, where short is safe and long is not, and
 * {@link S3#isPathStyle()}, where the answer that works with real providers is the false one.
 *
 * <p>Relaxed binding means the environment-variable spelling is the obvious one:
 * {@code STORAGE_MODE}, {@code STORAGE_S3_ENDPOINT}, {@code STORAGE_S3_REGION},
 * {@code STORAGE_S3_BUCKET}, {@code STORAGE_S3_ACCESS_KEY}, {@code STORAGE_S3_SECRET_KEY},
 * {@code STORAGE_S3_PRESIGN_TTL}, {@code STORAGE_S3_PATH_STYLE}.
 */
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** What to do with no credentials. See {@link StorageMode}. */
    private StorageMode mode = StorageMode.REQUIRED;

    private final S3 s3 = new S3();

    /**
     * Whether object storage is usable. <b>The credentials, and nothing else, decide this.</b>
     *
     * <p>Not the bucket or the endpoint, deliberately: a deployment that named a bucket and forgot
     * the secret key has TRIED to configure storage, and the useful failure says which piece is
     * missing rather than falling back as though nobody had asked for any. A blank bucket with
     * credentials present is refused by {@code S3Config} instead, by name.
     */
    public boolean isConfigured() {
        return !s3.getAccessKey().isBlank() && !s3.getSecretKey().isBlank();
    }

    public StorageMode getMode() {
        return mode;
    }

    public void setMode(final StorageMode mode) {
        this.mode = mode;
    }

    public S3 getS3() {
        return s3;
    }

    /** {@code storage.s3.*} — the coordinates of an S3-compatible store. */
    public static class S3 {

        /**
         * The service endpoint, with its scheme. Location-bound on most providers, and the region
         * below is then the same location code — Hetzner's are
         * {@code https://nbg1.your-objectstorage.com} with {@code nbg1} (also {@code fsn1},
         * {@code hel1}).
         */
        private String endpoint = "";

        /** The signing region. An S3 signature covers it, so a wrong one fails at the store. */
        private String region = "";

        /**
         * Created and configured BY HAND, and expected to exist already. This library writes keys
         * beneath it and never creates, configures or deletes a bucket — that includes its CORS
         * policy, which a browser needs before a presigned read works at all.
         */
        private String bucket = "";

        /** Presence selects S3. Never logged. */
        private String accessKey = "";

        /** Never logged. */
        private String secretKey = "";

        /**
         * How long a presigned read stays valid, when a caller does not name a lifetime.
         *
         * <p>Short on purpose, and the one default here that is a judgment rather than a blank: for
         * its lifetime that URL is a bearer token, and whoever holds it fetches the object with no
         * session and no ownership check.
         */
        private Duration presignTtl = Duration.ofMinutes(15);

        /**
         * Path-style addressing ({@code endpoint/bucket/key}) instead of virtual-hosted
         * ({@code bucket.endpoint/key}).
         *
         * <p>False, because Hetzner — and AWS itself, which has deprecated path-style — requires
         * virtual-hosted for presigned URLs to work at all. It exists as a switch for MinIO, which
         * is the obvious thing to develop against and which requires the opposite.
         */
        private boolean pathStyle;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(final String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(final String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(final String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(final String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(final String secretKey) {
            this.secretKey = secretKey;
        }

        public Duration getPresignTtl() {
            return presignTtl;
        }

        public void setPresignTtl(final Duration presignTtl) {
            this.presignTtl = presignTtl;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(final boolean pathStyle) {
            this.pathStyle = pathStyle;
        }
    }
}
