package vn.edu.ptit.library.circulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.dto.BorrowResponseDto;
import vn.edu.ptit.library.circulation.dto.FinePaymentResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldRequestDto;
import vn.edu.ptit.library.circulation.dto.HoldResponseDto;
import vn.edu.ptit.library.circulation.dto.RenewRequestDto;
import vn.edu.ptit.library.circulation.dto.RenewResponseDto;
import vn.edu.ptit.library.circulation.dto.ReturnRequestDto;
import vn.edu.ptit.library.circulation.dto.ReturnResponseDto;
import vn.edu.ptit.library.circulation.entity.Circulation;
import vn.edu.ptit.library.circulation.entity.CirculationFine;
import vn.edu.ptit.library.circulation.entity.HoldQueue;
import vn.edu.ptit.library.circulation.entity.LoanPolicy;
import vn.edu.ptit.library.circulation.entity.PenaltyType;
import vn.edu.ptit.library.circulation.repository.CirculationFineRepository;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.circulation.repository.HoldQueueRepository;
import vn.edu.ptit.library.circulation.repository.LoanPolicyRepository;
import vn.edu.ptit.library.circulation.repository.PenaltyTypeRepository;
import vn.edu.ptit.library.circulation.service.impl.CirculationServiceImpl;
import vn.edu.ptit.library.common.enums.BookReturnCondition;
import vn.edu.ptit.library.common.enums.FineStatus;
import vn.edu.ptit.library.common.enums.HoldStatus;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.security.CustomUserDetails;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CirculationServiceTest {

    @Mock
    private CirculationRepository circulationRepository;
    @Mock
    private PhysicalCopyRepository physicalCopyRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoanPolicyRepository loanPolicyRepository;
    @Mock
    private HoldQueueRepository holdQueueRepository;
    @Mock
    private CirculationFineRepository circulationFineRepository;
    @Mock
    private PenaltyTypeRepository penaltyTypeRepository;

    @InjectMocks
    private CirculationServiceImpl circulationService;

    private User reader;
    private User librarian;
    private Document document;
    private PhysicalCopy copy1;
    private LoanPolicy readerPolicy;

    @BeforeEach
    void setUp() {
        reader = User.builder()
                .id(UUID.randomUUID())
                .userCode("B21DCCN001")
                .email("reader@stu.ptit.edu.vn")
                .fullName("Nguyen Van A")
                .role("READER")
                .userType("STUDENT")
                .status("ACTIVE")
                .build();

        librarian = User.builder()
                .id(UUID.randomUUID())
                .userCode("LIB001")
                .email("librarian@ptit.edu.vn")
                .fullName("Tran Thi Thu")
                .role("LIBRARIAN")
                .userType("STAFF")
                .status("ACTIVE")
                .build();

        document = Document.builder()
                .id(UUID.randomUUID())
                .title("Cau truc du lieu va Giai thuat")
                .docType("TEXTBOOK")
                .build();

        copy1 = PhysicalCopy.builder()
                .id(UUID.randomUUID())
                .barcode("893524480001")
                .document(document)
                .status(PhysicalCopyStatus.AVAILABLE.name())
                .build();

        readerPolicy = LoanPolicy.builder()
                .id(1L)
                .userType("STUDENT")
                .docType("TEXTBOOK")
                .maxLoans(2)
                .loanDays(14)
                .maxRenewals(1)
                .holdExpiryDays(3)
                .build();

        CustomUserDetails userDetails = CustomUserDetails.builder()
                .id(librarian.getId())
                .userCode(librarian.getUserCode())
                .email(librarian.getEmail())
                .password("hashed_pwd")
                .role(librarian.getRole())
                .status(librarian.getStatus())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void borrowBooks_shouldReturnWarningWhenExceedingMaxLoans() {
        when(userRepository.findByUserCode("B21DCCN001")).thenReturn(Optional.of(reader));
        when(userRepository.findById(librarian.getId())).thenReturn(Optional.of(librarian));
        when(circulationFineRepository.findByUserIdAndStatusOrderByCreatedAtDesc(reader.getId(), FineStatus.UNPAID.name()))
                .thenReturn(Collections.emptyList());
        when(circulationRepository.countByUserIdAndReturnDateIsNull(reader.getId())).thenReturn(2L);
        when(physicalCopyRepository.findByBarcode("893524480001")).thenReturn(Optional.of(copy1));
        when(holdQueueRepository.findByUserIdAndDocumentIdAndStatus(
                reader.getId(), document.getId(), HoldStatus.WAITING.name())).thenReturn(Optional.empty());
        when(loanPolicyRepository.findByUserTypeAndDocType("STUDENT", "TEXTBOOK"))
                .thenReturn(Optional.of(readerPolicy));
        when(circulationRepository.save(any(Circulation.class))).thenAnswer(inv -> {
            Circulation c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        BorrowRequestDto request = BorrowRequestDto.builder()
                .userCode("B21DCCN001")
                .barcodes(List.of("893524480001"))
                .build();

        BorrowResponseDto response = circulationService.borrowBooks(request);

        assertEquals(1, response.getTotalBorrowed());
        assertEquals(3L, response.getCurrentActiveLoans());
        assertFalse(response.getWarnings().isEmpty());
        assertEquals(PhysicalCopyStatus.BORROWED.name(), copy1.getStatus());
    }

    @Test
    void returnBook_whenOverdueAndNormalAndHasWaitingHold_shouldGenerateFineAndReserveCopy() {
        copy1.setStatus(PhysicalCopyStatus.BORROWED.name());
        Circulation activeLoan = Circulation.builder()
                .id(200L)
                .user(reader)
                .physicalCopy(copy1)
                .borrowDate(OffsetDateTime.now().minusDays(20))
                .dueDate(OffsetDateTime.now().minusDays(5))
                .build();

        HoldQueue waitingHold = HoldQueue.builder()
                .id(50L)
                .user(reader)
                .document(document)
                .queuePosition(1)
                .status(HoldStatus.WAITING.name())
                .build();

        PenaltyType overduePenalty = PenaltyType.builder()
                .id(1L)
                .code("OVERDUE")
                .name("Phat tra sach qua han")
                .unitName("Ngay")
                .unitPrice(5000L)
                .build();

        when(physicalCopyRepository.findByBarcode("893524480001")).thenReturn(Optional.of(copy1));
        when(circulationRepository.findByPhysicalCopyIdAndReturnDateIsNull(copy1.getId()))
                .thenReturn(Optional.of(activeLoan));
        when(penaltyTypeRepository.findByCode("OVERDUE")).thenReturn(Optional.of(overduePenalty));
        when(circulationFineRepository.save(any(CirculationFine.class))).thenAnswer(inv -> {
            CirculationFine fine = inv.getArgument(0);
            fine.setId(300L);
            return fine;
        });
        when(holdQueueRepository.findFirstByDocumentIdAndStatusOrderByQueuePositionAsc(
                document.getId(), HoldStatus.WAITING.name())).thenReturn(Optional.of(waitingHold));
        when(loanPolicyRepository.findByUserTypeAndDocType("STUDENT", "TEXTBOOK"))
                .thenReturn(Optional.of(readerPolicy));

        ReturnRequestDto request = ReturnRequestDto.builder()
                .barcode("893524480001")
                .condition(BookReturnCondition.NORMAL)
                .build();

        ReturnResponseDto response = circulationService.returnBook(request);

        assertTrue(response.getOverdueDays() >= 5);
        assertTrue(response.getTotalFineAmount() >= 25000L);
        assertEquals(1, response.getGeneratedFines().size());
        assertEquals(PhysicalCopyStatus.RESERVED.name(), response.getUpdatedCopyStatus());
        assertEquals("B21DCCN001", response.getAssignedHoldUserCode());
        assertEquals(HoldStatus.READY_FOR_PICKUP.name(), waitingHold.getStatus());
    }

    @Test
    void renewLoan_whenMaxRenewalsReached_shouldReturnExceededStatus() {
        Circulation activeLoan = Circulation.builder()
                .id(201L)
                .user(reader)
                .physicalCopy(copy1)
                .borrowDate(OffsetDateTime.now().minusDays(10))
                .dueDate(OffsetDateTime.now().plusDays(4))
                .renewalsCount(1)
                .build();

        when(circulationRepository.findById(201L)).thenReturn(Optional.of(activeLoan));
        when(holdQueueRepository.countByDocumentIdAndStatus(document.getId(), HoldStatus.WAITING.name()))
                .thenReturn(0L);
        when(physicalCopyRepository.countByDocumentIdAndStatus(document.getId(), PhysicalCopyStatus.AVAILABLE.name()))
                .thenReturn(0L);
        when(loanPolicyRepository.findByUserTypeAndDocType("STUDENT", "TEXTBOOK"))
                .thenReturn(Optional.of(readerPolicy));

        RenewRequestDto request = RenewRequestDto.builder()
                .circulationId(201L)
                .build();

        RenewResponseDto response = circulationService.renewLoan(request);

        assertEquals("EXCEEDED_MAX_RENEWALS", response.getRenewalStatus());
        assertEquals(1, response.getRenewalsCount());
    }

    @Test
    void placeHold_whenAllCopiesBusy_shouldCreateWaitingHoldWithEstimatedDate() {
        CustomUserDetails readerDetails = CustomUserDetails.builder()
                .id(reader.getId())
                .userCode(reader.getUserCode())
                .email(reader.getEmail())
                .password("hashed_pwd")
                .role(reader.getRole())
                .status(reader.getStatus())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(readerDetails, null, readerDetails.getAuthorities())
        );

        copy1.setStatus(PhysicalCopyStatus.BORROWED.name());
        OffsetDateTime dueDate = OffsetDateTime.now().plusDays(6);
        Circulation activeLoan = Circulation.builder()
                .id(202L)
                .user(librarian)
                .physicalCopy(copy1)
                .dueDate(dueDate)
                .build();

        when(userRepository.findById(reader.getId())).thenReturn(Optional.of(reader));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(physicalCopyRepository.countByDocumentIdAndStatus(document.getId(), PhysicalCopyStatus.AVAILABLE.name()))
                .thenReturn(0L);
        when(circulationRepository.isUserCurrentlyBorrowingDocument(reader.getId(), document.getId()))
                .thenReturn(false);
        when(holdQueueRepository.findActiveHoldByUserAndDocument(reader.getId(), document.getId()))
                .thenReturn(Optional.empty());
        when(holdQueueRepository.getNextQueuePosition(document.getId())).thenReturn(1);
        when(circulationRepository.findActiveLoansByDocumentIdOrderByDueDateAsc(document.getId()))
                .thenReturn(List.of(activeLoan));
        when(holdQueueRepository.save(any(HoldQueue.class))).thenAnswer(inv -> {
            HoldQueue hold = inv.getArgument(0);
            hold.setId(77L);
            return hold;
        });

        HoldRequestDto request = HoldRequestDto.builder()
                .documentId(document.getId())
                .build();

        HoldResponseDto response = circulationService.placeHold(request);

        assertEquals(77L, response.getHoldId());
        assertEquals(1, response.getQueuePosition());
        assertEquals(HoldStatus.WAITING.name(), response.getStatus());
        assertNotNull(response.getEstimatedAvailableDate());
    }

    @Test
    void payFine_whenUnpaid_shouldMarkAsPaid() {
        PenaltyType penalty = PenaltyType.builder()
                .id(1L)
                .code("OVERDUE")
                .name("Phat tra sach qua han")
                .unitPrice(5000L)
                .build();

        Circulation loan = Circulation.builder()
                .id(205L)
                .user(reader)
                .physicalCopy(copy1)
                .build();

        CirculationFine fine = CirculationFine.builder()
                .id(900L)
                .circulation(loan)
                .penaltyType(penalty)
                .fineAmount(15000L)
                .status(FineStatus.UNPAID.name())
                .build();

        when(circulationFineRepository.findById(900L)).thenReturn(Optional.of(fine));
        when(circulationFineRepository.save(any(CirculationFine.class))).thenAnswer(inv -> inv.getArgument(0));

        FinePaymentResponseDto response = circulationService.payFine(900L, "Thu tien mat tai quay");

        assertEquals(FineStatus.PAID.name(), response.getStatus());
        assertEquals(15000L, response.getFineAmount());
        assertTrue(response.getNote().contains("Thu tien mat tai quay"));
    }
}
