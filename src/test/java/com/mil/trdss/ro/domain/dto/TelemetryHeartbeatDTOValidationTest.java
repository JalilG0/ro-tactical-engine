package com.mil.trdss.ro.domain.dto;

import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryHeartbeatDTOValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsAFullyValidPayload() {
        TelemetryHeartbeatDTO dto = validHeartbeat(50, 80, 3);

        Set<ConstraintViolation<TelemetryHeartbeatDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsFuelPercentageAboveOneHundred() {
        TelemetryHeartbeatDTO dto = validHeartbeat(50, 101, 3);

        Set<ConstraintViolation<TelemetryHeartbeatDTO>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("fuelPercentage");
    }

    @Test
    void rejectsNegativeFuelPercentage() {
        TelemetryHeartbeatDTO dto = validHeartbeat(50, -1, 3);

        Set<ConstraintViolation<TelemetryHeartbeatDTO>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("fuelPercentage");
    }

    @Test
    void rejectsLinkQualityOutOfBounds() {
        TelemetryHeartbeatDTO dto = validHeartbeat(150, 80, 3);

        Set<ConstraintViolation<TelemetryHeartbeatDTO>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("linkQuality");
    }

    @Test
    void rejectsNegativeMunitionCount() {
        TelemetryHeartbeatDTO dto = validHeartbeat(50, 80, -1);

        Set<ConstraintViolation<TelemetryHeartbeatDTO>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("munition.count");
    }

    @Test
    void rejectsBlankAssetId() {
        TelemetryHeartbeatDTO dto = new TelemetryHeartbeatDTO(
                " ",
                "TB2",
                new TelemetryHeartbeatDTO.Location(39.0, 35.0, 1000),
                AssetStatus.FREE,
                50,
                80,
                new TelemetryHeartbeatDTO.MunitionState(MunitionType.MAM_L, List.of(MunitionType.MAM_L), 3),
                System.currentTimeMillis()
        );

        Set<ConstraintViolation<TelemetryHeartbeatDTO>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("assetId");
    }

    private TelemetryHeartbeatDTO validHeartbeat(int linkQuality, int fuelPercentage, int munitionCount) {
        return new TelemetryHeartbeatDTO(
                "asset-1",
                "TB2",
                new TelemetryHeartbeatDTO.Location(39.0, 35.0, 1000),
                AssetStatus.FREE,
                linkQuality,
                fuelPercentage,
                new TelemetryHeartbeatDTO.MunitionState(MunitionType.MAM_L, List.of(MunitionType.MAM_L), munitionCount),
                System.currentTimeMillis()
        );
    }
}
