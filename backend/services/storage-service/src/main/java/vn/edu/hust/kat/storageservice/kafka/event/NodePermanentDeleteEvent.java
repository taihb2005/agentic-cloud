package vn.edu.hust.kat.storageservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.core.util.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.UUID;


@Data
@Getter
@Builder
@AllArgsConstructor
public class NodePermanentDeleteEvent {
    @JsonProperty("event_id")
    private UUID eventId;

    @JsonProperty("batch_delete_id")
    private UUID batchDeleteId;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("objects")
    private List<DeleteObject> objects;

    @Data
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DeleteObject
    {
        @JsonProperty("node_id")
        private Long nodeId;

        @JsonProperty("bucket_name")
        private String bucketName;

        @JsonProperty("object_key")
        private String objectKey;

        @JsonProperty("size")
        private Long size;
    }
}
