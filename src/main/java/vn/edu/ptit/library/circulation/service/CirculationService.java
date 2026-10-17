package vn.edu.ptit.library.circulation.service;

import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.entity.Circulation;
import vn.edu.ptit.library.circulation.entity.HoldQueue;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

public interface CirculationService {

    Circulation borrowBook(BorrowRequestDto request);

    Circulation returnBook(Long circulationId, String returnNote);

    Circulation renewLoan(Long circulationId);

    HoldQueue placeHold(UUID documentId);

    PageResponse<Circulation> getMyLoans(PaginationRequest request);
}
