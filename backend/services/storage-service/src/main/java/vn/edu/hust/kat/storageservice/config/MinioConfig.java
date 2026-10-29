package vn.edu.hust.kat.storageservice.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean("internalMinioClient")
    @ConditionalOnProperty(prefix = "app.minio", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MinioClient minioClient() {
        log.info("Khởi tạo MinioClient kết nối tới: {}", minioProperties.getEndpoint());
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }

    @Bean("publicMinioClient")
    @ConditionalOnProperty(prefix = "app.minio", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MinioClient publicMinioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getPublicEndpoint())
                .region("us-east-1")
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
