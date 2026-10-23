package vn.edu.hust.kat.common.kafka.event;

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

    private String eventId;

    private String eventType;

    private LocalDateTime timestamp;

    private T payload;
}