package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.Circulation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CirculationRepository extends JpaRepository<Circulation, Long> {

    List<Circulation> findByUserIdAndReturnDateIsNull(UUID userId);

    long countByUserIdAndReturnDateIsNull(UUID userId);

    @EntityGraph(attributePaths = {"physicalCopy", "physicalCopy.document"})
    Page<Circulation> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"physicalCopy", "physicalCopy.document"})
    Page<Circulation> findByUserIdAndReturnDateIsNull(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"physicalCopy", "physicalCopy.document"})
    Page<Circulation> findByUserIdAndReturnDateIsNotNull(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"physicalCopy", "physicalCopy.document"})
    @Query("SELECT c FROM Circulation c WHERE c.user.id = :userId AND c.returnDate IS NULL AND c.dueDate < :now")
    Page<Circulation> findOverdueByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now, Pageable pageable);

    Optional<Circulation> findByPhysicalCopyIdAndReturnDateIsNull(UUID physicalCopyId);

    @Query("SELECT c FROM Circulation c WHERE c.physicalCopy.barcode = :barcode AND c.returnDate IS NULL")
    Optional<Circulation> findActiveLoanByBarcode(@Param("barcode") String barcode);

    @Query("SELECT c FROM Circulation c WHERE c.physicalCopy.document.id = :documentId AND c.returnDate IS NULL ORDER BY c.dueDate ASC")
    List<Circulation> findActiveLoansByDocumentIdOrderByDueDateAsc(@Param("documentId") UUID documentId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Circulation c WHERE c.user.id = :userId AND c.physicalCopy.document.id = :documentId AND c.returnDate IS NULL")
    boolean isUserCurrentlyBorrowingDocument(@Param("userId") UUID userId, @Param("documentId") UUID documentId);

    @Query("SELECT c FROM Circulation c WHERE c.returnDate IS NULL AND c.dueDate < :now")
    List<Circulation> findOverdueLoans(@Param("now") OffsetDateTime now);

    long countByReturnDateIsNull();

    @Query("SELECT COUNT(c) FROM Circulation c WHERE c.returnDate IS NULL AND c.dueDate < :threshold")
    long countOverdueLoans(@Param("threshold") OffsetDateTime threshold);

    @Query(value = """
            SELECT
                COUNT(c.id) AS total_borrows,
                COUNT(c.id) FILTER (WHERE c.return_date IS NOT NULL) AS total_returns,
                COUNT(c.id) FILTER (WHERE (c.return_date IS NULL AND c.due_date < :now) OR (c.return_date IS NOT NULL AND c.return_date > c.due_date)) AS overdue_in_period
            FROM public.circulation c
            JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
            JOIN public.documents d ON d.id = pc.document_id
            JOIN public.users u ON u.id = c.user_id
            WHERE (CAST(:startDate AS timestamptz) IS NULL OR c.borrow_date >= CAST(:startDate AS timestamptz))
              AND (CAST(:endDate AS timestamptz) IS NULL OR c.borrow_date <= CAST(:endDate AS timestamptz))
              AND (CAST(:departmentId AS bigint) IS NULL OR u.department_id = CAST(:departmentId AS bigint))
              AND (CAST(:docType AS varchar) IS NULL OR LOWER(d.doc_type) = LOWER(CAST(:docType AS varchar)))
            """, nativeQuery = true)
    List<Object[]> findPeriodSummary(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("departmentId") Long departmentId,
            @Param("docType") String docType,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            SELECT
                TO_CHAR(c.borrow_date AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS bucket,
                COUNT(c.id) AS borrow_count,
                COUNT(c.id) FILTER (WHERE c.return_date IS NOT NULL) AS return_count,
                COUNT(c.id) FILTER (WHERE (c.return_date IS NULL AND c.due_date < :now) OR (c.return_date IS NOT NULL AND c.return_date > c.due_date)) AS overdue_count
            FROM public.circulation c
            JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
            JOIN public.documents d ON d.id = pc.document_id
            JOIN public.users u ON u.id = c.user_id
            WHERE (CAST(:startDate AS timestamptz) IS NULL OR c.borrow_date >= CAST(:startDate AS timestamptz))
              AND (CAST(:endDate AS timestamptz) IS NULL OR c.borrow_date <= CAST(:endDate AS timestamptz))
              AND (CAST(:departmentId AS bigint) IS NULL OR u.department_id = CAST(:departmentId AS bigint))
              AND (CAST(:docType AS varchar) IS NULL OR LOWER(d.doc_type) = LOWER(CAST(:docType AS varchar)))
            GROUP BY bucket
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> findCirculationTrendByDay(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("departmentId") Long departmentId,
            @Param("docType") String docType,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            SELECT
                TO_CHAR(c.borrow_date AT TIME ZONE 'UTC', 'YYYY-MM') AS bucket,
                COUNT(c.id) AS borrow_count,
                COUNT(c.id) FILTER (WHERE c.return_date IS NOT NULL) AS return_count,
                COUNT(c.id) FILTER (WHERE (c.return_date IS NULL AND c.due_date < :now) OR (c.return_date IS NOT NULL AND c.return_date > c.due_date)) AS overdue_count
            FROM public.circulation c
            JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
            JOIN public.documents d ON d.id = pc.document_id
            JOIN public.users u ON u.id = c.user_id
            WHERE (CAST(:startDate AS timestamptz) IS NULL OR c.borrow_date >= CAST(:startDate AS timestamptz))
              AND (CAST(:endDate AS timestamptz) IS NULL OR c.borrow_date <= CAST(:endDate AS timestamptz))
              AND (CAST(:departmentId AS bigint) IS NULL OR u.department_id = CAST(:departmentId AS bigint))
              AND (CAST(:docType AS varchar) IS NULL OR LOWER(d.doc_type) = LOWER(CAST(:docType AS varchar)))
            GROUP BY bucket
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> findCirculationTrendByMonth(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("departmentId") Long departmentId,
            @Param("docType") String docType,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            SELECT
                COALESCE(CAST(dept.id AS varchar), 'OTHER') AS dept_key,
                COALESCE(dept.name, 'Chưa phân khoa') AS dept_name,
                COUNT(c.id) AS borrow_count
            FROM public.circulation c
            JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
            JOIN public.documents d ON d.id = pc.document_id
            JOIN public.users u ON u.id = c.user_id
            LEFT JOIN public.departments dept ON dept.id = u.department_id
            WHERE (CAST(:startDate AS timestamptz) IS NULL OR c.borrow_date >= CAST(:startDate AS timestamptz))
              AND (CAST(:endDate AS timestamptz) IS NULL OR c.borrow_date <= CAST(:endDate AS timestamptz))
              AND (CAST(:departmentId AS bigint) IS NULL OR u.department_id = CAST(:departmentId AS bigint))
              AND (CAST(:docType AS varchar) IS NULL OR LOWER(d.doc_type) = LOWER(CAST(:docType AS varchar)))
            GROUP BY dept.id, dept.name
            ORDER BY borrow_count DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> findBorrowsByDepartment(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("departmentId") Long departmentId,
            @Param("docType") String docType
    );

    @Query(value = """
            SELECT
                COALESCE(NULLIF(TRIM(d.doc_type), ''), 'other') AS doc_type_key,
                COUNT(c.id) AS borrow_count
            FROM public.circulation c
            JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
            JOIN public.documents d ON d.id = pc.document_id
            JOIN public.users u ON u.id = c.user_id
            WHERE (CAST(:startDate AS timestamptz) IS NULL OR c.borrow_date >= CAST(:startDate AS timestamptz))
              AND (CAST(:endDate AS timestamptz) IS NULL OR c.borrow_date <= CAST(:endDate AS timestamptz))
              AND (CAST(:departmentId AS bigint) IS NULL OR u.department_id = CAST(:departmentId AS bigint))
              AND (CAST(:docType AS varchar) IS NULL OR LOWER(d.doc_type) = LOWER(CAST(:docType AS varchar)))
            GROUP BY doc_type_key
            ORDER BY borrow_count DESC
            """, nativeQuery = true)
    List<Object[]> findBorrowsByDocType(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("departmentId") Long departmentId,
            @Param("docType") String docType
    );

    @Query(value = """
            WITH borrow_stats AS (
                SELECT pc.document_id, COUNT(c.id) AS borrow_count
                FROM public.circulation c
                JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
                WHERE (CAST(:startDate AS timestamptz) IS NULL OR c.borrow_date >= CAST(:startDate AS timestamptz))
                  AND (CAST(:endDate AS timestamptz) IS NULL OR c.borrow_date <= CAST(:endDate AS timestamptz))
                GROUP BY pc.document_id
            ),
            fav_stats AS (
                SELECT f.document_id, COUNT(f.id) AS favorite_count
                FROM public.favorites f
                WHERE (CAST(:startDate AS timestamptz) IS NULL OR f.created_at >= CAST(:startDate AS timestamptz))
                  AND (CAST(:endDate AS timestamptz) IS NULL OR f.created_at <= CAST(:endDate AS timestamptz))
                GROUP BY f.document_id
            ),
            review_stats AS (
                SELECT
                    r.document_id,
                    COUNT(r.id) FILTER (
                        WHERE (CAST(:startDate AS timestamptz) IS NULL OR r.created_at >= CAST(:startDate AS timestamptz))
                          AND (CAST(:endDate AS timestamptz) IS NULL OR r.created_at <= CAST(:endDate AS timestamptz))
                    ) AS period_review_count,
                    AVG(r.rating) AS avg_rating
                FROM public.reviews r
                GROUP BY r.document_id
            )
            SELECT
                d.id,
                d.title,
                d.doc_type,
                d.publication_year,
                COALESCE(NULLIF(TRIM(d.cover_url), ''), d.metadata->>'coverUrl') AS cover_url,
                COALESCE(b.borrow_count, 0) AS borrow_count,
                COALESCE(fv.favorite_count, 0) AS favorite_count,
                COALESCE(rv.period_review_count, 0) AS review_count,
                rv.avg_rating,
                (COALESCE(b.borrow_count, 0) + COALESCE(fv.favorite_count, 0) + COALESCE(rv.period_review_count, 0)) AS interaction_count
            FROM public.documents d
            LEFT JOIN borrow_stats b ON b.document_id = d.id
            LEFT JOIN fav_stats fv ON fv.document_id = d.id
            LEFT JOIN review_stats rv ON rv.document_id = d.id
            WHERE d.deleted_at IS NULL
              AND (CAST(:docType AS varchar) IS NULL OR LOWER(d.doc_type) = LOWER(CAST(:docType AS varchar)))
              AND (CAST(:departmentId AS bigint) IS NULL OR EXISTS (
                  SELECT 1 FROM public.document_departments dd
                  WHERE dd.document_id = d.id AND dd.department_id = CAST(:departmentId AS bigint)
              ))
              AND (COALESCE(b.borrow_count, 0) + COALESCE(fv.favorite_count, 0) + COALESCE(rv.period_review_count, 0)) > 0
            ORDER BY
                CASE WHEN :sortBy = 'INTERACTIONS'
                     THEN (COALESCE(b.borrow_count, 0) + COALESCE(fv.favorite_count, 0) + COALESCE(rv.period_review_count, 0))
                     ELSE COALESCE(b.borrow_count, 0)
                END DESC,
                (COALESCE(b.borrow_count, 0) + COALESCE(fv.favorite_count, 0) + COALESCE(rv.period_review_count, 0)) DESC,
                COALESCE(rv.avg_rating, 0) DESC,
                d.title ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findPopularDocuments(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("departmentId") Long departmentId,
            @Param("docType") String docType,
            @Param("sortBy") String sortBy,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT
                d.id,
                d.title,
                d.doc_type,
                COUNT(c.id) FILTER (WHERE c.borrow_date >= :sinceDate) AS recent_borrows,
                MAX(c.borrow_date) AS last_borrowed_at
            FROM public.documents d
            LEFT JOIN public.physical_copies pc ON pc.document_id = d.id AND pc.deleted_at IS NULL
            LEFT JOIN public.circulation c ON c.physical_copy_id = pc.id
            WHERE d.deleted_at IS NULL
              AND (CAST(:docType AS varchar) IS NULL OR LOWER(d.doc_type) = LOWER(CAST(:docType AS varchar)))
              AND (CAST(:departmentId AS bigint) IS NULL OR EXISTS (
                  SELECT 1 FROM public.document_departments dd
                  WHERE dd.document_id = d.id AND dd.department_id = CAST(:departmentId AS bigint)
              ))
            GROUP BY d.id, d.title, d.doc_type, d.created_at
            ORDER BY recent_borrows ASC, last_borrowed_at ASC NULLS FIRST, d.created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findLowCirculationDocuments(
            @Param("sinceDate") OffsetDateTime sinceDate,
            @Param("departmentId") Long departmentId,
            @Param("docType") String docType,
            @Param("limit") int limit
    );
}
