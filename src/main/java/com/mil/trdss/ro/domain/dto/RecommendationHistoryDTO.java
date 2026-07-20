package com.mil.trdss.ro.domain.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record RecommendationHistoryDTO(
        String recommendationId,
        String targetId,
        String xaiExplanation,
        JsonNode rankedPayload,
        Instant createdAt
) {}
