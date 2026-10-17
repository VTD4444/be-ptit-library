package vn.edu.ptit.library.common.enums;

/**
 * Trang thai dat truoc / hang cho sach (holds_queue.status).
 */
public enum HoldStatus {
    WAITING,
    READY_FOR_PICKUP,
    FULFILLED,
    EXPIRED,
    CANCELLED
}
