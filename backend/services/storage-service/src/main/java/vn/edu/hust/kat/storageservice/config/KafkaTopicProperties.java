package vn.edu.hust.kat.storageservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.kafka.topics")
@Getter
@Setter
public class KafkaTopicProperties {

    private UserService userService = new UserService();
    private StorageService storageService = new StorageService();

    @Getter
    @Setter
    public static class UserService {
        private String userCreated;
    }

    @Getter
    @Setter
    public static class StorageService {
        private String nodePermanentDelete;
    }
}

