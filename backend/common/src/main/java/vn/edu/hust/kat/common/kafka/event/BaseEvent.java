package vn.edu.hust.kat.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEvent<T> {

    @JsonAlias({"eventId", "event_id"})
    private String eventId;

    @JsonAlias({"eventType", "event_type"})
    private String eventType;

    private LocalDateTime timestamp;

    private T payload;
}