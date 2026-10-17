package vn.edu.ptit.library.search.entity;

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
public class DocumentSimilarityId implements Serializable {

    @Column(name = "document_a_id")
    private UUID documentAId;

    @Column(name = "document_b_id")
    private UUID documentBId;

    @Column(name = "model_version")
    private String modelVersion;
}
