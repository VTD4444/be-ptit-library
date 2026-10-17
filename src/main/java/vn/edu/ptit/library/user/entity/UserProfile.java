package vn.edu.ptit.library.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import vn.edu.ptit.library.common.audit.BaseAuditableEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_profiles", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile extends BaseAuditableEntity implements Persistable<UUID> {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_topics", columnDefinition = "jsonb")
    private Map<String, Object> topTopics;

    @Column(name = "preferred_format")
    private String preferredFormat;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_categories")
    private List<String> preferredCategories;

    @Builder.Default
    @Column(name = "personalization_enabled", nullable = false)
    private Boolean personalizationEnabled = true;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew || getCreatedAt() == null;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
