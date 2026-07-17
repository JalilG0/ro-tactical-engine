package com.mil.trdss.ro.domain.dto;

import com.mil.trdss.ro.domain.enums.AssetStatus;

import java.util.List;

public record TacticalRecommendationDTO(
        String recommendationId,
        String targetId,
        long timestamp,
        String xaiExplanation,
        List<RankedModelGroup> rankedModelGroups
) {
    public record Location(double lat, double lng, double alt) {}

    public record SubCluster(
            Location location,
            AssetStatus status,
            List<String> assetIds,
            int maxSelectable,
            boolean isBatchSelectable
    ) {}

    public record RankedModelGroup(
            String modelName,
            int totalAvailableCount,
            List<SubCluster> subClusters,
            boolean tieBreakerApplied
    ) {}
}
