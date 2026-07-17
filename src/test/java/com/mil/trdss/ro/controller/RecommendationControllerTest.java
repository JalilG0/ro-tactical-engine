package com.mil.trdss.ro.controller;

import com.mil.trdss.ro.domain.dto.TacticalRecommendationDTO;
import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;
import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
import com.mil.trdss.ro.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void rejectsEmptyPayloadAsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request"));
    }

    @Test
    void rejectsPayloadFailingBeanValidationWithFieldErrors() throws Exception {
        TargetIntakeDTO invalidIntake = new TargetIntakeDTO(
                "evt-1",
                System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo(
                        "target-1", 15,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(false, List.of()),
                false
        );

        mockMvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidIntake)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors['target.threatLevel']").exists());
    }

    @Test
    void returns422WhenServiceRejectsStaleData() throws Exception {
        when(recommendationService.calculateRecommendation(any())).thenThrow(new StaleDataException("too old"));

        mockMvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validIntake())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Stale Data Rejected"));
    }

    @Test
    void returns409WhenServiceRejectsDuplicateEvent() throws Exception {
        when(recommendationService.calculateRecommendation(any())).thenThrow(new DuplicateEventException("dup"));

        mockMvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validIntake())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Event Rejected"));
    }

    @Test
    void returns200WithRecommendationOnSuccess() throws Exception {
        TacticalRecommendationDTO recommendation = new TacticalRecommendationDTO(
                "rec-1", "target-1", System.currentTimeMillis(), "explanation", List.of());
        when(recommendationService.calculateRecommendation(any())).thenReturn(recommendation);

        mockMvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validIntake())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value("rec-1"))
                .andExpect(jsonPath("$.targetId").value("target-1"));
    }

    @Test
    void ingestsHeartbeatAndDelegatesToService() throws Exception {
        TelemetryHeartbeatDTO telemetry = validTelemetry();

        mockMvc.perform(post("/api/v1/telemetry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(telemetry)))
                .andExpect(status().isOk());

        verify(recommendationService).ingestHeartbeat(any());
    }

    private TargetIntakeDTO validIntake() {
        return new TargetIntakeDTO(
                "evt-1",
                System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo(
                        "target-1", 5,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(false, List.of()),
                false
        );
    }

    private TelemetryHeartbeatDTO validTelemetry() {
        return new TelemetryHeartbeatDTO(
                "asset-1",
                "TB2",
                new TelemetryHeartbeatDTO.Location(39.0, 35.0, 1000),
                AssetStatus.FREE,
                80,
                80,
                new TelemetryHeartbeatDTO.MunitionState(MunitionType.MAM_L, List.of(MunitionType.MAM_L), 3),
                System.currentTimeMillis()
        );
    }
}
