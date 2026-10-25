package vn.edu.ptit.library.circulation.service;

import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.dto.BorrowResponseDto;
import vn.edu.ptit.library.circulation.dto.CirculationHistoryItemDto;
import vn.edu.ptit.library.circulation.dto.FinePaymentResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldCancelResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldRequestDto;
import vn.edu.ptit.library.circulation.dto.HoldResponseDto;
import vn.edu.ptit.library.circulation.dto.RenewRequestDto;
import vn.edu.ptit.library.circulation.dto.RenewResponseDto;
import vn.edu.ptit.library.circulation.dto.ReturnRequestDto;
import vn.edu.ptit.library.circulation.dto.ReturnResponseDto;
import vn.edu.ptit.library.circulation.dto.UserFinesSummaryDto;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

public interface CirculationService {

    BorrowResponseDto borrowBooks(BorrowRequestDto request);

    ReturnResponseDto returnBook(ReturnRequestDto request);

    RenewResponseDto renewLoan(RenewRequestDto request);

    PageResponse<CirculationHistoryItemDto> getMyCirculationHistory(String status, PaginationRequest request);

    HoldResponseDto placeHold(HoldRequestDto request);

    HoldCancelResponseDto cancelHold(Long holdId, String cancelReason);

    UserFinesSummaryDto getMyFines(String status);

    FinePaymentResponseDto payFine(Long fineId, String paymentNote);
}
