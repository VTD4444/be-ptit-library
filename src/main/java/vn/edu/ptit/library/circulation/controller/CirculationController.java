package vn.edu.ptit.library.circulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.dto.BorrowResponseDto;
import vn.edu.ptit.library.circulation.dto.RenewRequestDto;
import vn.edu.ptit.library.circulation.dto.RenewResponseDto;
import vn.edu.ptit.library.circulation.dto.ReturnRequestDto;
import vn.edu.ptit.library.circulation.dto.ReturnResponseDto;
import vn.edu.ptit.library.circulation.service.CirculationService;
import vn.edu.ptit.library.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/circulation")
@RequiredArgsConstructor
@Tag(name = "Circulation", description = "Nghiep vu luu thong tai quay: Muon sach, Tra sach, Gia han")
public class CirculationController {

    private final CirculationService circulationService;

    @PostMapping("/borrow")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Cho muon sach tai quay (Librarian, Admin)",
            description = "Truyen ma ban doc (userCode) va danh sach ma vach sach (barcodes). "
                    + "Tra ve trang thai muon, han tra cho tung cuon va canh bao neu muon qua so luong quy dinh."
    )
    public ApiResponse<BorrowResponseDto> borrowBooks(@Valid @RequestBody BorrowRequestDto request) {
        return ApiResponse.success("Lap phieu muon sach thanh cong", circulationService.borrowBooks(request));
    }

    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Nhan tra sach tai quay (Librarian, Admin)",
            description = "Truyen ma vach cuon sach (barcode) va tinh trang sach luc tra (NORMAL, DAMAGED, LOST). "
                    + "Tra ve so ngay qua han, phi phat phat sinh va trang thai sach duoc cap nhat."
    )
    public ApiResponse<ReturnResponseDto> returnBook(@Valid @RequestBody ReturnRequestDto request) {
        return ApiResponse.success("Ghi nhan tra sach thanh cong", circulationService.returnBook(request));
    }

    @PostMapping("/renew")
    @PreAuthorize("hasAnyRole('READER', 'LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Gia han tai lieu dang muon (Reader, Librarian, Admin)",
            description = "Truyen ma vach cua sach (barcode) hoac ID luot muon (circulationId). "
                    + "Tra ve han tra moi va trang thai gia han (SUCCESS hoac EXCEEDED_MAX_RENEWALS)."
    )
    public ApiResponse<RenewResponseDto> renewLoan(@Valid @RequestBody RenewRequestDto request) {
        RenewResponseDto response = circulationService.renewLoan(request);
        return ApiResponse.success(response.getMessage(), response);
    }
}
