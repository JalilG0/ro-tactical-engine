package com.mil.trdss.ro.domain.dto;

import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TargetIntakeDTO(
        @NotBlank String eventId,
        long timestamp,
        @NotNull @Valid TargetInfo target,
        @NotNull @Valid EwContext ewContext,
        boolean allowPreemption
) {
    public record Coordinates(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lng
    ) {}

    public record TargetInfo(
            @NotBlank String targetId,
            @Min(1) @Max(10) int threatLevel,
            @NotNull @Valid Coordinates coordinates,
            @NotNull WeatherCondition weatherContext,
            @NotNull TargetMovementStatus movementStatus
    ) {}

    public record EwContext(
            boolean activeEwThreat,
            @NotNull List<@Valid Coordinates> jammerPolygon
    ) {}
}
