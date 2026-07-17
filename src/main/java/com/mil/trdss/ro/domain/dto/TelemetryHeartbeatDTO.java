package com.mil.trdss.ro.domain.dto;

import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record TelemetryHeartbeatDTO(
        @NotBlank String assetId,
        @NotBlank String model,
        @NotNull @Valid Location location,
        @NotNull AssetStatus status,
        @Min(0) @Max(100) int linkQuality,
        @Min(0) @Max(100) int fuelPercentage,
        @NotNull @Valid MunitionState munition,
        long timestamp
) {
    public record Location(double lat, double lng, double alt) {}

    public record MunitionState(
            @NotNull MunitionType currentType,
            @NotNull List<MunitionType> capableTypes,
            @PositiveOrZero int count
    ) {}
}
