package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.dto.PhysicalCopyDto;
import vn.edu.ptit.library.catalog.dto.PhysicalCopyRequestDto;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.catalog.service.CatalogService;
import vn.edu.ptit.library.catalog.service.PhysicalCopyService;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhysicalCopyServiceImpl implements PhysicalCopyService {

    private final PhysicalCopyRepository physicalCopyRepository;
    private final CatalogService catalogService;

    @Override
    @Transactional(readOnly = true)
    public List<PhysicalCopyDto> getCopiesByDocument(UUID documentId) {
        catalogService.getDocumentEntityOrThrow(documentId);

        return physicalCopyRepository.findByDocumentId(documentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public PhysicalCopyDto createCopy(PhysicalCopyRequestDto request) {
        Document document = catalogService.getDocumentEntityOrThrow(request.getDocumentId());
        checkDuplicateBarcode(request.getBarcode(), null);

        String status = parseStatus(request.getStatus());

        PhysicalCopy copy = PhysicalCopy.builder()
                .document(document)
                .barcode(request.getBarcode().trim())
                .callNumber(request.getCallNumber())
                .shelfLocation(request.getShelfLocation())
                .status(status != null ? status : PhysicalCopyStatus.AVAILABLE.name())
                .accessionDate(OffsetDateTime.now())
                .build();

        return toDto(physicalCopyRepository.save(copy));
    }

    @Override
    @Transactional
    public PhysicalCopyDto updateCopy(UUID id, PhysicalCopyRequestDto request) {
        PhysicalCopy copy = physicalCopyRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PHYSICAL_COPY_NOT_FOUND));
        Document document = catalogService.getDocumentEntityOrThrow(request.getDocumentId());
        checkDuplicateBarcode(request.getBarcode(), id);

        copy.setDocument(document);
        copy.setBarcode(request.getBarcode().trim());
        copy.setCallNumber(request.getCallNumber());
        copy.setShelfLocation(request.getShelfLocation());

        String status = parseStatus(request.getStatus());
        if (status != null) {
            copy.setStatus(status);
        }

        return toDto(physicalCopyRepository.save(copy));
    }

    private void checkDuplicateBarcode(String barcode, UUID currentCopyId) {
        physicalCopyRepository.findByBarcode(barcode.trim())
                .filter(existing -> !existing.getId().equals(currentCopyId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Mã vạch đã tồn tại: " + barcode);
                });
    }

    private String parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }

        try {
            return PhysicalCopyStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Trạng thái bản sách không hợp lệ: " + status);
        }
    }

    private PhysicalCopyDto toDto(PhysicalCopy copy) {
        return PhysicalCopyDto.builder()
                .id(copy.getId())
                .documentId(copy.getDocument().getId())
                .barcode(copy.getBarcode())
                .callNumber(copy.getCallNumber())
                .shelfLocation(copy.getShelfLocation())
                .status(copy.getStatus())
                .accessionDate(copy.getAccessionDate())
                .lastInventoryDate(copy.getLastInventoryDate())
                .build();
    }
}
