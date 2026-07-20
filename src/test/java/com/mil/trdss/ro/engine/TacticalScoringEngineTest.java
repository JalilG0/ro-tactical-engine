package com.mil.trdss.ro.engine;

import com.mil.trdss.ro.domain.dto.TacticalRecommendationDTO;
import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;
import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TacticalScoringEngineTest {

    private final TacticalScoringEngine scoringEngine = new TacticalScoringEngine();

    @Test
    void doesNotApplySwarmWhenPairedPowerOnlyTiesTheThreatLevel() {
        // Two MAM_C assets (power 5 each) against threatLevel 10: 2*5 == 10, not strictly greater,
        // so combining them must NOT be reported as achieving overmatch.
        TargetIntakeDTO intake = intakeWith(10, WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY, false);
        List<TelemetryHeartbeatDTO> assets = List.of(
                asset("asset-1", MunitionType.MAM_C),
                asset("asset-2", MunitionType.MAM_C));

        TacticalScoringEngine.ScoringOutcome outcome = scoringEngine.generateRankedGroups(intake, assets);

        assertThat(outcome.topGroupSwarmApplied()).isFalse();
        assertThat(outcome.topGroupOvermatchAchieved()).isFalse();
    }

    @Test
    void appliesSwarmWhenPairedPowerStrictlyExceedsTheThreatLevel() {
        // Two MAM_C assets (power 5 each) against threatLevel 9: 2*5 == 10 > 9, so the pair
        // genuinely overmatches and Swarm Allocation should be reported.
        TargetIntakeDTO intake = intakeWith(9, WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY, false);
        List<TelemetryHeartbeatDTO> assets = List.of(
                asset("asset-1", MunitionType.MAM_C),
                asset("asset-2", MunitionType.MAM_C));

        TacticalScoringEngine.ScoringOutcome outcome = scoringEngine.generateRankedGroups(intake, assets);

        assertThat(outcome.topGroupSwarmApplied()).isTrue();
    }

    @ParameterizedTest(name = "{0} + {1} weather + {2} target")
    @MethodSource("everyMunitionWeatherMovementCombination")
    void scoreBreakdownMatchesDoctrineForEveryMunitionWeatherMovementCombination(
            MunitionType munitionType, WeatherCondition weather, TargetMovementStatus movement) {
        // threatLevel=1 is below every munition's power, so overmatchBonus is always +50 here —
        // that isolates the weather/movement deltas being asserted below.
        TargetIntakeDTO intake = intakeWith(1, weather, movement, false);
        TelemetryHeartbeatDTO singleAsset = asset("asset-1", munitionType);

        TacticalScoringEngine.ScoringOutcome outcome =
                scoringEngine.generateRankedGroups(intake, List.of(singleAsset));
        TacticalRecommendationDTO.ScoreBreakdown breakdown = outcome.topAssetScoreBreakdown();

        int expectedPower = expectedPower(munitionType);
        int expectedWeatherAdjustment = expectedWeatherAdjustment(munitionType, weather);
        int expectedMovementAdjustment = expectedMovementAdjustment(munitionType, movement);

        assertThat(breakdown.assetId()).isEqualTo("asset-1");
        assertThat(breakdown.munitionType()).isEqualTo(munitionType);
        assertThat(breakdown.basePower()).isEqualTo(expectedPower);
        assertThat(breakdown.baseScore()).isEqualTo(expectedPower * 10);
        assertThat(breakdown.overmatchBonus()).isEqualTo(50);
        assertThat(breakdown.weatherAdjustment()).isEqualTo(expectedWeatherAdjustment);
        assertThat(breakdown.movementAdjustment()).isEqualTo(expectedMovementAdjustment);
        assertThat(breakdown.ewPenalty()).isZero();
        assertThat(breakdown.totalScore()).isEqualTo(
                expectedPower * 10 + 50 + expectedWeatherAdjustment + expectedMovementAdjustment);
    }

    @Test
    void ewPenaltyAppliesOnlyWhenThreatIsActiveWithANonEmptyJammerPolygon() {
        TargetIntakeDTO withActiveJammer = new TargetIntakeDTO(
                "evt-1", System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo("target-1", 1,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(true, List.of(new TargetIntakeDTO.Coordinates(39.1, 35.1))),
                false);

        TacticalScoringEngine.ScoringOutcome outcome =
                scoringEngine.generateRankedGroups(withActiveJammer, List.of(asset("asset-1", MunitionType.SOM_CRUISE_MISSILE)));

        assertThat(outcome.topAssetScoreBreakdown().ewPenalty()).isEqualTo(-5);
    }

    @Test
    void ewPenaltyIsZeroWhenThreatIsActiveButJammerPolygonIsEmpty() {
        TargetIntakeDTO withEmptyPolygon = new TargetIntakeDTO(
                "evt-1", System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo("target-1", 1,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(true, List.of()),
                false);

        TacticalScoringEngine.ScoringOutcome outcome =
                scoringEngine.generateRankedGroups(withEmptyPolygon, List.of(asset("asset-1", MunitionType.SOM_CRUISE_MISSILE)));

        assertThat(outcome.topAssetScoreBreakdown().ewPenalty()).isZero();
    }

    private static Stream<Arguments> everyMunitionWeatherMovementCombination() {
        return Stream.of(MunitionType.values())
                .flatMap(munition -> Stream.of(WeatherCondition.values())
                        .flatMap(weather -> Stream.of(TargetMovementStatus.values())
                                .map(movement -> Arguments.of(munition, weather, movement))));
    }

    private static int expectedPower(MunitionType type) {
        return switch (type) {
            case MAM_L -> 3;
            case MAM_C -> 5;
            case BOZOK -> 6;
            case TOLUN -> 8;
            case SOM_CRUISE_MISSILE -> 10;
        };
    }

    private static boolean isLaserGuided(MunitionType type) {
        return type == MunitionType.MAM_L || type == MunitionType.MAM_C || type == MunitionType.BOZOK;
    }

    private static boolean isGpsInsGuided(MunitionType type) {
        return type == MunitionType.TOLUN || type == MunitionType.SOM_CRUISE_MISSILE;
    }

    // Only DENSE_FOG currently affects scoring; HEAVY_RAIN and STORM are no-ops today.
    private static int expectedWeatherAdjustment(MunitionType type, WeatherCondition weather) {
        if (weather != WeatherCondition.DENSE_FOG) {
            return 0;
        }
        if (isLaserGuided(type)) {
            return -30;
        }
        if (isGpsInsGuided(type)) {
            return 20;
        }
        return 0;
    }

    private static int expectedMovementAdjustment(MunitionType type, TargetMovementStatus movement) {
        if (movement == TargetMovementStatus.MOVING) {
            if (isLaserGuided(type)) {
                return 25;
            }
            if (isGpsInsGuided(type)) {
                return -20;
            }
            return 0;
        }
        return isGpsInsGuided(type) ? 10 : 0;
    }

    private TargetIntakeDTO intakeWith(
            int threatLevel, WeatherCondition weather, TargetMovementStatus movement, boolean ewActive) {
        return new TargetIntakeDTO(
                "evt-1",
                System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo(
                        "target-1", threatLevel,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        weather, movement),
                new TargetIntakeDTO.EwContext(ewActive, List.of()),
                false
        );
    }

    private TelemetryHeartbeatDTO asset(String assetId, MunitionType munitionType) {
        return new TelemetryHeartbeatDTO(
                assetId,
                "TB2",
                new TelemetryHeartbeatDTO.Location(39.0, 35.0, 1000),
                AssetStatus.FREE,
                100,
                100,
                new TelemetryHeartbeatDTO.MunitionState(munitionType, List.of(munitionType), 3),
                System.currentTimeMillis()
        );
    }
}
