package vn.edu.ptit.library.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;

/**
 * Lop co so cho cac bang co day du created_at va updated_at.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAuditableEntity extends BaseCreatedEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onPrePersistAuditable() {
        OffsetDateTime now = OffsetDateTime.now();
        if (getCreatedAt() == null) {
            setCreatedAt(now);
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onPreUpdateAuditable() {
        this.updatedAt = OffsetDateTime.now();
    }
}
