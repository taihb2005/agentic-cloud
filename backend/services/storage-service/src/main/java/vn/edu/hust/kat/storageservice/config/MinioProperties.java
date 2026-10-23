package vn.edu.hust.kat.storageservice.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.minio")
@RequiredArgsConstructor
@AllArgsConstructor
public class MinioProperties {

    @Value("${app.minio.endpoint}")
    private String endpoint;

    @Value("${app.minio.access-key}")
    private String accessKey;

    @Value("${app.minio.secret-key}")
    private String secretKey;

    @Value("${app.minio.default-bucket}")
    private String defaultBucket;

    @Value("${app.minio.default-quota-bytes}")
    private long defaultQuotaBytes; // 15 GB

    @Value("${app.minio.user-prefix-pattern}:users/%s/")
    private String userPrefixPattern;
}
