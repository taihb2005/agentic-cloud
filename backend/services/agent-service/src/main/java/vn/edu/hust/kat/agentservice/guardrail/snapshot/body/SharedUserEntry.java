package vn.edu.hust.kat.agentservice.guardrail.snapshot.body;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SharedUserEntry {

    private String userId;

    private String username;

    /** Quyền của user này trên node: VIEW hoặc EDIT */
    private String permission;
}
