package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("parent_id")
    private String parentId;
}
