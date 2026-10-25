package vn.edu.hust.kat.userservice.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import vn.edu.hust.kat.proto.storage.v1.StorageServiceGrpc;

@Configuration
public class GrpcClientConfig {

    @Bean
    public StorageServiceGrpc.StorageServiceBlockingStub storageServiceStub(
            GrpcChannelFactory channelFactory
    ) {

        ChannelBuilderOptions options = ChannelBuilderOptions.defaults()
                .withCustomizer((channelName, builder) -> {
                    builder.enableRetry()
                            .maxRetryAttempts(3);
                });

        ManagedChannel channel = channelFactory.createChannel("storage", options);

        return StorageServiceGrpc.newBlockingStub(channel);
    }
}
