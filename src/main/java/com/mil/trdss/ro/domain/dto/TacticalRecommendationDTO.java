package com.mil.trdss.ro.domain.dto;

import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;

import java.util.List;

public record TacticalRecommendationDTO(
        String recommendationId,
        String targetId,
        long timestamp,
        String xaiExplanation,
        ScoreBreakdown topAssetScoreBreakdown,
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

    // Machine-readable score composition for the single highest-ranked asset overall —
    // the same numbers XaiExplanationGenerator turns into a Turkish sentence, exposed here
    // for programmatic consumption instead of string parsing. Null when no asset is eligible.
    public record ScoreBreakdown(
            String assetId,
            MunitionType munitionType,
            int basePower,
            int baseScore,
            int overmatchBonus,
            int weatherAdjustment,
            int movementAdjustment,
            int ewPenalty,
            int totalScore
    ) {}
}
