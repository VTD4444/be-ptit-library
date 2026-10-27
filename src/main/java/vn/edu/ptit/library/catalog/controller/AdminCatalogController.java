package vn.edu.ptit.library.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.catalog.dto.AuthorDto;
import vn.edu.ptit.library.catalog.dto.AuthorRequestDto;
import vn.edu.ptit.library.catalog.dto.DocumentRequestDto;
import vn.edu.ptit.library.catalog.dto.PhysicalCopyDto;
import vn.edu.ptit.library.catalog.dto.PhysicalCopyRequestDto;
import vn.edu.ptit.library.catalog.service.AuthorService;
import vn.edu.ptit.library.catalog.service.CatalogService;
import vn.edu.ptit.library.catalog.service.PhysicalCopyService;
import vn.edu.ptit.library.common.dto.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog-admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
@Tag(name = "Admin - Catalog", description = "Quan tri bieu ghi thu muc va ban sach giay")
public class AdminCatalogController {

    private final CatalogService catalogService;
    private final PhysicalCopyService physicalCopyService;
    private final AuthorService authorService;

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Them bieu ghi thu muc goc")
    public ApiResponse<UUID> createDocument(@Valid @RequestBody DocumentRequestDto request) {
        return ApiResponse.created("Them bieu ghi thanh cong", catalogService.createDocument(request));
    }

    @PutMapping("/documents/{id}")
    @Operation(summary = "Cap nhat bieu ghi thu muc goc")
    public ApiResponse<UUID> updateDocument(
            @PathVariable("id") UUID id,
            @Valid @RequestBody DocumentRequestDto request) {
        return ApiResponse.success("Cap nhat bieu ghi thanh cong", catalogService.updateDocument(id, request));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Xoa mem bieu ghi thu muc")
    public ApiResponse<Void> deleteDocument(@PathVariable("id") UUID id) {
        catalogService.deleteDocument(id);
        return ApiResponse.success("Xoa bieu ghi thanh cong", null);
    }

    @GetMapping("/documents/{id}/physical-copies")
    @Operation(summary = "Xem danh sach ban sach giay cua mot dau sach")
    public ApiResponse<List<PhysicalCopyDto>> getPhysicalCopies(@PathVariable("id") UUID id) {
        return ApiResponse.success(physicalCopyService.getCopiesByDocument(id));
    }

    @PostMapping("/physical-copies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Them ban sach giay")
    public ApiResponse<PhysicalCopyDto> createPhysicalCopy(@Valid @RequestBody PhysicalCopyRequestDto request) {
        return ApiResponse.created("Them ban sach giay thanh cong", physicalCopyService.createCopy(request));
    }

    @PutMapping("/physical-copies/{id}")
    @Operation(summary = "Cap nhat ban sach giay")
    public ApiResponse<PhysicalCopyDto> updatePhysicalCopy(
            @PathVariable("id") UUID id,
            @Valid @RequestBody PhysicalCopyRequestDto request) {
        return ApiResponse.success("Cap nhat ban sach giay thanh cong", physicalCopyService.updateCopy(id, request));
    }

    @PostMapping("/authors")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Them tac gia")
    public ApiResponse<AuthorDto> createAuthor(@Valid @RequestBody AuthorRequestDto request) {
        return ApiResponse.created("Them tac gia thanh cong", authorService.createAuthor(request));
    }

    @PutMapping("/authors/{id}")
    @Operation(summary = "Cap nhat tac gia")
    public ApiResponse<AuthorDto> updateAuthor(
            @PathVariable("id") Long id,
            @Valid @RequestBody AuthorRequestDto request) {
        return ApiResponse.success("Cap nhat tac gia thanh cong", authorService.updateAuthor(id, request));
    }

    @DeleteMapping("/authors/{id}")
    @Operation(summary = "Xoa tac gia (chi khi chua gan voi tai lieu nao)")
    public ApiResponse<Void> deleteAuthor(@PathVariable("id") Long id) {
        authorService.deleteAuthor(id);
        return ApiResponse.success("Xoa tac gia thanh cong", null);
    }
}
