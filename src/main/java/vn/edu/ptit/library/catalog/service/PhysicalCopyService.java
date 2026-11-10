package vn.edu.ptit.library.catalog.service;

import vn.edu.ptit.library.catalog.dto.PhysicalCopyDto;
import vn.edu.ptit.library.catalog.dto.PhysicalCopyRequestDto;

import java.util.List;
import java.util.UUID;

public interface PhysicalCopyService {

    List<PhysicalCopyDto> getCopiesByDocument(UUID documentId);

    PhysicalCopyDto createCopy(PhysicalCopyRequestDto request);

    PhysicalCopyDto updateCopy(UUID id, PhysicalCopyRequestDto request);

    /** Xoa mem ban sach giay; tu choi khi ban dang duoc muon hoac dang giu cho ban doc. */
    void deleteCopy(UUID id);
}
