package vn.edu.ptit.library.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- Common & Validation (1000 - 1099) ---
    INTERNAL_SERVER_ERROR(1000, "Loi he thong noi bo", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1001, "Du lieu dau vao khong hop le", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1002, "Khong tim thay tai nguyen yeu cau", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(1003, "Tai nguyen da ton tai trong he thong", HttpStatus.CONFLICT),
    FEATURE_NOT_IMPLEMENTED(1004, "Chuc nang dang duoc phat trien", HttpStatus.NOT_IMPLEMENTED),

    // --- Auth & User (1100 - 1199) ---
    UNAUTHORIZED(1100, "Chua xac thuc hoac token khong hop le", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1101, "Khong co quyen truy cap tai nguyen nay", HttpStatus.FORBIDDEN),
    USER_NOT_FOUND(1102, "Khong tim thay nguoi dung", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(1103, "Email/Ma nguoi dung hoac mat khau khong dung", HttpStatus.UNAUTHORIZED),
    USER_CARD_EXPIRED(1104, "The thu vien cua ban doc da het han", HttpStatus.BAD_REQUEST),

    // --- Catalog & Documents (1200 - 1299) ---
    DOCUMENT_NOT_FOUND(1200, "Khong tim thay bieu ghi tai lieu", HttpStatus.NOT_FOUND),
    PHYSICAL_COPY_NOT_FOUND(1201, "Khong tim thay ban sach vat ly", HttpStatus.NOT_FOUND),
    DIGITAL_COPY_NOT_FOUND(1202, "Khong tim thay ban tai lieu so", HttpStatus.NOT_FOUND),

    // --- Circulation, Holds & Fines (1300 - 1399) ---
    COPY_NOT_AVAILABLE(1300, "Ban sach vat ly hien khong san sang de muon", HttpStatus.BAD_REQUEST),
    LOAN_LIMIT_EXCEEDED(1301, "Ban doc da vuot qua so luong sach duoc phep muon", HttpStatus.BAD_REQUEST),
    RENEWAL_LIMIT_EXCEEDED(1302, "Da dat gioi han so lan gia han cho luot muon nay", HttpStatus.BAD_REQUEST),
    HOLD_ALREADY_EXISTS(1303, "Ban doc da dat truoc tai lieu nay trong hang cho", HttpStatus.CONFLICT),
    LOAN_NOT_FOUND(1304, "Khong tim thay luot muon sach dang hoat dong", HttpStatus.NOT_FOUND),
    ALREADY_RETURNED(1305, "Cuon sach nay da duoc ghi nhan tra truoc do", HttpStatus.BAD_REQUEST),
    HOLD_NOT_FOUND(1306, "Khong tim thay yeu cau dat truoc sach", HttpStatus.NOT_FOUND),
    HOLD_NOT_ALLOWED_WHEN_AVAILABLE(1307, "Sach hien van con ban san sang tren ke, ban doc co the muon truc tiep", HttpStatus.BAD_REQUEST),
    HOLD_CANNOT_CANCEL(1308, "Khong the huy yeu cau dat truoc o trang thai hien tai", HttpStatus.BAD_REQUEST),
    FINE_NOT_FOUND(1309, "Khong tim thay khoan phat", HttpStatus.NOT_FOUND),
    FINE_ALREADY_PAID(1310, "Khoan phat nay da duoc thanh toan hoac mien giam", HttpStatus.BAD_REQUEST),
    CANNOT_RENEW_OVERDUE(1311, "Sach da qua han tra, khong the gia han", HttpStatus.BAD_REQUEST),
    CANNOT_RENEW_ON_HOLD(1312, "Tai lieu dang co ban doc khac dat truoc trong hang cho, khong the gia han", HttpStatus.BAD_REQUEST),
    ALREADY_BORROWING_DOCUMENT(1313, "Ban doc hien dang muon mot ban cua dau sach nay", HttpStatus.CONFLICT),

    // --- Indexing, Search & AI Worker (1400 - 1499) ---
    PYTHON_WORKER_ERROR(1400, "Loi giao tiep voi dich vu xu ly AI/NLP Python", HttpStatus.BAD_GATEWAY),
    SEARCH_CONFIG_NOT_FOUND(1401, "Khong tim thay cau hinh chi muc vector dang hoat dong", HttpStatus.NOT_FOUND);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
