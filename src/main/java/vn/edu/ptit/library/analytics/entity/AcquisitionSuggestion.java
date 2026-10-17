package vn.edu.ptit.library.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.library.common.audit.BaseAuditableEntity;
import vn.edu.ptit.library.user.entity.Department;

import java.util.Map;

@Entity
@Table(name = "acquisition_suggestions", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquisitionSuggestion extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "topic")
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_query_cluster", columnDefinition = "jsonb")
    private Map<String, Object> sourceQueryCluster;

    @Column(name = "query_count")
    private Integer queryCount;

    @Column(name = "status")
    private String status;
}
