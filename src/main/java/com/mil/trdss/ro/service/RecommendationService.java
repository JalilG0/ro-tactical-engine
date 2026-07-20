package com.mil.trdss.ro.service;

import com.mil.trdss.ro.controller.DuplicateEventException;
import com.mil.trdss.ro.controller.StaleDataException;
import com.mil.trdss.ro.domain.dto.TacticalRecommendationDTO;
import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.dto.TelemetryHeartbeatDTO;
import com.mil.trdss.ro.engine.ExclusionEngine;
import com.mil.trdss.ro.engine.TacticalScoringEngine;
import com.mil.trdss.ro.engine.XaiExplanationGenerator;
import com.mil.trdss.ro.repository.RecommendationHistoryRepository;
import com.mil.trdss.ro.repository.cache.FleetStatusCacheRepository;
import com.mil.trdss.ro.repository.entity.RecommendationHistoryEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class RecommendationService {

    private static final long STALE_DATA_THRESHOLD_MS = 10_000L;

    private final ExclusionEngine exclusionEngine;
    private final TacticalScoringEngine tacticalScoringEngine;
    private final XaiExplanationGenerator xaiExplanationGenerator;
    private final FleetStatusCacheRepository fleetStatusCacheRepository;
    private final RecommendationHistoryRepository recommendationHistoryRepository;
    private final ObjectMapper objectMapper;

    public RecommendationService(
            ExclusionEngine exclusionEngine,
            TacticalScoringEngine tacticalScoringEngine,
            XaiExplanationGenerator xaiExplanationGenerator,
            FleetStatusCacheRepository fleetStatusCacheRepository,
            RecommendationHistoryRepository recommendationHistoryRepository,
            ObjectMapper objectMapper) {
        this.exclusionEngine = exclusionEngine;
        this.tacticalScoringEngine = tacticalScoringEngine;
        this.xaiExplanationGenerator = xaiExplanationGenerator;
        this.fleetStatusCacheRepository = fleetStatusCacheRepository;
        this.recommendationHistoryRepository = recommendationHistoryRepository;
        this.objectMapper = objectMapper;
    }

    public TacticalRecommendationDTO calculateRecommendation(TargetIntakeDTO intakeDTO) {
        rejectIfStale(intakeDTO);
        rejectIfDuplicate(intakeDTO);

        List<TelemetryHeartbeatDTO> fleetPool = fleetStatusCacheRepository.getActiveFreePool();
        ExclusionEngine.ExclusionResult exclusionResult = exclusionEngine.filterEligibleAssets(fleetPool, intakeDTO);

        TacticalScoringEngine.ScoringOutcome scoringOutcome =
                tacticalScoringEngine.generateRankedGroups(intakeDTO, exclusionResult.eligibleAssets());

        String xaiExplanation = xaiExplanationGenerator.generate(intakeDTO, exclusionResult.excludedAssets(), scoringOutcome);

        TacticalRecommendationDTO recommendation = new TacticalRecommendationDTO(
                UUID.randomUUID().toString(),
                intakeDTO.target().targetId(),
                System.currentTimeMillis(),
                xaiExplanation,
                scoringOutcome.rankedModelGroups()
        );

        persistAuditRecord(recommendation);
        applyShadowLocksToTopGroup(scoringOutcome.rankedModelGroups());

        return recommendation;
    }

    // Sequential by design: each intake's shadow-lock write is visible to the next intake's
    // pool read, so two targets in the same batch can't be recommended the same asset.
    public List<TacticalRecommendationDTO> calculateRecommendations(List<TargetIntakeDTO> intakes) {
        return intakes.stream().map(this::calculateRecommendation).toList();
    }

    public void ingestHeartbeat(TelemetryHeartbeatDTO telemetry) {
        fleetStatusCacheRepository.saveTelemetry(telemetry);
    }

    private void rejectIfStale(TargetIntakeDTO intakeDTO) {
        if (System.currentTimeMillis() - intakeDTO.timestamp() > STALE_DATA_THRESHOLD_MS) {
            throw new StaleDataException("STALE_DATA_REJECTED: Payload age exceeds 10s threshold");
        }
    }

    private void rejectIfDuplicate(TargetIntakeDTO intakeDTO) {
        if (!fleetStatusCacheRepository.isIdempotent(intakeDTO.eventId())) {
            throw new DuplicateEventException("DUPLICATE_EVENT_REJECTED: eventId " + intakeDTO.eventId() + " already processed");
        }
    }

    private void persistAuditRecord(TacticalRecommendationDTO recommendation) {
        RecommendationHistoryEntity entity = RecommendationHistoryEntity.builder()
                .recommendationId(recommendation.recommendationId())
                .targetId(recommendation.targetId())
                .xaiExplanation(recommendation.xaiExplanation())
                .rankedPayload(objectMapper.valueToTree(recommendation.rankedModelGroups()))
                .build();
        recommendationHistoryRepository.save(entity);
    }

    private void applyShadowLocksToTopGroup(List<TacticalRecommendationDTO.RankedModelGroup> rankedModelGroups) {
        if (rankedModelGroups.isEmpty()) {
            return;
        }
        TacticalRecommendationDTO.RankedModelGroup topGroup = rankedModelGroups.get(0);
        for (TacticalRecommendationDTO.SubCluster subCluster : topGroup.subClusters()) {
            for (String assetId : subCluster.assetIds()) {
                fleetStatusCacheRepository.applyShadowLock(assetId);
            }
        }
    }
}
