package vn.edu.ptit.library.circulation.entity;

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
import vn.edu.ptit.library.common.audit.BaseAuditableEntity;

@Entity
@Table(name = "circulation_fines", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CirculationFine extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circulation_id", nullable = false)
    private Circulation circulation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "penalty_type_id", nullable = false)
    private PenaltyType penaltyType;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "fine_amount")
    private Long fineAmount;

    @Column(name = "status")
    private String status;

    @Column(name = "note")
    private String note;
}
