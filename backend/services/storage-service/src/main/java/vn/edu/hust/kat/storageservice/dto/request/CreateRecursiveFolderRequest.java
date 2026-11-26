package vn.edu.hust.kat.storageservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecursiveFolderRequest {

    @JsonProperty("path")
    @JsonAlias({"folder_path", "folderPath", "name"})
    private String path;

    @JsonProperty("parent_id")
    @JsonAlias({"parentId"})
    private String parentId;
}
