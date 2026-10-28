package vn.edu.hust.kat.storageservice.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@RequiredArgsConstructor
@AllArgsConstructor
public class NodeDeleteProperties {

    @Value("${app.node_async_threshold}")
    private int asyncThreshold;
}
