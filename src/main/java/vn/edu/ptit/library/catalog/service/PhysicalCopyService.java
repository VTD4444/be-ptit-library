package vn.edu.ptit.library.catalog.service;

import vn.edu.ptit.library.catalog.dto.PhysicalCopyDto;
import vn.edu.ptit.library.catalog.dto.PhysicalCopyRequestDto;

import java.util.List;
import java.util.UUID;

public interface PhysicalCopyService {

    List<PhysicalCopyDto> getCopiesByDocument(UUID documentId);

    PhysicalCopyDto createCopy(PhysicalCopyRequestDto request);

    PhysicalCopyDto updateCopy(UUID id, PhysicalCopyRequestDto request);
}
