package com.mil.trdss.ro.service;

import com.mil.trdss.ro.controller.RecommendationNotFoundException;
import com.mil.trdss.ro.domain.dto.RecommendationHistoryDTO;
import com.mil.trdss.ro.repository.RecommendationHistoryRepository;
import com.mil.trdss.ro.repository.entity.RecommendationHistoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationHistoryQueryServiceTest {

    @Mock
    private RecommendationHistoryRepository recommendationHistoryRepository;

    private RecommendationHistoryQueryService queryService;

    @Test
    void returnsDtoWhenRecommendationExists() {
        queryService = new RecommendationHistoryQueryService(recommendationHistoryRepository);
        RecommendationHistoryEntity entity = RecommendationHistoryEntity.builder()
                .recommendationId("rec-1")
                .targetId("target-1")
                .xaiExplanation("explanation")
                .createdAt(Instant.now())
                .build();
        when(recommendationHistoryRepository.findByRecommendationId("rec-1")).thenReturn(Optional.of(entity));

        RecommendationHistoryDTO result = queryService.getByRecommendationId("rec-1");

        assertThat(result.recommendationId()).isEqualTo("rec-1");
        assertThat(result.targetId()).isEqualTo("target-1");
    }

    @Test
    void throwsNotFoundWhenRecommendationMissing() {
        queryService = new RecommendationHistoryQueryService(recommendationHistoryRepository);
        when(recommendationHistoryRepository.findByRecommendationId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getByRecommendationId("missing"))
                .isInstanceOf(RecommendationNotFoundException.class);
    }

    @Test
    void listsAllWhenNoTargetIdGiven() {
        queryService = new RecommendationHistoryQueryService(recommendationHistoryRepository);
        Pageable pageable = PageRequest.of(0, 20);
        RecommendationHistoryEntity entity = RecommendationHistoryEntity.builder()
                .recommendationId("rec-1")
                .targetId("target-1")
                .createdAt(Instant.now())
                .build();
        Page<RecommendationHistoryEntity> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(recommendationHistoryRepository.findAll(pageable)).thenReturn(page);

        Page<RecommendationHistoryDTO> result = queryService.list(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).recommendationId()).isEqualTo("rec-1");
    }

    @Test
    void listsByTargetIdWhenGiven() {
        queryService = new RecommendationHistoryQueryService(recommendationHistoryRepository);
        Pageable pageable = PageRequest.of(0, 20);
        RecommendationHistoryEntity entity = RecommendationHistoryEntity.builder()
                .recommendationId("rec-2")
                .targetId("target-2")
                .createdAt(Instant.now())
                .build();
        Page<RecommendationHistoryEntity> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(recommendationHistoryRepository.findByTargetId("target-2", pageable)).thenReturn(page);

        Page<RecommendationHistoryDTO> result = queryService.list("target-2", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).targetId()).isEqualTo("target-2");
    }
}
