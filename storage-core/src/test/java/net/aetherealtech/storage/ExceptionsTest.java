package net.aetherealtech.storage;

import net.aetherealtech.storage.upload.UploadFormat;
import net.aetherealtech.storage.upload.UploadRejectedException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    private static final ObjectKey KEY = ObjectKey.of("listings/5/photo");

    @Nested
    class Hierarchy {

        @Test
        void storageExceptionIsARuntimeException() {
            assertThat(new StorageException("x")).isInstanceOf(RuntimeException.class);
        }

        @Test
        void objectNotFoundExceptionIsAStorageException() {
            assertThat(new ObjectNotFoundException(KEY, null)).isInstanceOf(StorageException.class);
        }

        @Test
        void storageAccessExceptionIsAStorageException() {
            assertThat(new StorageAccessException("denied", 403, "AccessDenied", null))
                    .isInstanceOf(StorageException.class);
        }

        @Test
        void storageUnavailableExceptionIsAStorageException() {
            assertThat(new StorageUnavailableException("unavailable")).isInstanceOf(StorageException.class);
        }

        @Test
        void uploadRejectedExceptionIsAStorageException() {
            assertThat(new UploadRejectedException(
                            UploadRejectedException.Reason.TOO_LARGE, UploadFormat.PNG, "too big"))
                    .isInstanceOf(StorageException.class);
        }
    }

    @Nested
    class StorageAccessExceptionFields {

        @Test
        void carriesTheStatusCodeAndErrorCode() {
            final StorageAccessException exception =
                    new StorageAccessException("denied", 403, "AccessDenied", null);

            assertThat(exception.statusCode()).isEqualTo(403);
            assertThat(exception.errorCode()).isEqualTo("AccessDenied");
        }

        @Test
        void aNullErrorCodeIsCarriedAsNull() {
            final StorageAccessException exception = new StorageAccessException("denied", 403, null, null);

            assertThat(exception.errorCode()).isNull();
        }

        @Test
        void theCauseIsPreserved() {
            final Throwable cause = new RuntimeException("wire failure");

            final StorageAccessException exception =
                    new StorageAccessException("denied", 403, "AccessDenied", cause);

            assertThat(exception.getCause()).isSameAs(cause);
        }
    }

    @Nested
    class ObjectNotFoundExceptionFields {

        @Test
        void carriesTheKey() {
            final ObjectNotFoundException exception = new ObjectNotFoundException(KEY, null);

            assertThat(exception.key()).isEqualTo(KEY);
        }

        @Test
        void theCauseIsPreserved() {
            final Throwable cause = new RuntimeException("network");

            final ObjectNotFoundException exception = new ObjectNotFoundException(KEY, cause);

            assertThat(exception.getCause()).isSameAs(cause);
        }
    }

    @Nested
    class StorageUnavailableExceptionFields {

        @Test
        void theCauseIsPreservedWhenGiven() {
            final Throwable cause = new RuntimeException("connect timed out");

            final StorageUnavailableException exception =
                    new StorageUnavailableException("unavailable", cause);

            assertThat(exception.getCause()).isSameAs(cause);
        }

        @Test
        void hasNoCauseWhenNoneIsGiven() {
            assertThat(new StorageUnavailableException("unavailable").getCause()).isNull();
        }
    }
}
