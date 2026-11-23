package vn.edu.ptit.library.circulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.circulation.dto.HoldCancelResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldRequestDto;
import vn.edu.ptit.library.circulation.dto.HoldResponseDto;
import vn.edu.ptit.library.circulation.dto.StaffHoldItemDto;
import vn.edu.ptit.library.circulation.service.CirculationService;
import vn.edu.ptit.library.common.dto.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/holds")
@RequiredArgsConstructor
@Tag(name = "Holds", description = "Nghiep vu dat truoc sach (Hold Queue) khi kho het ban giay san sang")
public class HoldController {

    private final CirculationService circulationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('READER', 'LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Dat truoc sach khi het ban giay (Reader)",
            description = "Truyen ID cua dau sach can dat (documentId). "
                    + "Tra ve vi tri trong hang cho va ngay du kien co the nhan sach."
    )
    public ApiResponse<HoldResponseDto> placeHold(@Valid @RequestBody HoldRequestDto request) {
        return ApiResponse.success("Dat truoc sach thanh cong", circulationService.placeHold(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Danh sach dat truoc cho thu thu (Librarian, Admin)",
            description = "status: bo trong/ACTIVE = dang cho + san sang nhan; hoac WAITING, READY_FOR_PICKUP, "
                    + "FULFILLED, EXPIRED, CANCELLED. keyword: nhan de, ma / ten / email ban doc. Toi da 500 dong."
    )
    public ApiResponse<List<StaffHoldItemDto>> searchHolds(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.success(circulationService.searchHoldsForStaff(status, keyword));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('READER', 'LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Huy yeu cau dat truoc sach (Reader, Librarian)",
            description = "Huy luot dat truoc theo ID va tu dong cap nhat lai thu tu hang cho hoac giai phong ban sao dang giu."
    )
    public ApiResponse<HoldCancelResponseDto> cancelHold(
            @PathVariable("id") Long id,
            @RequestParam(value = "reason", required = false) String reason) {
        return ApiResponse.success("Huy yeu cau dat truoc thanh cong", circulationService.cancelHold(id, reason));
    }
}
