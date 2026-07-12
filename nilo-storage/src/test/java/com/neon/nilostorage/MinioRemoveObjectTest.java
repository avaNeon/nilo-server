package com.neon.nilostorage;

import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilostorage.service.ImageService;
import com.neon.nilostorage.service.VideoFileService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 验证 MinIO {@code removeObject} 对不存在对象的行为
 */
@SpringBootTest
class MinioRemoveObjectTest
{
    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ImageService imageService;

    @Autowired
    private VideoFileService videoFileService;

    @Test
    void removeObject_onNonExistentKey_shouldNotThrow()
    {
        String key = "tmp/test/non-existent-" + UUID.randomUUID() + ".bin";

        assertDoesNotThrow(() -> minioClient.removeObject(RemoveObjectArgs.builder()
                                                                          .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                          .object(key)
                                                                          .build()),
                           () -> describeFailure("MinioClient.removeObject", key));
    }

    @Test
    void imageServiceDelete_onNonExistentKey_shouldNotThrow()
    {
        String key = "tmp/test/non-existent-" + UUID.randomUUID() + ".jpg";

        assertDoesNotThrow(() -> imageService.delete(key),
                           () -> describeFailure("ImageService.delete", key));
    }

    @Test
    void videoFileServiceDelete_onNonExistentKey_shouldNotThrow()
    {
        String key = "tmp/test/non-existent-" + UUID.randomUUID() + ".mp4";

        assertDoesNotThrow(() -> videoFileService.deleteRecursively(key),
                           () -> describeFailure("VideoFileService.deleteRecursively", key));
    }

    @Test
    void videoFileServiceDeleteObject_onNonExistentKey_shouldNotThrow()
    {
        String key = "tmp/test/non-existent-" + UUID.randomUUID() + ".mp4";

        assertDoesNotThrow(() -> videoFileService.deleteObject(key),
                           () -> describeFailure("VideoFileService.deleteObject", key));
    }

    @Test
    void removeObject_onAlreadyDeletedKey_shouldNotThrow() throws Exception
    {
        String key = "tmp/test/double-delete-" + UUID.randomUUID() + ".txt";
        byte[] content = "minio-delete-test".getBytes();

        minioClient.putObject(PutObjectArgs.builder()
                                           .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                           .object(key)
                                           .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                                           .build());

        minioClient.removeObject(RemoveObjectArgs.builder()
                                                 .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                 .object(key)
                                                 .build());

        assertDoesNotThrow(() -> minioClient.removeObject(RemoveObjectArgs.builder()
                                                                          .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                          .object(key)
                                                                          .build()),
                           () -> describeFailure("MinioClient.removeObject (second delete)", key));
    }

    @Test
    void removeObject_reportExceptionDetailsForManualInspection()
    {
        String key = "tmp/test/report-" + UUID.randomUUID() + ".bin";

        try
        {
            minioClient.removeObject(RemoveObjectArgs.builder()
                                                     .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                     .object(key)
                                                     .build());
            System.out.println("[MinioRemoveObjectTest] removeObject succeeded without exception, key=" + key);
        }
        catch (ErrorResponseException e)
        {
            System.out.println("[MinioRemoveObjectTest] ErrorResponseException");
            System.out.println("  key=" + key);
            System.out.println("  code=" + e.errorResponse().code());
            System.out.println("  message=" + e.errorResponse().message());
            System.out.println("  statusCode=" + e.response().code());
            fail("removeObject threw ErrorResponseException for non-existent key: " + e.errorResponse().code(), e);
        }
        catch (Exception e)
        {
            System.out.println("[MinioRemoveObjectTest] Exception type=" + e.getClass().getName());
            System.out.println("  key=" + key);
            System.out.println("  message=" + e.getMessage());
            fail("removeObject threw exception for non-existent key", e);
        }
    }

    private static String describeFailure(String operation, String key)
    {
        return operation + " should not throw for non-existent key: " + key;
    }
}
