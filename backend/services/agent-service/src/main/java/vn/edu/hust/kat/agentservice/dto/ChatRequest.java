package vn.edu.hust.kat.agentservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message must not be empty")
    @JsonProperty("message")
    private String message;

    @JsonProperty("conversation_id")
    @JsonAlias({"conversationId"})
    private String conversationId;

    @JsonProperty("confirmation_token")
    @JsonAlias({"confirmationToken"})
    private String confirmationToken;
}
