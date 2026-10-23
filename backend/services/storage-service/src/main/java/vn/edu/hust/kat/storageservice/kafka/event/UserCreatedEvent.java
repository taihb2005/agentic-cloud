package vn.edu.hust.kat.storageservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import vn.edu.hust.kat.common.kafka.event.BaseEvent;

@SuperBuilder
public class UserCreatedEvent extends BaseEvent<UserCreatedEvent.UserCreatedPayload> {
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCreatedPayload {
        private String id;
        private String username;
        private String email;
    }
}
