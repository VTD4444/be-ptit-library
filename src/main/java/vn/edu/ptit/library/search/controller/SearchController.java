package vn.edu.ptit.library.search.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.search.dto.ContentSearchRequest;
import vn.edu.ptit.library.search.dto.ContentSearchResultDto;
import vn.edu.ptit.library.search.service.SearchService;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Content Search", description = "Truy van noi dung: Tim kiem vector ngu nghia, vi tri trang/chuong va vi tri sach tren ke")
public class SearchController {

    private final SearchService searchService;

    @PostMapping("/content")
    @Operation(summary = "Truy van noi dung tai lieu bang doan trich hoac mo ta")
    public ApiResponse<List<ContentSearchResultDto>> searchContent(@Valid @RequestBody ContentSearchRequest request) {
        return ApiResponse.success("Tim kiem noi dung thanh cong", searchService.searchContent(request));
    }
}
