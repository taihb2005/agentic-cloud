package vn.edu.hust.kat.apigateway.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FindUserResponse {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("exists")
    private Boolean exists;
}
