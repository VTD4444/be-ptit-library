package vn.edu.ptit.library.catalog.entity;

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
import org.hibernate.annotations.SQLRestriction;
import vn.edu.ptit.library.common.audit.SoftDeletableEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "physical_copies", schema = "public")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhysicalCopy extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "barcode", unique = true)
    private String barcode;

    @Column(name = "call_number")
    private String callNumber;

    @Column(name = "shelf_location")
    private String shelfLocation;

    @Column(name = "status")
    private String status;

    @Column(name = "accession_date")
    private OffsetDateTime accessionDate;

    @Column(name = "last_inventory_date")
    private OffsetDateTime lastInventoryDate;
}
