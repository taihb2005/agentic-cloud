package vn.edu.hust.kat.storageservice.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.hust.kat.storageservice.grpc.UserStorageGrpcService;

@Configuration
@ConditionalOnProperty(name = "spring.grpc.server.enabled", havingValue = "true", matchIfMissing = false)
public class GrpcServerConfig {

    @Value("${spring.grpc.server.port:9092}")
    private int port;

    private int enableKeepAlive;

    private int keepAliveTime;

    private int keepAliveTimeout;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public Server grpcStorageServer(UserStorageGrpcService userStorageGrpcService) {
        return ServerBuilder
                .forPort(port)
                .addService(userStorageGrpcService)
                .build();
    }
}
