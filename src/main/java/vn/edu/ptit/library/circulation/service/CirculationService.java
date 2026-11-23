package vn.edu.ptit.library.circulation.service;

import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.dto.BorrowResponseDto;
import vn.edu.ptit.library.circulation.dto.CirculationHistoryItemDto;
import vn.edu.ptit.library.circulation.dto.CopyLookupDto;
import vn.edu.ptit.library.circulation.dto.StaffHoldItemDto;
import vn.edu.ptit.library.circulation.dto.FinePaymentResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldCancelResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldRequestDto;
import vn.edu.ptit.library.circulation.dto.HoldResponseDto;
import vn.edu.ptit.library.circulation.dto.MyHoldItemDto;
import vn.edu.ptit.library.circulation.dto.RenewRequestDto;
import vn.edu.ptit.library.circulation.dto.RenewResponseDto;
import vn.edu.ptit.library.circulation.dto.ReturnRequestDto;
import vn.edu.ptit.library.circulation.dto.ReturnResponseDto;
import vn.edu.ptit.library.circulation.dto.UserFinesSummaryDto;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.List;

public interface CirculationService {

    BorrowResponseDto borrowBooks(BorrowRequestDto request);

    ReturnResponseDto returnBook(ReturnRequestDto request);

    RenewResponseDto renewLoan(RenewRequestDto request);

    PageResponse<CirculationHistoryItemDto> getMyCirculationHistory(String status, PaginationRequest request);

    HoldResponseDto placeHold(HoldRequestDto request);

    HoldCancelResponseDto cancelHold(Long holdId, String cancelReason);

    /**
     * Danh sach dat truoc cua ban doc dang dang nhap.
     * status: bo trong = tat ca; ACTIVE = WAITING + READY_FOR_PICKUP; hoac mot HoldStatus cu the.
     */
    List<MyHoldItemDto> getMyHolds(String status);

    /**
     * Chuyen cac luot READY_FOR_PICKUP da qua han nhan sang EXPIRED va
     * nhuong ban sach cho nguoi ke tiep trong hang cho (hoac tra ve AVAILABLE).
     * @return so luot da het han
     */
    int expireOverduePickups();

    /**
     * Danh sach dat truoc cho thu thu.
     * status: bo trong/ACTIVE = WAITING + READY_FOR_PICKUP; hoac mot HoldStatus cu the.
     * keyword: tim theo nhan de, ma / ten / email ban doc.
     */
    List<StaffHoldItemDto> searchHoldsForStaff(String status, String keyword);

    /** Tra cuu ban sach theo ma vach: thong tin tai lieu, luot muon dang mo, luot dat truoc dang giu. */
    CopyLookupDto lookupCopy(String barcode);

    UserFinesSummaryDto getMyFines(String status);

    FinePaymentResponseDto payFine(Long fineId, String paymentNote);
}
