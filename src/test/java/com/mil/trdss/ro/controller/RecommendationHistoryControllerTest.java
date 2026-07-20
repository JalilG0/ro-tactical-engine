package com.mil.trdss.ro.controller;

import com.mil.trdss.ro.domain.dto.RecommendationHistoryDTO;
import com.mil.trdss.ro.security.JwtAuthenticationFilter;
import com.mil.trdss.ro.service.RecommendationHistoryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Security filters are disabled and JwtAuthenticationFilter is excluded from this slice's
// component scan: this test targets the history query endpoints, not the JWT filter chain
// (covered separately by JwtSecurityIntegrationTest).
@WebMvcTest(controllers = RecommendationHistoryController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class RecommendationHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationHistoryQueryService recommendationHistoryQueryService;

    @Test
    void returnsAuditRecordWhenFound() throws Exception {
        RecommendationHistoryDTO dto = new RecommendationHistoryDTO(
                "rec-1", "target-1", "explanation", null, Instant.now());
        when(recommendationHistoryQueryService.getByRecommendationId("rec-1")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/recommendations/history/rec-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value("rec-1"))
                .andExpect(jsonPath("$.targetId").value("target-1"));
    }

    @Test
    void returns404WhenAuditRecordMissing() throws Exception {
        when(recommendationHistoryQueryService.getByRecommendationId("missing"))
                .thenThrow(new RecommendationNotFoundException("RECOMMENDATION_NOT_FOUND: no audit record for recommendationId missing"));

        mockMvc.perform(get("/api/v1/recommendations/history/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recommendation Not Found"));
    }

    @Test
    void listsHistoryFilteredByTargetId() throws Exception {
        RecommendationHistoryDTO dto = new RecommendationHistoryDTO(
                "rec-2", "target-2", "explanation", null, Instant.now());
        when(recommendationHistoryQueryService.list(eq("target-2"), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/recommendations/history").param("targetId", "target-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recommendationId").value("rec-2"));
    }

    @Test
    void listsAllHistoryWhenNoTargetIdGiven() throws Exception {
        when(recommendationHistoryQueryService.list(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/recommendations/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
