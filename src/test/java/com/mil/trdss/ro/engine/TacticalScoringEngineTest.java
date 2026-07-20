package com.mil.trdss.ro.engine;

import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;
import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TacticalScoringEngineTest {

    private final TacticalScoringEngine scoringEngine = new TacticalScoringEngine();

    @Test
    void doesNotApplySwarmWhenPairedPowerOnlyTiesTheThreatLevel() {
        // Two MAM_C assets (power 5 each) against threatLevel 10: 2*5 == 10, not strictly greater,
        // so combining them must NOT be reported as achieving overmatch.
        TargetIntakeDTO intake = intakeWithThreatLevel(10);
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
        TargetIntakeDTO intake = intakeWithThreatLevel(9);
        List<TelemetryHeartbeatDTO> assets = List.of(
                asset("asset-1", MunitionType.MAM_C),
                asset("asset-2", MunitionType.MAM_C));

        TacticalScoringEngine.ScoringOutcome outcome = scoringEngine.generateRankedGroups(intake, assets);

        assertThat(outcome.topGroupSwarmApplied()).isTrue();
    }

    private TargetIntakeDTO intakeWithThreatLevel(int threatLevel) {
        return new TargetIntakeDTO(
                "evt-1",
                System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo(
                        "target-1", threatLevel,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(false, List.of()),
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
