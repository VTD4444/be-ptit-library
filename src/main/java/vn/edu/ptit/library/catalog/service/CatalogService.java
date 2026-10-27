package vn.edu.ptit.library.catalog.service;

import vn.edu.ptit.library.catalog.dto.DocumentDetailDto;
import vn.edu.ptit.library.catalog.dto.DocumentFilterRequest;
import vn.edu.ptit.library.catalog.dto.DocumentRequestDto;
import vn.edu.ptit.library.catalog.dto.DocumentSummaryDto;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

public interface CatalogService {

    PageResponse<DocumentSummaryDto> searchDocuments(DocumentFilterRequest filter, PaginationRequest request);

    DocumentDetailDto getDocumentDetails(UUID id);

    UUID createDocument(DocumentRequestDto request);

    UUID updateDocument(UUID id, DocumentRequestDto request);

    void deleteDocument(UUID id);

    Document getDocumentEntityOrThrow(UUID id);
}
