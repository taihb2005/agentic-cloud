package vn.edu.hust.kat.agentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.agent.memory")
@Getter
@Setter
public class AgentMemoryProperties {

    private int maxMessages = 20;
    private long sessionTtlMinutes = 45;
    private double minScore = 0.68;
    private int ragMaxResults = 5;
}
