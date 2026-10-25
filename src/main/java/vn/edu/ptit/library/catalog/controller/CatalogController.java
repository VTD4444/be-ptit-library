package vn.edu.ptit.library.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.catalog.dto.DocumentSummaryDto;
import vn.edu.ptit.library.catalog.service.CatalogService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Quan ly danh muc tai lieu, tac gia va ban sach vat ly")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/documents/{id}")
    @Operation(summary = "Xem chi tiet bieu ghi tai lieu va so ban con lai tren ke")
    public ApiResponse<DocumentSummaryDto> getDocumentDetails(@PathVariable("id") UUID id) {
        return ApiResponse.success(catalogService.getDocumentDetails(id));
    }

    @GetMapping("/documents")
    @Operation(summary = "Tra cuu danh sach tai lieu co phan trang")
    public ApiResponse<PageResponse<DocumentSummaryDto>> searchDocuments(
            @RequestParam(value = "keyword", required = false) String keyword,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(catalogService.searchDocuments(keyword, request));
    }
}
