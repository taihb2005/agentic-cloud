package vn.edu.hust.kat.agentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.agent.pgvector")
@Getter
@Setter
public class PgVectorProperties {

    private boolean enabled = false;
    private String table = "agent_long_term_memory";
    private int dimension = 1536;
    private boolean useIndex = false;
    private int indexListSize = 100;
    private boolean createTable = true;
    private boolean dropTableFirst = false;
}
