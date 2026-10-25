package vn.edu.hust.kat.storageservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.storageservice.dto.response.MinioObjectResponse;

import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;

    /**
     * Khởi tạo folder prefix trên MinIO và trả về s3ObjectKey sau khi thành công.
     * @param bucketName tên của bucket đang muốn khởi tạo folder
     * @param storagePrefix tiền tố của folder cha mong muốn khởi tạo. Ví dụ '(user_id)/(parent_id)/(..)'
     */
    public MinioObjectResponse createDirectory(String bucketName, String storagePrefix) {
        String normalizedKey = normalizeFolderKey(storagePrefix);

        try {
            ensureBucketExists(bucketName);

            // Tạo một empty object có giá trị 0 byte để nhận diện là thư mục
            ObjectWriteResponse response = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(normalizedKey)
                            .stream(new ByteArrayInputStream(new byte[0]), 0L, -1L)
                            .build()
            );

            MinioObjectResponse minioObjectResponse = MinioObjectResponse.builder()
                                .bucketName(response.bucket())
                                .objectKey(response.object())
                                .etag(response.etag())
                                .build();

            log.info("Khởi tạo thành công folder trên MinIO: bucket={}, object={}, etag={}",
                    bucketName, response.object(), response.etag());

            // Trả về object key thực tế từ phản hồi của MinIO
            return minioObjectResponse;
        } catch (Exception e) {
            log.error("Lỗi khi khởi tạo thư mục trên MinIO cho bucket={}, prefix={}", bucketName, normalizedKey, e);
            throw new RuntimeException("Không thể khởi tạo không gian lưu trữ trên MinIO", e);
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
