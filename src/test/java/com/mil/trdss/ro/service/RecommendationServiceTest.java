package com.mil.trdss.ro.service;

import com.mil.trdss.ro.controller.DuplicateEventException;
import com.mil.trdss.ro.controller.StaleDataException;
import com.mil.trdss.ro.domain.dto.TacticalRecommendationDTO;
import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.domain.enums.AssetStatus;
import com.mil.trdss.ro.domain.enums.MunitionType;
import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
import com.mil.trdss.ro.engine.ExclusionEngine;
import com.mil.trdss.ro.engine.TacticalScoringEngine;
import com.mil.trdss.ro.engine.XaiExplanationGenerator;
import com.mil.trdss.ro.repository.RecommendationHistoryRepository;
import com.mil.trdss.ro.repository.cache.FleetStatusCacheRepository;
import com.mil.trdss.ro.repository.entity.RecommendationHistoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ExclusionEngine exclusionEngine;
    @Mock
    private TacticalScoringEngine tacticalScoringEngine;
    @Mock
    private XaiExplanationGenerator xaiExplanationGenerator;
    @Mock
    private FleetStatusCacheRepository fleetStatusCacheRepository;
    @Mock
    private RecommendationHistoryRepository recommendationHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                exclusionEngine, tacticalScoringEngine, xaiExplanationGenerator,
                fleetStatusCacheRepository, recommendationHistoryRepository, objectMapper);
    }

    @Test
    void rejectsPayloadsOlderThanTenSeconds() {
        TargetIntakeDTO staleIntake = intakeWithTimestamp(System.currentTimeMillis() - 15_000L);

        assertThatThrownBy(() -> recommendationService.calculateRecommendation(staleIntake))
                .isInstanceOf(StaleDataException.class);
    }

    @Test
    void rejectsDuplicateEventIds() {
        TargetIntakeDTO intake = intakeWithTimestamp(System.currentTimeMillis());
        when(fleetStatusCacheRepository.isIdempotent(intake.eventId())).thenReturn(false);

        assertThatThrownBy(() -> recommendationService.calculateRecommendation(intake))
                .isInstanceOf(DuplicateEventException.class);
    }

    @Test
    void persistsAuditRecordAndAppliesShadowLocksOnSuccess() {
        TargetIntakeDTO intake = intakeWithTimestamp(System.currentTimeMillis());
        when(fleetStatusCacheRepository.isIdempotent(intake.eventId())).thenReturn(true);
        when(fleetStatusCacheRepository.getActiveFreePool()).thenReturn(List.of());

        ExclusionEngine.ExclusionResult exclusionResult = new ExclusionEngine.ExclusionResult(List.of(), List.of());
        when(exclusionEngine.filterEligibleAssets(any(), eq(intake))).thenReturn(exclusionResult);

        TacticalRecommendationDTO.RankedModelGroup topGroup = new TacticalRecommendationDTO.RankedModelGroup(
                "TB2", 2,
                List.of(new TacticalRecommendationDTO.SubCluster(
                        new TacticalRecommendationDTO.Location(39.0, 35.0, 1000),
                        AssetStatus.FREE,
                        List.of("asset-1", "asset-2"),
                        2,
                        true)),
                false);
        TacticalRecommendationDTO.ScoreBreakdown topAssetBreakdown = new TacticalRecommendationDTO.ScoreBreakdown(
                "asset-1", MunitionType.TOLUN, 8, 80, 50, 0, -20, 0, 110);
        TacticalScoringEngine.ScoringOutcome scoringOutcome =
                new TacticalScoringEngine.ScoringOutcome(List.of(topGroup), true, false, topAssetBreakdown);
        when(tacticalScoringEngine.generateRankedGroups(intake, List.of())).thenReturn(scoringOutcome);
        when(xaiExplanationGenerator.generate(eq(intake), any(), eq(scoringOutcome))).thenReturn("explanation");

        TacticalRecommendationDTO result = recommendationService.calculateRecommendation(intake);

        assertThat(result.targetId()).isEqualTo(intake.target().targetId());
        assertThat(result.xaiExplanation()).isEqualTo("explanation");
        assertThat(result.rankedModelGroups()).containsExactly(topGroup);
        assertThat(result.topAssetScoreBreakdown()).isEqualTo(topAssetBreakdown);

        verify(recommendationHistoryRepository).save(any(RecommendationHistoryEntity.class));
        verify(fleetStatusCacheRepository).applyShadowLock("asset-1");
        verify(fleetStatusCacheRepository).applyShadowLock("asset-2");
    }

    @Test
    void skipsShadowLocksWhenNoGroupsAreRanked() {
        TargetIntakeDTO intake = intakeWithTimestamp(System.currentTimeMillis());
        when(fleetStatusCacheRepository.isIdempotent(intake.eventId())).thenReturn(true);
        when(fleetStatusCacheRepository.getActiveFreePool()).thenReturn(List.of());

        ExclusionEngine.ExclusionResult exclusionResult = new ExclusionEngine.ExclusionResult(List.of(), List.of());
        when(exclusionEngine.filterEligibleAssets(any(), eq(intake))).thenReturn(exclusionResult);

        TacticalScoringEngine.ScoringOutcome emptyOutcome =
                new TacticalScoringEngine.ScoringOutcome(List.of(), false, false, null);
        when(tacticalScoringEngine.generateRankedGroups(intake, List.of())).thenReturn(emptyOutcome);
        when(xaiExplanationGenerator.generate(eq(intake), any(), eq(emptyOutcome))).thenReturn("no assets available");

        recommendationService.calculateRecommendation(intake);

        verify(fleetStatusCacheRepository, never()).applyShadowLock(any());
    }

    @Test
    void processesBatchSequentiallyAndReturnsOneResultPerIntake() {
        TargetIntakeDTO intake1 = intakeWithEventId("evt-batch-1");
        TargetIntakeDTO intake2 = intakeWithEventId("evt-batch-2");

        when(fleetStatusCacheRepository.isIdempotent(anyString())).thenReturn(true);
        when(fleetStatusCacheRepository.getActiveFreePool()).thenReturn(List.of());

        ExclusionEngine.ExclusionResult exclusionResult = new ExclusionEngine.ExclusionResult(List.of(), List.of());
        when(exclusionEngine.filterEligibleAssets(any(), any())).thenReturn(exclusionResult);

        TacticalScoringEngine.ScoringOutcome emptyOutcome =
                new TacticalScoringEngine.ScoringOutcome(List.of(), false, false, null);
        when(tacticalScoringEngine.generateRankedGroups(any(), eq(List.of()))).thenReturn(emptyOutcome);
        when(xaiExplanationGenerator.generate(any(), any(), eq(emptyOutcome))).thenReturn("no assets available");

        List<TacticalRecommendationDTO> results =
                recommendationService.calculateRecommendations(List.of(intake1, intake2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).targetId()).isEqualTo(intake1.target().targetId());
        assertThat(results.get(1).targetId()).isEqualTo(intake2.target().targetId());
        verify(recommendationHistoryRepository, times(2)).save(any(RecommendationHistoryEntity.class));
    }

    @Test
    void stopsBatchProcessingOnFirstRejectedIntake() {
        TargetIntakeDTO staleIntake = intakeWithTimestamp(System.currentTimeMillis() - 15_000L);
        TargetIntakeDTO validIntake = intakeWithEventId("evt-after-stale");

        assertThatThrownBy(() -> recommendationService.calculateRecommendations(List.of(staleIntake, validIntake)))
                .isInstanceOf(StaleDataException.class);

        verify(fleetStatusCacheRepository, never()).getActiveFreePool();
    }

    @Test
    void ingestHeartbeatDelegatesToCache() {
        TelemetryHeartbeatDTO telemetry = mock(TelemetryHeartbeatDTO.class);

        recommendationService.ingestHeartbeat(telemetry);

        verify(fleetStatusCacheRepository).saveTelemetry(telemetry);
    }

    private TargetIntakeDTO intakeWithEventId(String eventId) {
        return new TargetIntakeDTO(
                eventId,
                System.currentTimeMillis(),
                new TargetIntakeDTO.TargetInfo(
                        "target-" + eventId, 5,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(false, List.of()),
                false
        );
    }

    private TargetIntakeDTO intakeWithTimestamp(long timestamp) {
        return new TargetIntakeDTO(
                "evt-1",
                timestamp,
                new TargetIntakeDTO.TargetInfo(
                        "target-1", 5,
                        new TargetIntakeDTO.Coordinates(39.0, 35.0),
                        WeatherCondition.CLEAR, TargetMovementStatus.STATIONARY),
                new TargetIntakeDTO.EwContext(false, List.of()),
                false
        );
    }
}
