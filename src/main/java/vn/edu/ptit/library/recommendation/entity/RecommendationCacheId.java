package vn.edu.ptit.library.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RecommendationCacheId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "rec_model_version")
    private String recModelVersion;
}
