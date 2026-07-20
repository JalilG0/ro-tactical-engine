package com.mil.trdss.ro.engine;

import com.mil.trdss.ro.domain.dto.TacticalRecommendationDTO;
import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.MunitionType;
import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TacticalScoringEngine {

    private static final int MAX_RANKED_MODEL_GROUPS = 10;

    private static final int OVERMATCH_BONUS = 50;
    private static final int FOG_LASER_PENALTY = 30;
    private static final int FOG_GPS_INS_BONUS = 20;
    private static final int EW_REROUTE_PENALTY = 5;
    private static final int MOVING_TARGET_LASER_BONUS = 25;
    private static final int MOVING_TARGET_GPS_INS_PENALTY = 20;
    private static final int STATIONARY_TARGET_GPS_INS_BONUS = 10;

    private static final Comparator<ScoredAsset> TIE_BREAK_COMPARATOR =
            Comparator.comparingInt(ScoredAsset::score).reversed()
                    .thenComparing((ScoredAsset sa) -> sa.asset().fuelPercentage(), Comparator.reverseOrder())
                    .thenComparing(sa -> sa.asset().assetId());

    public ScoringOutcome generateRankedGroups(
            TargetIntakeDTO targetIntake, List<TelemetryHeartbeatDTO> eligibleAssets) {

        if (eligibleAssets.isEmpty()) {
            return new ScoringOutcome(List.of(), false, false);
        }

        List<ScoredAsset> scoredAssets = eligibleAssets.stream()
                .map(asset -> new ScoredAsset(asset, scoreAsset(asset, targetIntake)))
                .sorted(TIE_BREAK_COMPARATOR)
                .toList();

        Map<String, List<ScoredAsset>> byModel = new LinkedHashMap<>();
        for (ScoredAsset scored : scoredAssets) {
            byModel.computeIfAbsent(scored.asset().model(), key -> new ArrayList<>()).add(scored);
        }

        List<ModelGroupResult> groupResults = new ArrayList<>();
        for (Map.Entry<String, List<ScoredAsset>> entry : byModel.entrySet()) {
            groupResults.add(buildModelGroup(entry.getKey(), entry.getValue(), targetIntake));
        }

        List<ModelGroupResult> limited = groupResults.stream().limit(MAX_RANKED_MODEL_GROUPS).toList();
        List<TacticalRecommendationDTO.RankedModelGroup> rankedGroups = limited.stream()
                .map(ModelGroupResult::group)
                .toList();

        ModelGroupResult topGroup = limited.get(0);
        return new ScoringOutcome(rankedGroups, topGroup.overmatchAchieved(), topGroup.swarmApplied());
    }

    private int scoreAsset(TelemetryHeartbeatDTO asset, TargetIntakeDTO targetIntake) {
        MunitionType currentType = asset.munition().currentType();
        int power = munitionPower(currentType);
        int threatLevel = targetIntake.target().threatLevel();

        int score = power * 10;

        // Overmatch Doctrine: munition power must strictly exceed the target's threat level.
        if (power > threatLevel) {
            score += OVERMATCH_BONUS;
        }

        WeatherCondition weather = targetIntake.target().weatherContext();
        if (weather == WeatherCondition.DENSE_FOG) {
            if (isLaserGuided(currentType)) {
                score -= FOG_LASER_PENALTY;
            } else if (isGpsInsGuided(currentType)) {
                score += FOG_GPS_INS_BONUS;
            }
        }

        TargetMovementStatus movementStatus = targetIntake.target().movementStatus();
        if (movementStatus == TargetMovementStatus.MOVING) {
            // Moving Target Doctrine: laser designation (FLIR/laser) can actively re-track a moving
            // target in flight, while GPS/INS-only munitions commit to a fixed coordinate at launch.
            if (isLaserGuided(currentType)) {
                score += MOVING_TARGET_LASER_BONUS;
            } else if (isGpsInsGuided(currentType)) {
                score -= MOVING_TARGET_GPS_INS_PENALTY;
            }
        } else if (isGpsInsGuided(currentType)) {
            score += STATIONARY_TARGET_GPS_INS_BONUS;
        }

        if (targetIntake.ewContext().activeEwThreat() && !targetIntake.ewContext().jammerPolygon().isEmpty()) {
            // Active jammer polygon forces a detour waypoint, penalizing every asset uniformly.
            score -= EW_REROUTE_PENALTY;
        }

        return score;
    }

    private ModelGroupResult buildModelGroup(
            String modelName, List<ScoredAsset> scoredAssets, TargetIntakeDTO targetIntake) {

        boolean tieBreakerApplied = hasScoreTie(scoredAssets);

        boolean singleAssetOvermatchAchieved = scoredAssets.stream()
                .anyMatch(sa -> munitionPower(sa.asset().munition().currentType()) > targetIntake.target().threatLevel());
        MunitionType representativeType = scoredAssets.get(0).asset().munition().currentType();
        boolean isMediumTier = isMediumTier(representativeType);
        // Swarm Allocation only pays off if two of these assets combined actually
        // overmatch the target; otherwise "swarm applied" would misreport an
        // overmatch that was never mathematically achieved.
        boolean pairedOvermatchAchieved = munitionPower(representativeType) * 2 > targetIntake.target().threatLevel();

        Map<String, List<ScoredAsset>> byLocationAndStatus = new LinkedHashMap<>();
        for (ScoredAsset scored : scoredAssets) {
            byLocationAndStatus.computeIfAbsent(clusterKey(scored.asset()), key -> new ArrayList<>()).add(scored);
        }

        List<TacticalRecommendationDTO.SubCluster> subClusters = new ArrayList<>();
        int totalAvailableCount = 0;
        boolean swarmApplied = false;
        for (List<ScoredAsset> cluster : byLocationAndStatus.values()) {
            TelemetryHeartbeatDTO representative = cluster.get(0).asset();
            List<String> assetIds = cluster.stream().map(sa -> sa.asset().assetId()).toList();

            // Swarm Allocation: pair up medium-tier assets when no single asset overmatches alone,
            // but only if the pair's combined power actually overmatches the target.
            boolean swarmEligible = !singleAssetOvermatchAchieved && isMediumTier && cluster.size() >= 2
                    && pairedOvermatchAchieved;
            int maxSelectable = swarmEligible ? Math.min(2, cluster.size()) : cluster.size();
            boolean isBatchSelectable = cluster.size() > 1;
            swarmApplied = swarmApplied || swarmEligible;

            subClusters.add(new TacticalRecommendationDTO.SubCluster(
                    new TacticalRecommendationDTO.Location(
                            representative.location().lat(),
                            representative.location().lng(),
                            representative.location().alt()),
                    representative.status(),
                    assetIds,
                    maxSelectable,
                    isBatchSelectable
            ));

            totalAvailableCount += assetIds.size();
        }

        TacticalRecommendationDTO.RankedModelGroup group =
                new TacticalRecommendationDTO.RankedModelGroup(modelName, totalAvailableCount, subClusters, tieBreakerApplied);
        return new ModelGroupResult(group, singleAssetOvermatchAchieved, swarmApplied);
    }

    private boolean hasScoreTie(List<ScoredAsset> scoredAssets) {
        Set<Integer> seenScores = new HashSet<>();
        for (ScoredAsset scored : scoredAssets) {
            if (!seenScores.add(scored.score())) {
                return true;
            }
        }
        return false;
    }

    private String clusterKey(TelemetryHeartbeatDTO asset) {
        return asset.location().lat() + "," + asset.location().lng() + "|" + asset.status();
    }

    private static int munitionPower(MunitionType type) {
        return switch (type) {
            case MAM_L -> 3;
            case MAM_C -> 5;
            case BOZOK -> 6;
            case TOLUN -> 8;
            case SOM_CRUISE_MISSILE -> 10;
        };
    }

    private static boolean isMediumTier(MunitionType type) {
        return type == MunitionType.MAM_C || type == MunitionType.BOZOK;
    }

    private static boolean isLaserGuided(MunitionType type) {
        return type == MunitionType.MAM_L || type == MunitionType.MAM_C || type == MunitionType.BOZOK;
    }

    private static boolean isGpsInsGuided(MunitionType type) {
        return type == MunitionType.TOLUN || type == MunitionType.SOM_CRUISE_MISSILE;
    }

    private record ScoredAsset(TelemetryHeartbeatDTO asset, int score) {
    }

    private record ModelGroupResult(
            TacticalRecommendationDTO.RankedModelGroup group,
            boolean overmatchAchieved,
            boolean swarmApplied) {
    }

    public record ScoringOutcome(
            List<TacticalRecommendationDTO.RankedModelGroup> rankedModelGroups,
            boolean topGroupOvermatchAchieved,
            boolean topGroupSwarmApplied) {
    }
}
