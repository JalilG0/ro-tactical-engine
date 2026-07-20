package com.mil.trdss.ro.repository;

import com.mil.trdss.ro.repository.entity.RecommendationHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistoryEntity, UUID> {

    Optional<RecommendationHistoryEntity> findByRecommendationId(String recommendationId);

    Page<RecommendationHistoryEntity> findByTargetId(String targetId, Pageable pageable);
}
