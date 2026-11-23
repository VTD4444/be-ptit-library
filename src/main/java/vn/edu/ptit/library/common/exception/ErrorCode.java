package vn.edu.ptit.library.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- Common & Validation (1000 - 1099) ---
    INTERNAL_SERVER_ERROR(1000, "Lỗi hệ thống nội bộ", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1001, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1002, "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(1003, "Tài nguyên đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    FEATURE_NOT_IMPLEMENTED(1004, "Chức năng đang được phát triển", HttpStatus.NOT_IMPLEMENTED),

    // --- Auth & User (1100 - 1199) ---
    UNAUTHORIZED(1100, "Chưa xác thực hoặc phiên đăng nhập không hợp lệ", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1101, "Không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN),
    USER_NOT_FOUND(1102, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(1103, "Email/Mã người dùng hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    USER_CARD_EXPIRED(1104, "Thẻ thư viện của bạn đọc đã hết hạn", HttpStatus.BAD_REQUEST),

    // --- Catalog & Documents (1200 - 1299) ---
    DOCUMENT_NOT_FOUND(1200, "Không tìm thấy biểu ghi tài liệu", HttpStatus.NOT_FOUND),
    PHYSICAL_COPY_NOT_FOUND(1201, "Không tìm thấy bản sách vật lý", HttpStatus.NOT_FOUND),
    DIGITAL_COPY_NOT_FOUND(1202, "Không tìm thấy bản tài liệu số", HttpStatus.NOT_FOUND),
    AUTHOR_NOT_FOUND(1203, "Không tìm thấy tác giả", HttpStatus.NOT_FOUND),

    // --- Circulation, Holds & Fines (1300 - 1399) ---
    COPY_NOT_AVAILABLE(1300, "Bản sách vật lý hiện không sẵn sàng để mượn", HttpStatus.BAD_REQUEST),
    LOAN_LIMIT_EXCEEDED(1301, "Bạn đọc đã vượt quá số lượng sách được phép mượn", HttpStatus.BAD_REQUEST),
    RENEWAL_LIMIT_EXCEEDED(1302, "Đã đạt giới hạn số lần gia hạn cho lượt mượn này", HttpStatus.BAD_REQUEST),
    HOLD_ALREADY_EXISTS(1303, "Bạn đọc đã đặt trước tài liệu này trong hàng chờ", HttpStatus.CONFLICT),
    LOAN_NOT_FOUND(1304, "Không tìm thấy lượt mượn sách đang hoạt động", HttpStatus.NOT_FOUND),
    ALREADY_RETURNED(1305, "Cuốn sách này đã được ghi nhận trả trước đó", HttpStatus.BAD_REQUEST),
    HOLD_NOT_FOUND(1306, "Không tìm thấy yêu cầu đặt trước sách", HttpStatus.NOT_FOUND),
    HOLD_NOT_ALLOWED_WHEN_AVAILABLE(1307, "Sách hiện vẫn còn bản sẵn sàng trên kệ, bạn đọc có thể mượn trực tiếp tại quầy", HttpStatus.BAD_REQUEST),
    HOLD_CANNOT_CANCEL(1308, "Không thể hủy yêu cầu đặt trước ở trạng thái hiện tại", HttpStatus.BAD_REQUEST),
    FINE_NOT_FOUND(1309, "Không tìm thấy khoản phạt", HttpStatus.NOT_FOUND),
    FINE_ALREADY_PAID(1310, "Khoản phạt này đã được thanh toán hoặc miễn giảm", HttpStatus.BAD_REQUEST),
    CANNOT_RENEW_OVERDUE(1311, "Sách đã quá hạn trả, không thể gia hạn", HttpStatus.BAD_REQUEST),
    CANNOT_RENEW_ON_HOLD(1312, "Tài liệu đang có bạn đọc khác đặt trước trong hàng chờ, không thể gia hạn", HttpStatus.BAD_REQUEST),
    ALREADY_BORROWING_DOCUMENT(1313, "Bạn đọc hiện đang mượn một bản của đầu sách này", HttpStatus.CONFLICT),

    // --- Indexing, Search & AI Worker (1400 - 1499) ---
    PYTHON_WORKER_ERROR(1400, "Lỗi giao tiếp với dịch vụ xử lý AI/NLP Python", HttpStatus.BAD_GATEWAY),
    SEARCH_CONFIG_NOT_FOUND(1401, "Không tìm thấy cấu hình chỉ mục vector đang hoạt động", HttpStatus.NOT_FOUND);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
