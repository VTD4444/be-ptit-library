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
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.user.entity.User;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "circulation", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Circulation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "physical_copy_id", nullable = false)
    private PhysicalCopy physicalCopy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    @Column(name = "borrow_date")
    private OffsetDateTime borrowDate;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "return_date")
    private OffsetDateTime returnDate;

    @Column(name = "borrow_note")
    private String borrowNote;

    @Column(name = "return_note")
    private String returnNote;

    @Builder.Default
    @Column(name = "renewals_count", nullable = false)
    private Integer renewalsCount = 0;
}
