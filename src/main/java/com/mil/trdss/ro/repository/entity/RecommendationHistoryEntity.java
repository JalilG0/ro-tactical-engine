package com.mil.trdss.ro.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

// recommendation_id already gets a unique index from its unique=true column constraint.
// target_id and created_at are indexed here because RecommendationHistoryQueryService
// queries and sorts by both (paginated "by target" and "most recent" lookups).
@Entity
@Table(
        name = "recommendation_history",
        indexes = {
                @Index(name = "idx_recommendation_history_target_id_created_at", columnList = "target_id, created_at DESC"),
                @Index(name = "idx_recommendation_history_created_at", columnList = "created_at DESC")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recommendation_id", nullable = false, unique = true)
    private String recommendationId;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "xai_explanation", columnDefinition = "TEXT")
    private String xaiExplanation;

    @Convert(converter = JsonNodeConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ranked_payload", columnDefinition = "jsonb")
    private JsonNode rankedPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
