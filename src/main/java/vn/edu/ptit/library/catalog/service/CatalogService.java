package vn.edu.ptit.library.catalog.service;

import vn.edu.ptit.library.catalog.dto.DocumentSummaryDto;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

public interface CatalogService {

    DocumentSummaryDto getDocumentDetails(UUID id);

    PageResponse<DocumentSummaryDto> searchDocuments(String keyword, PaginationRequest request);

    Document getDocumentEntityOrThrow(UUID id);
}
