package vn.edu.hust.kat.storageservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.hust.kat.storageservice.entity.FsNode;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class NodeResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("parent_id")
    private Long parentId;

    @JsonProperty("path")
    private String path;

    @JsonProperty("name")
    private String name;

    @JsonProperty("is_folder")
    private Boolean isFolder;

    @JsonProperty("node_status")
    private String nodeStatus;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @JsonProperty("etag")
    private String etag;

    @JsonProperty("is_starred")
    private Boolean isStarred;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("deleted_at")
    private LocalDateTime deletedAt;

    public static NodeResponse from(FsNode node) {
        if (node == null) {
            return null;
        }
        return NodeResponse.builder()
                .id(node.getId())
                .parentId(node.getParent() != null ? node.getParent().getId() : null)
                .path(node.getMaterializedPath())
                .name(node.getName())
                .isFolder(node.getIsFolder())
                .nodeStatus(node.getNodeStatus() != null ? node.getNodeStatus().toString() : null)
                .mimeType(node.getMimeType())
                .sizeBytes(node.getSizeBytes() != null ? node.getSizeBytes() : 0L)
                .etag(node.getEtag())
                .isStarred(node.getIsStarred())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .deletedAt(node.getDeletedAt())
                .build();
    }
}
