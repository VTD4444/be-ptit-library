package vn.edu.ptit.library.catalog.repository;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.dto.DocumentFilterRequest;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentDepartment;

import java.util.UUID;

public final class DocumentSpecification {

    private DocumentSpecification() {
    }

    public static Specification<Document> filter(DocumentFilterRequest filter) {
        return Specification.where(hasKeyword(filter.getKeyword()))
                .and(hasDocType(filter.getDocType()))
                .and(hasPublicationYear(filter.getPublicationYear()))
                .and(hasAuthor(filter.getAuthorId()))
                .and(hasDepartment(filter.getDepartmentId()));
    }

    public static Specification<Document> hasKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        String pattern = "%" + keyword.trim().toLowerCase() + "%";

        return (root, query, cb) -> {
            Subquery<UUID> authorMatch = query.subquery(UUID.class);
            Root<DocumentAuthor> da = authorMatch.from(DocumentAuthor.class);
            authorMatch.select(da.get("id").get("documentId"))
                    .where(cb.like(cb.lower(da.get("author").get("name")), pattern));

            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("isbn")), pattern),
                    root.get("id").in(authorMatch)
            );
        };
    }

    public static Specification<Document> hasDocType(String docType) {
        if (!StringUtils.hasText(docType)) {
            return null;
        }

        return (root, query, cb) -> cb.equal(cb.upper(root.get("docType")), docType.trim().toUpperCase());
    }

    public static Specification<Document> hasPublicationYear(Integer year) {
        if (year == null) {
            return null;
        }

        return (root, query, cb) -> cb.equal(root.get("publicationYear"), year);
    }

    public static Specification<Document> hasAuthor(Long authorId) {
        if (authorId == null) {
            return null;
        }

        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<DocumentAuthor> da = sub.from(DocumentAuthor.class);
            sub.select(da.get("id").get("documentId"))
                    .where(cb.equal(da.get("id").get("authorId"), authorId));

            return root.get("id").in(sub);
        };
    }

    public static Specification<Document> hasDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }

        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<DocumentDepartment> dd = sub.from(DocumentDepartment.class);
            sub.select(dd.get("id").get("documentId"))
                    .where(cb.equal(dd.get("id").get("departmentId"), departmentId));

            return root.get("id").in(sub);
        };
    }
}
