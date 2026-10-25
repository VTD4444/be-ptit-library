# PTIT Smart Digital Library — Backend Service

> **Đề tài tốt nghiệp:** Xây dựng nền tảng thư viện số thông minh tích hợp truy vấn nội dung và gợi ý tài liệu cá nhân hóa  
> **Đơn vị thực hiện:** Học viện Công nghệ Bưu chính Viễn thông (PTIT)  
> **Giảng viên hướng dẫn:** PGS. TSKH. Hoàng Đăng Hải  
> **Công nghệ chính:** Java 17, Spring Boot 3.3.5, Hibernate 6, PostgreSQL (Supabase pgvector), JJWT, Springdoc OpenAPI.

---

## 1. Giới Thiệu Dự Án

Hệ thống Backend là nền tảng quản trị và cung cấp toàn bộ REST API cho thư viện số PTIT, hướng tới các mục tiêu trọng tâm:
1. **Quản lý biểu ghi thống nhất:** Đồng bộ kho sách vật lý (số bản, mã vạch, vị trí kệ) và kho tài liệu số (file PDF, phiên bản) trên cùng một biểu ghi thư mục duy nhất.
2. **Truy vấn nội dung thông minh (Semantic Content Search):** Cho phép người dùng tìm kiếm theo đoạn trích/mô tả ngữ nghĩa; hệ thống trả về tài liệu kèm vị trí chính xác (chương, mục, trang) trong file PDF và vị trí kệ của bản giấy còn lại trong kho.
3. **Gợi ý tài liệu cá nhân hóa (Personalized Recommendations):** Tự động gợi ý tài liệu theo ngành học, khóa học, lịch sử mượn, đánh giá và tương đồng vector đặc trưng tài liệu.
4. **Phân tích & Quản lý:** Theo dõi lưu thông (mượn, trả, gia hạn, phạt, hàng chờ đặt trước), phát hiện khoảng trống tài liệu từ các cụm truy vấn rỗng để đề xuất mua sắm bổ sung.

---

## 2. Kiến Trúc & Cấu Trúc Thư Mục

Dự án áp dụng kiến trúc **Package-by-Feature** chia theo các module nghiệp vụ độc lập:

```
src/main/java/vn/edu/ptit/library/
├── common/                  # Base Core: ApiResponse, PageResponse, Exceptions, Enums, Audit Entities
├── config/                  # Cấu hình hệ thống: OpenAPI/Swagger, CORS, Async ThreadPool, RestClient, JPA Auditing
├── security/                # Bảo mật: JWT Filter, TokenProvider, CustomUserDetails, SecurityConfig
├── integration/             # Tích hợp dịch vụ ngoài:
│   ├── storage/             # StorageService lưu trữ & quản lý file PDF cục bộ hoặc cloud
│   └── python/              # PythonWorkerClient gọi sang dịch vụ Python AI/NLP
│
├── user/                    # Quản lý người dùng, bạn đọc, phân quyền và khoa/ngành (Bảng 1, 2, 3)
├── catalog/                 # Quản lý biểu ghi, tác giả, bản giấy, bản số, yêu thích, đánh giá (Bảng 4..11)
├── circulation/             # Nghiệp vụ lưu thông: mượn, trả, gia hạn, phạt và hàng chờ (Bảng 12..16)
├── indexing/                # Hàng đợi tiến trình nền bóc tách PDF và ghi nhận sự kiện (Bảng 17)
├── search/                  # Cấu hình vector, phân đoạn chunk, embedding, tương đồng tài liệu (Bảng 18..21)
├── recommendation/          # Quản lý mô hình gợi ý và bộ nhớ đệm gợi ý cá nhân hóa (Bảng 22, 23)
├── analytics/               # Đề xuất bổ sung tài liệu và thống kê tổng quan thư viện (Bảng 24)
└── notification/            # Hàng đợi thông báo và cronjob quét sách quá hạn nhắc nhở (Bảng 25)
```

Chi tiết quy chuẩn code và bảng tra cứu 25 JPA Entities/Repositories tham khảo tại [ARCHITECTURE.md](file:///D:/PTIT/Do-an-tot-nghiep/Backend/ARCHITECTURE.md).

---

## 3. Yêu Cầu Môi Trường (Prerequisites)

* **Java Development Kit (JDK):** Phiên bản **17** trở lên.
  * Kiểm tra phiên bản Java:
    ```bash
    java -version
    ```
* **Maven:** Đã được tích hợp sẵn qua **Maven Wrapper (`mvnw` / `mvnw.cmd`)**, không bắt buộc phải cài Maven trên máy.

---

## 4. Cấu Hình Biến Môi Trường

Hệ thống đã chuẩn bị sẵn file mẫu [.env.example](file:///D:/PTIT/Do-an-tot-nghiep/Backend/.env.example). Tạo một file `.env` hoặc cấu hình các biến môi trường trực tiếp trong IDE (IntelliJ IDEA, Eclipse, VS Code):

```properties
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# Kết nối cơ sở dữ liệu Supabase PostgreSQL
DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&prepareThreshold=0
DB_USERNAME=postgres.pzommhbckoubtsihscsx
DB_PASSWORD=your_database_password_here

# JWT Authentication
JWT_SECRET=cHRpdC1zbWFydC1saWJyYXJ5LXNlY3JldC1rZXktMjAyNi12ZXJ5LWxvbmctYW5kLXNlY3VyZS1rZXk=
JWT_EXPIRATION_MS=86400000
JWT_REFRESH_EXPIRATION_MS=604800000

# Python AI Worker Service
PYTHON_WORKER_BASE_URL=http://localhost:8000
PYTHON_WORKER_TIMEOUT_SECONDS=30

# Lưu trữ tệp PDF
FILE_UPLOAD_DIR=./uploads/digital-copies
```

---

## 5. Hướng Dẫn Sử Dụng Maven Wrapper (`mvnw`)

> [!TIP]
> - Trên **Windows (PowerShell / Command Prompt)**: Dùng lệnh `.\mvnw.cmd <lệnh>`
> - Trên **Linux / macOS / Git Bash**: Dùng lệnh `./mvnw <lệnh>` (nếu bị lỗi quyền chạy, gõ `chmod +x mvnw`)

### 5.1. Kiểm tra phiên bản Maven Wrapper
```powershell
# Windows
.\mvnw.cmd -version

# Linux / macOS
./mvnw -version
```

### 5.2. Dọn dẹp thư mục build cũ (Clean)
Xóa thư mục `target/` để dọn các class biên dịch cũ:
```powershell
.\mvnw.cmd clean
```

### 5.3. Biên dịch mã nguồn (Compile)
Biên dịch toàn bộ các class mã nguồn trong `src/main/java`:
```powershell
.\mvnw.cmd compile
```

### 5.4. Chạy kiểm thử tự động (Unit Tests)
Chạy các test case trong `src/test/java`:
```powershell
# Chạy test thông thường
.\mvnw.cmd test

# Dọn dẹp sạch sẽ rồi mới chạy test
.\mvnw.cmd clean test
```

### 5.5. Khởi chạy ứng dụng trực tiếp (Run Development Server)
Khởi chạy Backend trực tiếp từ mã nguồn:
```powershell
# Chạy mặc định (profile 'dev')
.\mvnw.cmd spring-boot:run

# Chạy với profile cụ thể (ví dụ: prod)
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod

# Chạy với cổng server khác
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

### 5.6. Đóng gói ứng dụng thành file JAR (Package)
Biên dịch và đóng gói thành file thực thi `.jar` trong thư mục `target/`:
```powershell
# Đóng gói và chạy kiểm thử
.\mvnw.cmd clean package

# Đóng gói nhanh (bỏ qua bước chạy kiểm thử)
.\mvnw.cmd clean package -DskipTests
```

Sau khi đóng gói thành công, bạn có thể chạy file JAR độc lập bằng Java:
```powershell
java -jar target/smart-library-backend-0.0.1-SNAPSHOT.jar
```

### 5.7. Tải lại và cập nhật dependencies (Offline/Force update)
Cập nhật hoặc ép tải lại các thư viện dependencies từ Maven Central:
```powershell
.\mvnw.cmd clean install -U -DskipTests
```

---

## 6. Tài Liệu API & Swagger UI

Sau khi ứng dụng khởi chạy thành công tại cổng mặc định `8080`, bạn có thể truy cập:

* **Swagger UI (Giao diện tương tác & kiểm thử API):**  
  👉 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **OpenAPI Schema (JSON specification):**  
  👉 [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)