package vn.edu.ptit.library.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "rec_algorithm_registry", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecAlgorithmRegistry implements Serializable {

    @Id
    @Column(name = "rec_model_version")
    private String recModelVersion;

    @Column(name = "algorithm_type")
    private String algorithmType;

    @Column(name = "is_active")
    private Boolean isActive;
}
