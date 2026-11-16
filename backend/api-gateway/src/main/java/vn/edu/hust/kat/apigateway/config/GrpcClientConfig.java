package vn.edu.hust.kat.apigateway.config;

import io.grpc.ManagedChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import vn.edu.hust.kat.proto.gateway.v1.ApiGatewayGrpc;
import vn.edu.hust.kat.proto.storage.v1.StorageServiceGrpc;

@Configuration
public class GrpcClientConfig {

    @Bean
    public ApiGatewayGrpc.ApiGatewayBlockingStub apiGatewayStub(
            GrpcChannelFactory channelFactory
    ) {

        ChannelBuilderOptions options = ChannelBuilderOptions.defaults()
                .withCustomizer((channelName, builder) -> {
                    builder.enableRetry()
                            .maxRetryAttempts(3);
                });

        ManagedChannel channel = channelFactory.createChannel("api-gateway", options);

        return ApiGatewayGrpc.newBlockingStub(channel);
    }
}

