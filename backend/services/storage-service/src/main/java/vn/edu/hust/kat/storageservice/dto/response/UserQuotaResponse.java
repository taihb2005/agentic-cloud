package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class UserQuotaResponse {

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("storage_id")
    private UUID storageId;

    @JsonProperty("current_quota")
    private Long currentQuota;

    @JsonProperty("max_quota")
    private Long maxQuota;

    @JsonProperty("unit")
    private String unit;

    @JsonProperty("usage_percentage")
    private Double usagePercentage;

    @JsonProperty("status")
    private String status;
}
