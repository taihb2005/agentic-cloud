package vn.edu.hust.kat.storageservice.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.storageservice.config.MinioProperties;
import vn.edu.hust.kat.storageservice.dto.response.MinioObjectResponse;

import java.io.ByteArrayInputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service

@Slf4j
public class MinioStorageService {

    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final MinioProperties minioProperties;

    public MinioStorageService(
            @Qualifier("internalMinioClient")
            MinioClient internalMinioClient,

            @Qualifier("publicMinioClient")
            MinioClient publicMinioClient,

            MinioProperties minioProperties
    ) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.minioProperties = minioProperties;
    }

    /**
     * Khởi tạo folder storagePrefix trên MinIO và trả về s3ObjectKey sau khi thành công.
     * @param bucketName tên của bucket đang muốn khởi tạo folder
     * @param storagePrefix tiền tố của user khi khởi tạo storage'
     */
    public MinioObjectResponse createDirectory(String bucketName, String storagePrefix) {
        // Sinh key phẳng: có dạng user/<user_id>/random_uuid
        String normalizedPrefix = (storagePrefix.endsWith("/") ? storagePrefix : storagePrefix + "/");

        String objectKey = normalizedPrefix + UUID.randomUUID() + "/";

        try {
            // Đẩy 1 object rỗng (0-byte) lên MinIO
            ObjectWriteResponse response = internalMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(new byte[0]), 0L, -1L)
                            .contentType("application/x-directory")
                            .build()
            );

            return MinioObjectResponse.builder()
                    .bucketName(bucketName)
                    .objectKey(objectKey)
                    .etag(response.etag())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo thư mục vật lý trên MinIO: " + e.getMessage(), e);
        }
    }

    public MinioObjectResponse getObjectMetadata(String bucketName, String objectKey) {
        String normalizedKey = normalizeFolderKey(objectKey);

        try {
            StatObjectResponse statObject = internalMinioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(normalizedKey)
                            .build()
            );

            return MinioObjectResponse.builder()
                    .bucketName(statObject.bucket())
                    .objectKey(statObject.object())
                    .size(statObject.size())
                    .etag(statObject.etag())
                    .contentType(statObject.contentType())
                    .build();
        } catch (Exception e) {
            log.error("Lỗi khi lấy metadata trên MinIO cho bucket={}, objectKey={}", bucketName, normalizedKey, e);
            throw new RuntimeException("Không thể lây metadata của object");
        }
    }

    public void deleteObject(String bucketName, String objectKey) {
        if (bucketName == null || objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            internalMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            log.info("Đã xóa object trên MinIO: bucket={}, key={}", bucketName, objectKey);
        } catch (Exception ex) {
            log.error("Lỗi khi xóa object '{}' trên bucket '{}'", objectKey, bucketName, ex);
            throw new RuntimeException("Lỗi khi xóa object trên MinIO: " + ex.getMessage(), ex);
        }
    }

    /**
     * Hàm dọn dẹp khi quá trình lưu metadata vào DB thất bại sau khi đã tạo trên MinIO.
     */
    public void deleteObjectSilently(String bucketName, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            internalMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            log.info("Đã hoàn tác (xóa) object rác trên MinIO: bucket={}, key={}", bucketName, objectKey);
        } catch (Exception ex) {
            log.warn("Không thể xóa object '{}' trên bucket '{}' khi rollback", objectKey, bucketName, ex);
        }
    }

    /**
     * Sinh Presigned URL với quyền PUT để client tải file trực tiếp lên MinIO.
     * @param bucketName tên bucket
     * @param objectKey đường dẫn lưu file trên MinIO
     * @param expirySeconds hạn của url
     * @return Presigned URL
     */
    public String generatePresignedUploadUrl(String bucketName, String objectKey, int expirySeconds) {
        try {
            ensureBucketExists(bucketName);

            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi khi tạo presigned upload URL cho bucket={}, key={}", bucketName, objectKey, e);
            throw new RuntimeException("Không thể tạo URL tải lên cho file: " + e.getMessage(), e);
        }
    }

    /**
     * Sinh Presigned URL với quyền GET để client tải file trực tiếp từ MinIO.
     * @param bucketName tên bucket
     * @param objectKey đường dẫn object trên MinIO
     * @param expirySeconds hạn của url
     * @return Presigned URL
     */
    public String generatePresignedDownloadUrl(String bucketName, String objectKey, int expirySeconds) {
        try {
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi khi tạo presigned download URL cho bucket={}, key={}", bucketName, objectKey, e);
            throw new RuntimeException("Không thể tạo URL tải xuống cho file: " + e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra xem object có thực sự tồn tại trên MinIO hay không.
     */
    public boolean doesObjectExist(String bucketName, String objectKey) {
        if (bucketName == null || objectKey == null || objectKey.isBlank()) {
            return false;
        }
        try {
            internalMinioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                return false;
            }
            log.warn("Lỗi kiểm tra object trên MinIO: bucket={}, key={}, code={}", bucketName, objectKey, code);
            return false;
        } catch (Exception e) {
            log.warn("Ngoại lệ khi kiểm tra object trên MinIO: bucket={}, key={}", bucketName, objectKey, e);
            return false;
        }
    }


    private void ensureBucketExists(String bucketName) throws Exception {
        boolean found = internalMinioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!found) {
            internalMinioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("Đã tạo mới bucket '{}' trên MinIO", bucketName);
        }
    }

    private String normalizeFolderKey(String prefix) {
        String cleaned = prefix.trim();
        return cleaned.endsWith("/") ? cleaned : cleaned + "/";
    }
}
