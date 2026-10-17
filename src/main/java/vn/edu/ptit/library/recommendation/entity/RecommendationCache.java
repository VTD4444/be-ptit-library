package vn.edu.ptit.library.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.user.entity.User;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recommendations_cache", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCache implements Serializable {

    @Builder.Default
    @EmbeddedId
    private RecommendationCacheId id = new RecommendationCacheId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("documentId")
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("recModelVersion")
    @JoinColumn(name = "rec_model_version", nullable = false)
    private RecAlgorithmRegistry algorithm;

    @Column(name = "score")
    private Double score;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "reason")
    private String reason;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
