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
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.common.audit.BaseAuditableEntity;
import vn.edu.ptit.library.user.entity.User;

import java.time.OffsetDateTime;

@Entity
@Table(name = "holds_queue", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldQueue extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "physical_copy_id")
    private PhysicalCopy physicalCopy;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "request_date")
    private OffsetDateTime requestDate;

    @Column(name = "ready_date")
    private OffsetDateTime readyDate;

    @Column(name = "pickup_deadline")
    private OffsetDateTime pickupDeadline;

    @Column(name = "status")
    private String status;

    @Column(name = "cancel_reason")
    private String cancelReason;
}
