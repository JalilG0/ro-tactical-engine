package com.mil.trdss.ro.engine;

import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.AssetStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExclusionEngine {

    public static final String REASON_MAINTENANCE_REQUIRED = "MAINTENANCE_REQUIRED";
    public static final String REASON_MANUAL_OVERRIDE = "MANUAL_OVERRIDE";
    public static final String REASON_ZERO_MUNITION_COUNT = "ZERO_MUNITION_COUNT";
    public static final String REASON_BELOW_BINGO_FUEL_THRESHOLD = "BELOW_BINGO_FUEL_THRESHOLD";

    // Assumed fleet-wide operational range (km) at 100% fuel; used to translate
    // round-trip distance into the required Bingo Fuel percentage.
    private static final double OPERATIONAL_RANGE_KM_AT_FULL_FUEL = 500.0;
    private static final double RESERVE_FUEL_MARGIN = 1.15;

    public ExclusionResult filterEligibleAssets(List<TelemetryHeartbeatDTO> fleetPool, TargetIntakeDTO targetIntake) {
        List<TelemetryHeartbeatDTO> eligible = new ArrayList<>();
        List<ExclusionReason> excluded = new ArrayList<>();

        for (TelemetryHeartbeatDTO asset : fleetPool) {
            if (asset.status() == AssetStatus.MAINTENANCE_REQUIRED) {
                excluded.add(new ExclusionReason(asset.assetId(), REASON_MAINTENANCE_REQUIRED));
                continue;
            }
            if (asset.status() == AssetStatus.MANUAL_OVERRIDE) {
                excluded.add(new ExclusionReason(asset.assetId(), REASON_MANUAL_OVERRIDE));
                continue;
            }
            if (asset.munition().count() <= 0) {
                excluded.add(new ExclusionReason(asset.assetId(), REASON_ZERO_MUNITION_COUNT));
                continue;
            }

            double bingoThreshold = bingoFuelThresholdPercent(asset, targetIntake);
            if (asset.fuelPercentage() < bingoThreshold) {
                excluded.add(new ExclusionReason(asset.assetId(), REASON_BELOW_BINGO_FUEL_THRESHOLD));
                continue;
            }

            eligible.add(asset);
        }

        return new ExclusionResult(eligible, excluded);
    }

    private double bingoFuelThresholdPercent(TelemetryHeartbeatDTO asset, TargetIntakeDTO targetIntake) {
        double distanceToTargetKm = GeoUtils.distanceKm(
                asset.location().lat(), asset.location().lng(),
                targetIntake.target().coordinates().lat(), targetIntake.target().coordinates().lng()
        );
        double returnToBaseKm = distanceToTargetKm;
        double roundTripKm = distanceToTargetKm + returnToBaseKm;

        double requiredFuelPercentage = (roundTripKm / OPERATIONAL_RANGE_KM_AT_FULL_FUEL) * 100.0;
        return requiredFuelPercentage * RESERVE_FUEL_MARGIN;
    }

    public record ExclusionResult(List<TelemetryHeartbeatDTO> eligibleAssets, List<ExclusionReason> excludedAssets) {
    }

    public record ExclusionReason(String assetId, String reasonCode) {
    }
}
