package com.mil.trdss.ro.domain.dto;

public record LoginResponseDTO(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {}
