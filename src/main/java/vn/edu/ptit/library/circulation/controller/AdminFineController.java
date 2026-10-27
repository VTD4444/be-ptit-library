package vn.edu.ptit.library.circulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.circulation.dto.FinePaymentResponseDto;
import vn.edu.ptit.library.circulation.service.CirculationService;
import vn.edu.ptit.library.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/fines-admin")
@RequiredArgsConstructor
@Tag(name = "Admin Fines", description = "Quan ly va ghi nhan thanh toan phi phat tai quay (Librarian, Admin)")
public class AdminFineController {

    private final CirculationService circulationService;

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Ghi nhan thanh toan phi phat (Librarian, Admin)",
            description = "Cap nhat trang thai khoan phat sang PAID va ghi nhan thoi diem thanh toan."
    )
    public ApiResponse<FinePaymentResponseDto> payFine(
            @PathVariable("id") Long id,
            @RequestParam(value = "note", required = false) String note) {
        return ApiResponse.success("Ghi nhan thanh toan phi phat thanh cong", circulationService.payFine(id, note));
    }
}
