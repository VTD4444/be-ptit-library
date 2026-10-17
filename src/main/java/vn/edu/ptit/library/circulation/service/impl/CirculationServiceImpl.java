package vn.edu.ptit.library.circulation.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.entity.Circulation;
import vn.edu.ptit.library.circulation.entity.HoldQueue;
import vn.edu.ptit.library.circulation.entity.LoanPolicy;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.circulation.repository.HoldQueueRepository;
import vn.edu.ptit.library.circulation.repository.LoanPolicyRepository;
import vn.edu.ptit.library.circulation.service.CirculationService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.enums.HoldStatus;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CirculationServiceImpl implements CirculationService {

    private final CirculationRepository circulationRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final LoanPolicyRepository loanPolicyRepository;
    private final HoldQueueRepository holdQueueRepository;

    @Override
    @Transactional
    public Circulation borrowBook(BorrowRequestDto request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getCardExpiry() != null && user.getCardExpiry().isBefore(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.USER_CARD_EXPIRED);
        }

        PhysicalCopy copy = physicalCopyRepository.findById(request.getPhysicalCopyId())
                .orElseThrow(() -> new AppException(ErrorCode.PHYSICAL_COPY_NOT_FOUND));

        if (!PhysicalCopyStatus.AVAILABLE.name().equalsIgnoreCase(copy.getStatus())) {
            throw new AppException(ErrorCode.COPY_NOT_AVAILABLE);
        }

        // Kiem tra chinh sach muon (LoanPolicy)
        LoanPolicy policy = loanPolicyRepository.findByUserTypeAndDocType(user.getUserType(), copy.getDocument().getDocType())
                .orElse(null);

        int maxLoans = policy != null && policy.getMaxLoans() != null ? policy.getMaxLoans() : 5;
        int loanDays = policy != null && policy.getLoanDays() != null ? policy.getLoanDays() : 14;

        long currentLoans = circulationRepository.countByUserIdAndReturnDateIsNull(user.getId());
        if (currentLoans >= maxLoans) {
            throw new AppException(ErrorCode.LOAN_LIMIT_EXCEEDED);
        }

        User librarian = SecurityUtils.getCurrentUserDetails()
                .flatMap(u -> userRepository.findById(u.getId()))
                .orElse(null);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dueDate = now.plusDays(loanDays);

        Circulation circulation = Circulation.builder()
                .user(user)
                .physicalCopy(copy)
                .issuedBy(librarian)
                .borrowDate(now)
                .dueDate(dueDate)
                .borrowNote(request.getBorrowNote())
                .renewalsCount(0)
                .build();

        copy.setStatus(PhysicalCopyStatus.BORROWED.name());
        physicalCopyRepository.save(copy);

        return circulationRepository.save(circulation);
    }

    @Override
    @Transactional
    public Circulation returnBook(Long circulationId, String returnNote) {
        Circulation circulation = circulationRepository.findById(circulationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt mượn sách"));

        if (circulation.getReturnDate() != null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Sách này đã được ghi nhận trả trước đó");
        }

        circulation.setReturnDate(OffsetDateTime.now());
        circulation.setReturnNote(returnNote);

        PhysicalCopy copy = circulation.getPhysicalCopy();
        copy.setStatus(PhysicalCopyStatus.AVAILABLE.name());
        physicalCopyRepository.save(copy);

        return circulationRepository.save(circulation);
    }

    @Override
    @Transactional
    public Circulation renewLoan(Long circulationId) {
        Circulation circulation = circulationRepository.findById(circulationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt mượn"));

        if (circulation.getReturnDate() != null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Lượt mượn đã hoàn thành, không thể gia hạn");
        }

        LoanPolicy policy = loanPolicyRepository.findByUserTypeAndDocType(
                circulation.getUser().getUserType(),
                circulation.getPhysicalCopy().getDocument().getDocType()
        ).orElse(null);

        int maxRenewals = policy != null && policy.getMaxRenewals() != null ? policy.getMaxRenewals() : 2;
        int loanDays = policy != null && policy.getLoanDays() != null ? policy.getLoanDays() : 14;

        if (circulation.getRenewalsCount() >= maxRenewals) {
            throw new AppException(ErrorCode.RENEWAL_LIMIT_EXCEEDED);
        }

        circulation.setRenewalsCount(circulation.getRenewalsCount() + 1);
        circulation.setDueDate(circulation.getDueDate().plusDays(loanDays));

        return circulationRepository.save(circulation);
    }

    @Override
    @Transactional
    public HoldQueue placeHold(UUID documentId) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        User user = userRepository.getReferenceById(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (holdQueueRepository.findByUserIdAndDocumentIdAndStatus(userId, documentId, HoldStatus.WAITING.name()).isPresent()) {
            throw new AppException(ErrorCode.HOLD_ALREADY_EXISTS);
        }

        int nextPosition = holdQueueRepository.getNextQueuePosition(documentId);

        HoldQueue hold = HoldQueue.builder()
                .user(user)
                .document(document)
                .queuePosition(nextPosition)
                .requestDate(OffsetDateTime.now())
                .status(HoldStatus.WAITING.name())
                .build();

        return holdQueueRepository.save(hold);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Circulation> getMyLoans(PaginationRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        Page<Circulation> page = circulationRepository.findByUserId(currentUserId, request.toPageable());
        return PageResponse.from(page);
    }
}
