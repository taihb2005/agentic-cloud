package vn.edu.hust.kat.storageservice.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.storageservice.dto.response.MinioObjectResponse;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;

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
            ObjectWriteResponse response = minioClient.putObject(
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
            StatObjectResponse statObject = minioClient.statObject(
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
            minioClient.removeObject(
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
            minioClient.removeObject(
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

    private void ensureBucketExists(String bucketName) throws Exception {
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!found) {
            minioClient.makeBucket(
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
