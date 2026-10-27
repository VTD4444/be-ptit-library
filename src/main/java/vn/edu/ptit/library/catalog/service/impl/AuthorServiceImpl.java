package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.dto.AuthorDto;
import vn.edu.ptit.library.catalog.dto.AuthorRequestDto;
import vn.edu.ptit.library.catalog.entity.Author;
import vn.edu.ptit.library.catalog.repository.AuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.service.AuthorService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authorRepository;
    private final DocumentAuthorRepository documentAuthorRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuthorDto> searchAuthors(String keyword, PaginationRequest request) {
        Page<Author> page = StringUtils.hasText(keyword)
                ? authorRepository.findByNameContainingIgnoreCase(keyword.trim(), request.toPageable())
                : authorRepository.findAll(request.toPageable());

        return PageResponse.from(page, this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorDto getAuthor(Long id) {
        return toDto(getAuthorOrThrow(id));
    }

    @Override
    @Transactional
    public AuthorDto createAuthor(AuthorRequestDto request) {
        String name = normalizeName(request.getName());

        if (authorRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Tác giả đã tồn tại: " + name);
        }

        Author author = authorRepository.save(Author.builder().name(name).build());
        return toDto(author);
    }

    @Override
    @Transactional
    public AuthorDto updateAuthor(Long id, AuthorRequestDto request) {
        Author author = getAuthorOrThrow(id);
        String name = normalizeName(request.getName());

        if (authorRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Tác giả đã tồn tại: " + name);
        }

        author.setName(name);
        return toDto(authorRepository.save(author));
    }

    @Override
    @Transactional
    public void deleteAuthor(Long id) {
        Author author = getAuthorOrThrow(id);

        if (documentAuthorRepository.countByAuthorId(id) > 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tác giả đang gắn với tài liệu, không thể xóa");
        }

        authorRepository.delete(author);
    }

    private Author getAuthorOrThrow(Long id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AUTHOR_NOT_FOUND));
    }

    private String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private AuthorDto toDto(Author author) {
        return AuthorDto.builder()
                .id(author.getId())
                .name(author.getName())
                .documentCount(documentAuthorRepository.countByAuthorId(author.getId()))
                .createdAt(author.getCreatedAt())
                .updatedAt(author.getUpdatedAt())
                .build();
    }
}
