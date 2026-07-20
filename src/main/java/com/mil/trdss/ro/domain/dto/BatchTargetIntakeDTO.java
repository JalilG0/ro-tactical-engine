package com.mil.trdss.ro.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchTargetIntakeDTO(
        @NotEmpty @Valid List<@Valid TargetIntakeDTO> intakes
) {}
