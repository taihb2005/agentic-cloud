package vn.edu.hust.kat.storageservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record NodePermanentDeleteEvent(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("batch_delete_id") UUID batchDeleteId,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("objects") List<DeleteObject> objects
) {
    public record DeleteObject(
            @JsonProperty("node_id") Long nodeId,
            @JsonProperty("bucket_name") String bucketName,
            @JsonProperty("object_key") String objectKey
    ) {}
}
