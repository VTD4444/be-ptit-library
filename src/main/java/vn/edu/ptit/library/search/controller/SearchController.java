package vn.edu.ptit.library.search.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.search.dto.ContentSearchRequest;
import vn.edu.ptit.library.search.dto.ContentSearchResultDto;
import vn.edu.ptit.library.search.dto.DocumentMatchesResponse;
import vn.edu.ptit.library.search.dto.SmartSearchRequest;
import vn.edu.ptit.library.search.dto.SmartSearchResponse;
import vn.edu.ptit.library.search.service.SearchService;

import java.util.List;
import java.util.UUID;

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

    @PostMapping("/smart")
    @Operation(summary = "Tim kiem tai lieu: TF-IDF + cosine, ho tro AND / OR / NOT / \"cum tu\"; loc theo ngon ngu / dang tai lieu / nganh hoc")
    public ApiResponse<SmartSearchResponse> smartSearch(@Valid @RequestBody SmartSearchRequest request) {
        return ApiResponse.success("Tim kiem thong minh thanh cong", searchService.smartSearch(request));
    }

    @GetMapping("/document/{id}/matches")
    @Operation(summary = "Lay vi tri doan van khop (trang, chuong, muc, doan, cau, toa do) trong mot tai lieu de highlight khi doc")
    public ApiResponse<DocumentMatchesResponse> getDocumentMatches(
            @PathVariable("id") UUID documentId,
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ApiResponse.success(searchService.findMatches(documentId, query, limit));
    }
}
