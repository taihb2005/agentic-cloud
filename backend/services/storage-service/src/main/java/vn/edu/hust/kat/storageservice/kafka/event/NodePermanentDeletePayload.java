package vn.edu.hust.kat.storageservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import vn.edu.hust.kat.common.kafka.event.BaseEvent;

import java.util.List;
import java.util.UUID;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodePermanentDeletePayload {
    private Long nodeId;
    private String bucketName;
    private String objectKey;
}
