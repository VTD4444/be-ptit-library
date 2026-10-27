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
import vn.edu.ptit.library.catalog.dto.AuthorDto;
import vn.edu.ptit.library.catalog.service.AuthorService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

@RestController
@RequestMapping("/api/catalog/authors")
@RequiredArgsConstructor
@Tag(name = "Authors", description = "Tra cuu tac gia")
public class AuthorController {

    private final AuthorService authorService;

    @GetMapping
    @Operation(summary = "Lay danh sach tac gia, tim theo ten")
    public ApiResponse<PageResponse<AuthorDto>> searchAuthors(
            @RequestParam(value = "keyword", required = false) String keyword,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(authorService.searchAuthors(keyword, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiet tac gia")
    public ApiResponse<AuthorDto> getAuthor(@PathVariable("id") Long id) {
        return ApiResponse.success(authorService.getAuthor(id));
    }
}
