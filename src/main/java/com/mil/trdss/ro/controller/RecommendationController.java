package com.mil.trdss.ro.controller;

import com.mil.trdss.ro.domain.dto.TacticalRecommendationDTO;
import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/recommendations/calculate")
    public ResponseEntity<TacticalRecommendationDTO> calculate(@Valid @RequestBody TargetIntakeDTO intakeDTO) {
        return ResponseEntity.ok(recommendationService.calculateRecommendation(intakeDTO));
    }

    @PostMapping("/telemetry/heartbeat")
    public ResponseEntity<Void> ingestHeartbeat(@Valid @RequestBody TelemetryHeartbeatDTO telemetry) {
        recommendationService.ingestHeartbeat(telemetry);
        return ResponseEntity.ok().build();
    }
}
