package com.mil.trdss.ro.domain.dto;

import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
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

class TargetIntakeDTOValidationTest {

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
        TargetIntakeDTO dto = validIntake(5);

        Set<ConstraintViolation<TargetIntakeDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsThreatLevelOutOfRange() {
        TargetIntakeDTO dto = validIntake(11);

        Set<ConstraintViolation<TargetIntakeDTO>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("target.threatLevel");
    }

    @Test
    void rejectsCoordinatesOutOfRange() {
        TargetIntakeDTO dto = new TargetIntakeDTO(
                "evt-1",
                System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo(
                        "target-1",
                        5,
                        new TargetIntakeDTO.Coordinates(95.0, 200.0),
                        WeatherCondition.CLEAR,
                        TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(false, List.of()),
                false
        );

        Set<ConstraintViolation<TargetIntakeDTO>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
    }

    private TargetIntakeDTO validIntake(int threatLevel) {
        return new TargetIntakeDTO(
                "evt-1",
                System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo(
                        "target-1",
                        threatLevel,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR,
                        TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(false, List.of()),
                false
        );
    }
}
