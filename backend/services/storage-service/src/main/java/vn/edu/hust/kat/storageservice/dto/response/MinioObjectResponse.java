package vn.edu.hust.kat.storageservice.dto.response;

import jdk.jfr.MetadataDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinioObjectResponse {
    private String bucketName;
    private String objectKey;
    private String etag;
}
