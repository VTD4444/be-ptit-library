package vn.edu.ptit.library.search.service;

import vn.edu.ptit.library.search.dto.ContentSearchRequest;
import vn.edu.ptit.library.search.dto.ContentSearchResultDto;

import java.util.List;

public interface SearchService {

    List<ContentSearchResultDto> searchContent(ContentSearchRequest request);
}
