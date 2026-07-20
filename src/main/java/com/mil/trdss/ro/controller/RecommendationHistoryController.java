package com.mil.trdss.ro.controller;

import com.mil.trdss.ro.domain.dto.RecommendationHistoryDTO;
import com.mil.trdss.ro.service.RecommendationHistoryQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations/history")
public class RecommendationHistoryController {

    private final RecommendationHistoryQueryService recommendationHistoryQueryService;

    public RecommendationHistoryController(RecommendationHistoryQueryService recommendationHistoryQueryService) {
        this.recommendationHistoryQueryService = recommendationHistoryQueryService;
    }

    @GetMapping("/{recommendationId}")
    public ResponseEntity<RecommendationHistoryDTO> getByRecommendationId(@PathVariable String recommendationId) {
        return ResponseEntity.ok(recommendationHistoryQueryService.getByRecommendationId(recommendationId));
    }

    @GetMapping
    public ResponseEntity<PagedModel<RecommendationHistoryDTO>> list(
            @RequestParam(required = false) String targetId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(recommendationHistoryQueryService.list(targetId, pageable)));
    }
}
