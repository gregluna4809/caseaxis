package com.caseaxis.health;

import com.caseaxis.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.success(new HealthResponse("caseaxis-backend", "UP")));
    }

    public record HealthResponse(String service, String status) {}
}
