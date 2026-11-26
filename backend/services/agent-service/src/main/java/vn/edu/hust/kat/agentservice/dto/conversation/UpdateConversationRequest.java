package vn.edu.hust.kat.agentservice.dto.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationRequest {

    @Size(max = 255, message = "Tiêu đề hội thoại tối đa 255 ký tự")
    @JsonProperty("title")
    private String title;

    /** Optional: ACTIVE or ARCHIVED */
    @JsonProperty("status")
    private String status;
}
