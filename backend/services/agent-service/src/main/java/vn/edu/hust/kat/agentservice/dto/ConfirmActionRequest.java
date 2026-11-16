package vn.edu.hust.kat.agentservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmActionRequest {

    @NotBlank(message = "Confirmation token is required")
    private String confirmationToken;

    private boolean approved;
}
