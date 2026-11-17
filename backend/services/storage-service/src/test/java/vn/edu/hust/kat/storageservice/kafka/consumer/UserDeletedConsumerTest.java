package vn.edu.hust.kat.storageservice.kafka.consumer;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hust.kat.storageservice.config.JacksonConfig;
import vn.edu.hust.kat.storageservice.service.StorageUserService;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDeletedConsumerTest {

    private JsonMapper jsonMapper;

    @Mock
    private StorageUserService storageUserService;

    private UserDeletedConsumer userDeletedConsumer;

    @BeforeEach
    void setUp() {
        jsonMapper = new JacksonConfig().jsonMapper();
        userDeletedConsumer = new UserDeletedConsumer(jsonMapper, storageUserService);
    }

    @Test
    void consume_UserDeletedEvent_CallsDeleteUserStorage() {
        UUID userId = UUID.randomUUID();
        String message = """
                {
                  "eventId": "%s",
                  "eventType": "USER_DELETED",
                  "timestamp": "2026-11-17T12:00:00",
                  "payload": {
                    "id": "%s",
                    "username": "alice",
                    "email": "alice@example.com"
                  }
                }
                """.formatted(UUID.randomUUID(), userId);

        userDeletedConsumer.consume(message);

        verify(storageUserService).deleteUserStorage(userId);
    }
}
