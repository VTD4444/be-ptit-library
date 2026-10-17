# Kiến Trúc Codebase Backend — Thư Viện Số Thông Minh PTIT

Tài liệu hướng dẫn kiến trúc và quy ước code chuẩn cho dự án **"Xây dựng nền tảng thư viện số thông minh tích hợp truy vấn nội dung và gợi ý tài liệu cá nhân hóa"** (Spring Boot 3.3.5 + Java 17 + Hibernate 6 + Supabase PostgreSQL).

---

## 1. Mô Hình Package-by-Feature

Hệ thống được tổ chức theo từng module nghiệp vụ độc lập:

```
src/main/java/vn/edu/ptit/library/
├── common/                  # Core dung chung: ApiResponse, PageResponse, Exceptions, Enums, Audit
├── config/                  # Cau hinh: OpenAPI, CORS, Async ThreadPool, RestClient, JPA Auditing
├── security/                # Bao mat JWT: Filter, TokenProvider, CustomUserDetails, SecurityConfig
├── integration/             # Tich hop ngoai: StorageService (PDF), PythonWorkerClient (AI/NLP)
│
├── user/                    # Module 1: Nguoi dung, Khoa, Ho so ban doc (Bang 1, 2, 3)
├── catalog/                 # Module 2: Bieu ghi, Tac gia, Ban giay, Ban so, Yeu thich, Danh gia (Bang 4..11)
├── circulation/             # Module 3: Luu thong, Chinh sach muon, Phat, Hang cho dat truoc (Bang 12..16)
├── indexing/                # Module 4: Hang doi tien trinh nen boc tach PDF & event log (Bang 17)
├── search/                  # Module 5: Cau hinh vector, Doan van ban chunk, Embedding, Tuong dong (Bang 18..21)
├── recommendation/          # Module 6: Registry mo hinh goi y, Cache goi y ca nhan hoa (Bang 22, 23)
├── analytics/               # Module 7: De xuat bo sung tu cum query rong, Thong ke kho (Bang 24)
└── notification/            # Module 8: Hang doi tin nhan thong bao, Cronjob nhac qua han (Bang 25)
```

---

## 2. Bảng Tra Cứu 25 Bảng Supabase sang 25 JPA Entities & Repositories

| STT | Bảng trong Supabase | JPA Entity | Spring Data Repository | Module |
|:---:|:---|:---|:---|:---|
| 1 | `departments` | `Department` | `DepartmentRepository` | `user` |
| 2 | `users` | `User` | `UserRepository` | `user` |
| 3 | `user_profiles` | `UserProfile` | `UserProfileRepository` | `user` |
| 4 | `documents` | `Document` | `DocumentRepository` | `catalog` |
| 5 | `authors` | `Author` | `AuthorRepository` | `catalog` |
| 6 | `document_authors` | `DocumentAuthor` (`DocumentAuthorId`) | `DocumentAuthorRepository` | `catalog` |
| 7 | `document_departments` | `DocumentDepartment` (`DocumentDepartmentId`) | `DocumentDepartmentRepository` | `catalog` |
| 8 | `physical_copies` | `PhysicalCopy` | `PhysicalCopyRepository` | `catalog` |
| 9 | `digital_copies` | `DigitalCopy` | `DigitalCopyRepository` | `catalog` |
| 10 | `favorites` | `Favorite` | `FavoriteRepository` | `catalog` |
| 11 | `reviews` | `Review` | `ReviewRepository` | `catalog` |
| 12 | `loan_policies` | `LoanPolicy` | `LoanPolicyRepository` | `circulation` |
| 13 | `circulation` | `Circulation` | `CirculationRepository` | `circulation` |
| 14 | `penalty_types` | `PenaltyType` | `PenaltyTypeRepository` | `circulation` |
| 15 | `circulation_fines` | `CirculationFine` | `CirculationFineRepository` | `circulation` |
| 16 | `holds_queue` | `HoldQueue` | `HoldQueueRepository` | `circulation` |
| 17 | `background_jobs` | `BackgroundJob` | `BackgroundJobRepository` | `indexing` |
| 18 | `search_vector_configs` | `SearchVectorConfig` | `SearchVectorConfigRepository` | `search` |
| 19 | `search_document_chunks` | `SearchDocumentChunk` | `SearchDocumentChunkRepository` | `search` |
| 20 | `document_embeddings` | `DocumentEmbedding` | `DocumentEmbeddingRepository` | `search` |
| 21 | `document_similarities` | `DocumentSimilarity` (`DocumentSimilarityId`) | `DocumentSimilarityRepository` | `search` |
| 22 | `rec_algorithm_registry` | `RecAlgorithmRegistry` | `RecAlgorithmRegistryRepository` | `recommendation` |
| 23 | `recommendations_cache` | `RecommendationCache` (`RecommendationCacheId`) | `RecommendationCacheRepository` | `recommendation` |
| 24 | `acquisition_suggestions` | `AcquisitionSuggestion` | `AcquisitionSuggestionRepository` | `analytics` |
| 25 | `message_queue` | `MessageQueue` | `MessageQueueRepository` | `notification` |

---

## 3. Các Quy Tắc Vàng Khi Code (Coding Conventions)

### A. Chuẩn hóa Phản hồi & Xử lý Ngoại lệ
- Mọi Controller đều trả về `ApiResponse<T>` hoặc `ApiResponse<PageResponse<T>>`.
- Khi cần ném ngoại lệ nghiệp vụ, luôn ném `AppException(ErrorCode.XYZ)` hoặc `AppException(ErrorCode.XYZ, "Thông điệp tùy chỉnh")`. `GlobalExceptionHandler` sẽ tự động bắt và trả về format JSON chuẩn.
- Để lấy thông tin người dùng đang đăng nhập: `SecurityUtils.getCurrentUserIdOrThrow()` hoặc `SecurityUtils.getCurrentUserDetails()`.

### B. Chống N+1 và Phụ thuộc vòng giữa các Module
- **100% các quan hệ `@ManyToOne` và `@OneToOne` phải gắn `fetch = FetchType.LAZY`**.
- **Chỉ sử dụng quan hệ 1 chiều (Unidirectional)**: Không khai báo `@OneToMany` (ví dụ: không thêm `List<Circulation>` trong `User.java`). Khi cần lấy danh sách lượt mượn của user, hãy gọi `circulationRepository.findByUserId(...)`.

### C. Khóa chính UUID và Composite Key
- Các bảng UUID độc lập (`users`, `documents`, `physical_copies`, `digital_copies`, `search_document_chunks`): dùng `@GeneratedValue(strategy = GenerationType.UUID)`.
- Hai bảng `@MapsId` 1-1 (`user_profiles`, `document_embeddings`): `implements Persistable<UUID>` để tránh Hibernate gọi lệnh `merge()` sinh thêm câu `SELECT` thừa khi tạo mới.
- Bốn bảng khóa phức hợp (`DocumentAuthor`, `DocumentDepartment`, `DocumentSimilarity`, `RecommendationCache`): luôn khởi tạo sẵn `new *Id()` tại trường `@EmbeddedId` để tránh lỗi `NullPointerException` lúc Hibernate copy ID.

### D. Truy vấn Vector (pgvector) và tsvector
- Các cột `embedding vector` và `fts_tokens tsvector` **không khai báo trực tiếp trong JPA Entity** để tránh lỗi ép kiểu `PGobject`.
- Truy vấn vector cosine similarity `<=>` hoặc Full-Text Search `@@` được viết bằng `@Query(nativeQuery = true)` trong Repository với cú pháp:
  ```sql
  SELECT c.id, c.document_id, (1 - (c.embedding <=> CAST(:queryVector AS vector))) AS semanticScore
  FROM public.search_document_chunks c
  WHERE c.search_config_id = :configId
  ORDER BY c.embedding <=> CAST(:queryVector AS vector)
  LIMIT :limit
  ```
  *(Dùng `CAST(:queryVector AS vector)` thay vì `::vector` để không bị Spring Data JPA hiểu nhầm là named parameter).*

---

## 4. Tích Hợp Python Worker & Xử Lý Nền
- Khi tải lên tệp PDF bản số (`DigitalCopyService`), file được lưu qua `StorageService` và một bản ghi `BackgroundJob` được đẩy vào DB đồng thời kích hoạt bất đồng bộ (`@Async("taskExecutor")`) sang `PythonWorkerClient`.
- Khi người dùng tìm kiếm, nếu không có kết quả (`resultCount == 0` hoặc `maxScore < threshold`), `SearchService` sẽ tự động ghi một job `LOG_ZERO_RESULT_QUERY` vào `background_jobs` để làm đầu vào cho thuật toán gom cụm đề xuất mua sắm tài liệu (`AcquisitionSuggestion`).
- Mỗi 8:00 AM, `NotificationServiceImpl` chạy cronjob quét sách quá hạn và đẩy thông báo nhắc nhở vào `MessageQueue`.
