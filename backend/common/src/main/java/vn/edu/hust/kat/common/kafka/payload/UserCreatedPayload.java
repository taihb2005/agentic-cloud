package vn.edu.hust.kat.common.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedPayload {
    private String id;
    private String username;
    private String email;
}