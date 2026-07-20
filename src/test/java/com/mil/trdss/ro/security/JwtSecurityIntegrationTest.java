package com.mil.trdss.ro.security;

import com.mil.trdss.ro.controller.RecommendationController;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;
import com.mil.trdss.ro.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Exercises the real SecurityFilterChain (unlike RecommendationControllerTest, which
// disables it) to prove the JWT gate actually rejects/accepts requests end-to-end.
@WebMvcTest(RecommendationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void rejectsRequestsWithNoBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTelemetry())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication Required"));
    }

    @Test
    void rejectsRequestsWithAMalformedToken() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/heartbeat")
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTelemetry())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsRequestsWithAValidBearerToken() throws Exception {
        String token = jwtService.generateToken("operator");

        mockMvc.perform(post("/api/v1/telemetry/heartbeat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTelemetry())))
                .andExpect(status().isOk());
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
