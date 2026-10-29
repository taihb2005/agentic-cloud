package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlsResponse {

    @JsonProperty("presigned_urls")
    @JsonAlias({"presignedUrls"})
    private List<PresignedUrlResponse> presignedUrls;
}
