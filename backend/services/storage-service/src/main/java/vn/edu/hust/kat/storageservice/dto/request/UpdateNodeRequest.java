package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class UpdateNodeRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("starred")
    private boolean starred;
}
