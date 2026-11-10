package vn.edu.ptit.library.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.ptit.library.catalog.dto.PhysicalCopyRequestDto;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.catalog.service.CatalogService;
import vn.edu.ptit.library.catalog.service.impl.PhysicalCopyServiceImpl;
import vn.edu.ptit.library.circulation.entity.Circulation;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.circulation.repository.HoldQueueRepository;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.common.exception.AppException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhysicalCopyServiceTest {

    @Mock
    private PhysicalCopyRepository physicalCopyRepository;
    @Mock
    private CatalogService catalogService;
    @Mock
    private CirculationRepository circulationRepository;
    @Mock
    private HoldQueueRepository holdQueueRepository;

    @InjectMocks
    private PhysicalCopyServiceImpl service;

    private PhysicalCopy copy(String status) {
        return PhysicalCopy.builder()
                .id(UUID.randomUUID())
                .document(Document.builder().id(UUID.randomUUID()).title("Tai lieu").build())
                .barcode("PTIT-0001")
                .status(status)
                .build();
    }

    private PhysicalCopyRequestDto request(PhysicalCopy target, String status) {
        return PhysicalCopyRequestDto.builder()
                .documentId(target.getDocument().getId())
                .barcode(target.getBarcode())
                .status(status)
                .build();
    }

    @Test
    void deleteCopy_marksAvailableCopyDeleted() {
        PhysicalCopy target = copy(PhysicalCopyStatus.AVAILABLE.name());
        when(physicalCopyRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(circulationRepository.findByPhysicalCopyIdAndReturnDateIsNull(target.getId())).thenReturn(Optional.empty());
        when(holdQueueRepository.findByPhysicalCopyIdAndStatus(target.getId(), "READY_FOR_PICKUP")).thenReturn(Optional.empty());

        service.deleteCopy(target.getId());

        assertNotNull(target.getDeletedAt());
        verify(physicalCopyRepository).save(target);
    }

    @Test
    void deleteCopy_rejectsBorrowedCopy() {
        PhysicalCopy target = copy(PhysicalCopyStatus.BORROWED.name());
        when(physicalCopyRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThrows(AppException.class, () -> service.deleteCopy(target.getId()));
        assertNull(target.getDeletedAt());
        verify(physicalCopyRepository, never()).save(any());
    }

    @Test
    void deleteCopy_rejectsCopyWithOpenLoanEvenIfStatusIsStale() {
        PhysicalCopy target = copy(PhysicalCopyStatus.AVAILABLE.name());
        when(physicalCopyRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(circulationRepository.findByPhysicalCopyIdAndReturnDateIsNull(target.getId()))
                .thenReturn(Optional.of(new Circulation()));

        assertThrows(AppException.class, () -> service.deleteCopy(target.getId()));
        assertNull(target.getDeletedAt());
    }

    @Test
    void createCopy_rejectsCirculationStatus() {
        PhysicalCopy target = copy(null);
        when(catalogService.getDocumentEntityOrThrow(target.getDocument().getId())).thenReturn(target.getDocument());
        when(physicalCopyRepository.findByBarcode(anyString())).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> service.createCopy(request(target, "BORROWED")));
        verify(physicalCopyRepository, never()).save(any());
    }

    @Test
    void updateCopy_cannotReleaseBorrowedCopyManually() {
        PhysicalCopy target = copy(PhysicalCopyStatus.BORROWED.name());
        when(physicalCopyRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(catalogService.getDocumentEntityOrThrow(target.getDocument().getId())).thenReturn(target.getDocument());
        when(physicalCopyRepository.findByBarcode(anyString())).thenReturn(Optional.of(target));

        assertThrows(AppException.class, () -> service.updateCopy(target.getId(), request(target, "AVAILABLE")));
        assertEquals(PhysicalCopyStatus.BORROWED.name(), target.getStatus());
    }

    @Test
    void updateCopy_keepsBorrowedStatusWhenOnlyShelfChanges() {
        PhysicalCopy target = copy(PhysicalCopyStatus.BORROWED.name());
        when(physicalCopyRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(catalogService.getDocumentEntityOrThrow(target.getDocument().getId())).thenReturn(target.getDocument());
        when(physicalCopyRepository.findByBarcode(anyString())).thenReturn(Optional.of(target));
        when(physicalCopyRepository.save(target)).thenReturn(target);

        PhysicalCopyRequestDto update = request(target, "BORROWED");
        update.setShelfLocation("B1-02");
        assertEquals("B1-02", service.updateCopy(target.getId(), update).getShelfLocation());
    }
}
