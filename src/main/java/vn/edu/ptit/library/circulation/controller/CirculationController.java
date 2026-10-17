package vn.edu.ptit.library.circulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.entity.Circulation;
import vn.edu.ptit.library.circulation.entity.HoldQueue;
import vn.edu.ptit.library.circulation.service.CirculationService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/circulation")
@RequiredArgsConstructor
@Tag(name = "Circulation", description = "Nghiep vu luu thong: Muon, tra, gia han va hang cho dat truoc")
public class CirculationController {

    private final CirculationService circulationService;

    @PostMapping("/borrow")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Lap phieu muon sach vat ly (Can bo / Admin)")
    public ApiResponse<Circulation> borrowBook(@Valid @RequestBody BorrowRequestDto request) {
        return ApiResponse.success("Muon sach thanh cong", circulationService.borrowBook(request));
    }

    @PostMapping("/return/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Ghi nhan tra sach vat ly (Can bo / Admin)")
    public ApiResponse<Circulation> returnBook(
            @PathVariable("id") Long id,
            @RequestParam(value = "note", required = false) String returnNote) {
        return ApiResponse.success("Tra sach thanh cong", circulationService.returnBook(id, returnNote));
    }

    @PostMapping("/renew/{id}")
    @Operation(summary = "Gia han luot muon sach")
    public ApiResponse<Circulation> renewLoan(@PathVariable("id") Long id) {
        return ApiResponse.success("Gia han sach thanh cong", circulationService.renewLoan(id));
    }

    @PostMapping("/holds/{documentId}")
    @Operation(summary = "Dat truoc tai lieu vao hang cho (khi kho het sach san sang)")
    public ApiResponse<HoldQueue> placeHold(@PathVariable("documentId") UUID documentId) {
        return ApiResponse.success("Dat truoc thanh cong, da dua vao hang cho", circulationService.placeHold(documentId));
    }

    @GetMapping("/my-loans")
    @Operation(summary = "Lich su muon sach cua ban doc hien tai")
    public ApiResponse<PageResponse<Circulation>> getMyLoans(@Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(circulationService.getMyLoans(request));
    }
}
