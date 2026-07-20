package com.mil.trdss.ro.service;

import com.mil.trdss.ro.controller.RecommendationNotFoundException;
import com.mil.trdss.ro.domain.dto.RecommendationHistoryDTO;
import com.mil.trdss.ro.repository.RecommendationHistoryRepository;
import com.mil.trdss.ro.repository.entity.RecommendationHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RecommendationHistoryQueryService {

    private final RecommendationHistoryRepository recommendationHistoryRepository;

    public RecommendationHistoryQueryService(RecommendationHistoryRepository recommendationHistoryRepository) {
        this.recommendationHistoryRepository = recommendationHistoryRepository;
    }

    public RecommendationHistoryDTO getByRecommendationId(String recommendationId) {
        return recommendationHistoryRepository.findByRecommendationId(recommendationId)
                .map(RecommendationHistoryQueryService::toDto)
                .orElseThrow(() -> new RecommendationNotFoundException(
                        "RECOMMENDATION_NOT_FOUND: no audit record for recommendationId " + recommendationId));
    }

    public Page<RecommendationHistoryDTO> list(String targetId, Pageable pageable) {
        Page<RecommendationHistoryEntity> page = targetId == null
                ? recommendationHistoryRepository.findAll(pageable)
                : recommendationHistoryRepository.findByTargetId(targetId, pageable);
        return page.map(RecommendationHistoryQueryService::toDto);
    }

    private static RecommendationHistoryDTO toDto(RecommendationHistoryEntity entity) {
        return new RecommendationHistoryDTO(
                entity.getRecommendationId(),
                entity.getTargetId(),
                entity.getXaiExplanation(),
                entity.getRankedPayload(),
                entity.getCreatedAt());
    }
}
