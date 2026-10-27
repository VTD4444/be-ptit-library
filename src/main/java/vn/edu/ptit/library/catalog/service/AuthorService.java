package vn.edu.ptit.library.catalog.service;

import vn.edu.ptit.library.catalog.dto.AuthorDto;
import vn.edu.ptit.library.catalog.dto.AuthorRequestDto;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

public interface AuthorService {

    PageResponse<AuthorDto> searchAuthors(String keyword, PaginationRequest request);

    AuthorDto getAuthor(Long id);

    AuthorDto createAuthor(AuthorRequestDto request);

    AuthorDto updateAuthor(Long id, AuthorRequestDto request);

    void deleteAuthor(Long id);
}
