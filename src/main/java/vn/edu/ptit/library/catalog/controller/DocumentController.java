package vn.edu.ptit.library.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.catalog.dto.DocumentDetailDto;
import vn.edu.ptit.library.catalog.dto.DocumentFilterRequest;
import vn.edu.ptit.library.catalog.dto.DocumentSummaryDto;
import vn.edu.ptit.library.catalog.service.CatalogService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Tra cuu bieu ghi thu muc (danh cho tat ca nguoi dung)")
public class DocumentController {

    private final CatalogService catalogService;

    @GetMapping
    @Operation(summary = "Lay danh sach bieu ghi: tu khoa, phan trang, loc theo nganh / loai tai lieu / tac gia / nam XB")
    public ApiResponse<PageResponse<DocumentSummaryDto>> searchDocuments(
            @ModelAttribute DocumentFilterRequest filter,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(catalogService.searchDocuments(filter, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiet mot dau sach")
    public ApiResponse<DocumentDetailDto> getDocumentDetails(@PathVariable("id") UUID id) {
        return ApiResponse.success(catalogService.getDocumentDetails(id));
    }
}
