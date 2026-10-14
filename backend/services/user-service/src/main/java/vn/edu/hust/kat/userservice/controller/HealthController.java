package vn.edu.hust.kat.userservice.controller;

import vn.edu.hust.kat.userservice.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    @GetMapping("/ping")
    @Operation(summary = "Ping the API")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ping() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "status", "UP",
            "timestamp", Instant.now(),
            "version", "1.0.0"
        )));
    }
}
