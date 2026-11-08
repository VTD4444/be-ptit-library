package vn.edu.ptit.library.search.service;

import vn.edu.ptit.library.search.dto.ContentSearchRequest;
import vn.edu.ptit.library.search.dto.ContentSearchResultDto;
import vn.edu.ptit.library.search.dto.DocumentMatchesResponse;
import vn.edu.ptit.library.search.dto.SmartSearchRequest;
import vn.edu.ptit.library.search.dto.SmartSearchResponse;

import java.util.List;
import java.util.UUID;

public interface SearchService {

    List<ContentSearchResultDto> searchContent(ContentSearchRequest request);

    SmartSearchResponse smartSearch(SmartSearchRequest request);

    DocumentMatchesResponse findMatches(UUID documentId, String query, int limit, String searchMode);
}
