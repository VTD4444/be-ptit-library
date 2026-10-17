package vn.edu.ptit.library.search.entity;

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

@Entity
@Table(name = "document_similarities", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSimilarity {

    @Builder.Default
    @EmbeddedId
    private DocumentSimilarityId id = new DocumentSimilarityId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("documentAId")
    @JoinColumn(name = "document_a_id", nullable = false)
    private Document documentA;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("documentBId")
    @JoinColumn(name = "document_b_id", nullable = false)
    private Document documentB;

    @Column(name = "sim_score")
    private Double simScore;

    @Column(name = "source")
    private String source;
}
