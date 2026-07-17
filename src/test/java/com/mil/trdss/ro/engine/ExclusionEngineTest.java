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

class ExclusionEngineTest {

    private final ExclusionEngine exclusionEngine = new ExclusionEngine();

    // Target sits right on top of the asset so round-trip distance is ~0km,
    // keeping the fuel threshold out of play unless a test overrides fuel/location.
    private final TargetIntakeDTO nearbyTarget = new TargetIntakeDTO(
            "evt-1",
            System.currentTimeMillis(),
            new TargetIntakeDTO.TargetInfo(
                    "target-1", 5,
                    new TargetIntakeDTO.Coordinates(39.0, 35.0),
                    WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
            new TargetIntakeDTO.EwContext(false, List.of()),
            false
    );

    @Test
    void excludesAssetsUnderMaintenance() {
        TelemetryHeartbeatDTO asset = asset(AssetStatus.MAINTENANCE_REQUIRED, 100, 3, 39.0, 35.0);

        ExclusionEngine.ExclusionResult result = exclusionEngine.filterEligibleAssets(List.of(asset), nearbyTarget);

        assertThat(result.eligibleAssets()).isEmpty();
        assertThat(result.excludedAssets())
                .singleElement()
                .satisfies(reason -> assertThat(reason.reasonCode()).isEqualTo(ExclusionEngine.REASON_MAINTENANCE_REQUIRED));
    }

    @Test
    void excludesAssetsUnderManualOverride() {
        TelemetryHeartbeatDTO asset = asset(AssetStatus.MANUAL_OVERRIDE, 100, 3, 39.0, 35.0);

        ExclusionEngine.ExclusionResult result = exclusionEngine.filterEligibleAssets(List.of(asset), nearbyTarget);

        assertThat(result.eligibleAssets()).isEmpty();
        assertThat(result.excludedAssets())
                .singleElement()
                .satisfies(reason -> assertThat(reason.reasonCode()).isEqualTo(ExclusionEngine.REASON_MANUAL_OVERRIDE));
    }

    @Test
    void excludesAssetsWithNoMunitionsLeft() {
        TelemetryHeartbeatDTO asset = asset(AssetStatus.FREE, 100, 0, 39.0, 35.0);

        ExclusionEngine.ExclusionResult result = exclusionEngine.filterEligibleAssets(List.of(asset), nearbyTarget);

        assertThat(result.eligibleAssets()).isEmpty();
        assertThat(result.excludedAssets())
                .singleElement()
                .satisfies(reason -> assertThat(reason.reasonCode()).isEqualTo(ExclusionEngine.REASON_ZERO_MUNITION_COUNT));
    }

    @Test
    void excludesAssetsBelowBingoFuelThresholdWhenFarFromTarget() {
        // ~1000km away with only 5% fuel can't make the round trip.
        TelemetryHeartbeatDTO asset = asset(AssetStatus.FREE, 5, 3, 48.0, 35.0);

        ExclusionEngine.ExclusionResult result = exclusionEngine.filterEligibleAssets(List.of(asset), nearbyTarget);

        assertThat(result.eligibleAssets()).isEmpty();
        assertThat(result.excludedAssets())
                .singleElement()
                .satisfies(reason -> assertThat(reason.reasonCode()).isEqualTo(ExclusionEngine.REASON_BELOW_BINGO_FUEL_THRESHOLD));
    }

    @Test
    void keepsAssetsThatPassEveryCheck() {
        TelemetryHeartbeatDTO asset = asset(AssetStatus.FREE, 100, 3, 39.0, 35.0);

        ExclusionEngine.ExclusionResult result = exclusionEngine.filterEligibleAssets(List.of(asset), nearbyTarget);

        assertThat(result.excludedAssets()).isEmpty();
        assertThat(result.eligibleAssets()).containsExactly(asset);
    }

    private TelemetryHeartbeatDTO asset(AssetStatus status, int fuelPercentage, int munitionCount, double lat, double lng) {
        return new TelemetryHeartbeatDTO(
                "asset-1",
                "TB2",
                new TelemetryHeartbeatDTO.Location(lat, lng, 1000),
                status,
                100,
                fuelPercentage,
                new TelemetryHeartbeatDTO.MunitionState(MunitionType.MAM_L, List.of(MunitionType.MAM_L), munitionCount),
                System.currentTimeMillis()
        );
    }
}
