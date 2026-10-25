package vn.edu.ptit.library.circulation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.circulation.dto.BorrowRequestDto;
import vn.edu.ptit.library.circulation.dto.BorrowResponseDto;
import vn.edu.ptit.library.circulation.dto.BorrowedItemDto;
import vn.edu.ptit.library.circulation.dto.CirculationHistoryItemDto;
import vn.edu.ptit.library.circulation.dto.FineItemDto;
import vn.edu.ptit.library.circulation.dto.FinePaymentResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldCancelResponseDto;
import vn.edu.ptit.library.circulation.dto.HoldRequestDto;
import vn.edu.ptit.library.circulation.dto.HoldResponseDto;
import vn.edu.ptit.library.circulation.dto.RenewRequestDto;
import vn.edu.ptit.library.circulation.dto.RenewResponseDto;
import vn.edu.ptit.library.circulation.dto.ReturnRequestDto;
import vn.edu.ptit.library.circulation.dto.ReturnResponseDto;
import vn.edu.ptit.library.circulation.dto.UserFinesSummaryDto;
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
import vn.edu.ptit.library.circulation.service.CirculationService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.enums.BookReturnCondition;
import vn.edu.ptit.library.common.enums.FineStatus;
import vn.edu.ptit.library.common.enums.HoldStatus;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.security.CustomUserDetails;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CirculationServiceImpl implements CirculationService {

    private static final int DEFAULT_MAX_LOANS = 5;
    private static final int DEFAULT_LOAN_DAYS = 14;
    private static final int DEFAULT_MAX_RENEWALS = 2;
    private static final int DEFAULT_HOLD_EXPIRY_DAYS = 3;

    private final CirculationRepository circulationRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final LoanPolicyRepository loanPolicyRepository;
    private final HoldQueueRepository holdQueueRepository;
    private final CirculationFineRepository circulationFineRepository;
    private final PenaltyTypeRepository penaltyTypeRepository;

    @Override
    @Transactional
    public BorrowResponseDto borrowBooks(BorrowRequestDto request) {
        String code = request.getUserCode().trim();
        User user = userRepository.findByUserCode(code)
                .or(() -> userRepository.findByEmail(code))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy bạn đọc với mã: " + code));

        if ("LOCKED".equalsIgnoreCase(user.getStatus()) || "INACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản bạn đọc hiện đang bị khóa hoặc ngừng hoạt động");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (user.getCardExpiry() != null && user.getCardExpiry().isBefore(now)) {
            throw new AppException(ErrorCode.USER_CARD_EXPIRED);
        }

        List<String> warnings = new ArrayList<>();

        // Kiem tra cong no chua thanh toan de dua ra canh bao cho Thu thu
        List<CirculationFine> unpaidFines = circulationFineRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), FineStatus.UNPAID.name());
        if (!unpaidFines.isEmpty()) {
            long totalUnpaid = unpaidFines.stream()
                    .mapToLong(f -> f.getFineAmount() != null ? f.getFineAmount() : 0L)
                    .sum();
            warnings.add(String.format("Lưu ý: Bạn đọc đang có %d khoản phạt chưa thanh toán (Tổng: %,d VNĐ)",
                    unpaidFines.size(), totalUnpaid));
        }

        User librarian = SecurityUtils.getCurrentUserDetails()
                .flatMap(u -> userRepository.findById(u.getId()))
                .orElse(null);

        long currentActiveLoans = circulationRepository.countByUserIdAndReturnDateIsNull(user.getId());
        List<BorrowedItemDto> borrowedItems = new ArrayList<>();

        Set<String> uniqueBarcodes = new LinkedHashSet<>();
        for (String bc : request.getBarcodes()) {
            if (StringUtils.hasText(bc)) {
                uniqueBarcodes.add(bc.trim());
            }
        }

        for (String barcode : uniqueBarcodes) {
            PhysicalCopy copy = physicalCopyRepository.findByBarcode(barcode)
                    .orElseThrow(() -> new AppException(ErrorCode.PHYSICAL_COPY_NOT_FOUND,
                            "Không tìm thấy cuốn sách với mã vạch: " + barcode));

            Document document = copy.getDocument();

            // Kiem tra trang thai sach vat ly (AVAILABLE hoac RESERVED cho chinh ban doc nay)
            if (PhysicalCopyStatus.RESERVED.name().equalsIgnoreCase(copy.getStatus())) {
                HoldQueue readyHold = holdQueueRepository
                        .findByPhysicalCopyIdAndStatus(copy.getId(), HoldStatus.READY_FOR_PICKUP.name())
                        .orElse(null);
                if (readyHold == null || !readyHold.getUser().getId().equals(user.getId())) {
                    throw new AppException(ErrorCode.COPY_NOT_AVAILABLE,
                            "Cuốn sách " + barcode + " đang được giữ đặt trước cho bạn đọc khác");
                }
                readyHold.setStatus(HoldStatus.FULFILLED.name());
                holdQueueRepository.save(readyHold);
            } else if (PhysicalCopyStatus.AVAILABLE.name().equalsIgnoreCase(copy.getStatus())) {
                // Neu ban doc nay co yeu cau WAITING cho dau sach nay thi tu dong chuyen sang FULFILLED
                holdQueueRepository.findByUserIdAndDocumentIdAndStatus(
                        user.getId(), document.getId(), HoldStatus.WAITING.name()
                ).ifPresent(waitingHold -> {
                    Integer pos = waitingHold.getQueuePosition();
                    waitingHold.setStatus(HoldStatus.FULFILLED.name());
                    waitingHold.setPhysicalCopy(copy);
                    holdQueueRepository.save(waitingHold);
                    if (pos != null) {
                        holdQueueRepository.decrementQueuePositionsAfter(document.getId(), pos);
                    }
                });
            } else {
                throw new AppException(ErrorCode.COPY_NOT_AVAILABLE,
                        "Cuốn sách " + barcode + " hiện không sẵn sàng để mượn (Trạng thái: " + copy.getStatus() + ")");
            }

            LoanPolicy policy = loanPolicyRepository
                    .findByUserTypeAndDocType(user.getUserType(), document.getDocType())
                    .orElse(null);

            int maxLoans = (policy != null && policy.getMaxLoans() != null) ? policy.getMaxLoans() : DEFAULT_MAX_LOANS;
            int loanDays = (policy != null && policy.getLoanDays() != null) ? policy.getLoanDays() : DEFAULT_LOAN_DAYS;

            currentActiveLoans++;
            if (currentActiveLoans > maxLoans) {
                warnings.add(String.format(
                        "Cảnh báo: Bạn đọc mượn vượt quá số lượng tối đa theo quy định (%d/%d cuốn) tại mã vạch %s",
                        currentActiveLoans, maxLoans, barcode));
            }

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

            Circulation savedCirculation = circulationRepository.save(circulation);

            borrowedItems.add(BorrowedItemDto.builder()
                    .circulationId(savedCirculation.getId())
                    .barcode(copy.getBarcode())
                    .documentId(document.getId())
                    .documentTitle(document.getTitle())
                    .borrowDate(now)
                    .dueDate(dueDate)
                    .status(PhysicalCopyStatus.BORROWED.name())
                    .build());
        }

        return BorrowResponseDto.builder()
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .totalBorrowed(borrowedItems.size())
                .currentActiveLoans(currentActiveLoans)
                .items(borrowedItems)
                .warnings(warnings)
                .build();
    }

    @Override
    @Transactional
    public ReturnResponseDto returnBook(ReturnRequestDto request) {
        String barcode = request.getBarcode().trim();
        PhysicalCopy copy = physicalCopyRepository.findByBarcode(barcode)
                .orElseThrow(() -> new AppException(ErrorCode.PHYSICAL_COPY_NOT_FOUND,
                        "Không tìm thấy cuốn sách với mã vạch: " + barcode));

        Circulation circulation = circulationRepository.findByPhysicalCopyIdAndReturnDateIsNull(copy.getId())
                .orElseThrow(() -> new AppException(ErrorCode.LOAN_NOT_FOUND,
                        "Cuốn sách với mã vạch " + barcode + " hiện không có lượt mượn nào đang mở"));

        OffsetDateTime now = OffsetDateTime.now();
        circulation.setReturnDate(now);
        circulation.setReturnNote(request.getReturnNote());

        long overdueDays = 0;
        if (circulation.getDueDate() != null && now.isAfter(circulation.getDueDate())) {
            overdueDays = ChronoUnit.DAYS.between(circulation.getDueDate().toLocalDate(), now.toLocalDate());
            if (overdueDays <= 0) {
                overdueDays = 1;
            }
        }

        List<FineItemDto> generatedFines = new ArrayList<>();
        long totalFineAmount = 0L;
        Document document = copy.getDocument();

        // 1. Phat qua han (neu co)
        if (overdueDays > 0) {
            PenaltyType overduePenalty = getOrCreatePenaltyType(
                    "OVERDUE", "Phạt trả sách quá hạn", "Ngày", 5000L);
            long unitPrice = overduePenalty.getUnitPrice() != null ? overduePenalty.getUnitPrice() : 5000L;
            long amount = overdueDays * unitPrice;

            CirculationFine fine = CirculationFine.builder()
                    .circulation(circulation)
                    .penaltyType(overduePenalty)
                    .quantity((int) overdueDays)
                    .fineAmount(amount)
                    .status(FineStatus.UNPAID.name())
                    .note("Trả sách quá hạn " + overdueDays + " ngày")
                    .build();

            CirculationFine savedFine = circulationFineRepository.save(fine);
            generatedFines.add(toFineItemDto(savedFine, copy.getBarcode(), document.getTitle()));
            totalFineAmount += amount;
        }

        // 2. Phat hu hong hoac mat sach (neu co)
        BookReturnCondition condition = request.getCondition() != null
                ? request.getCondition()
                : BookReturnCondition.NORMAL;

        if (condition == BookReturnCondition.DAMAGED) {
            PenaltyType damagedPenalty = getOrCreatePenaltyType(
                    "DAMAGED", "Phạt làm rách hoặc hư hỏng sách", "Cuốn", 50000L);
            long unitPrice = damagedPenalty.getUnitPrice() != null ? damagedPenalty.getUnitPrice() : 50000L;

            CirculationFine fine = CirculationFine.builder()
                    .circulation(circulation)
                    .penaltyType(damagedPenalty)
                    .quantity(1)
                    .fineAmount(unitPrice)
                    .status(FineStatus.UNPAID.name())
                    .note(StringUtils.hasText(request.getReturnNote())
                            ? request.getReturnNote()
                            : "Sách bị rách/hư hỏng khi trả")
                    .build();

            CirculationFine savedFine = circulationFineRepository.save(fine);
            generatedFines.add(toFineItemDto(savedFine, copy.getBarcode(), document.getTitle()));
            totalFineAmount += unitPrice;
        } else if (condition == BookReturnCondition.LOST) {
            PenaltyType lostPenalty = getOrCreatePenaltyType(
                    "LOST", "Phạt làm mất sách", "Cuốn", 200000L);
            long unitPrice = lostPenalty.getUnitPrice() != null ? lostPenalty.getUnitPrice() : 200000L;

            CirculationFine fine = CirculationFine.builder()
                    .circulation(circulation)
                    .penaltyType(lostPenalty)
                    .quantity(1)
                    .fineAmount(unitPrice)
                    .status(FineStatus.UNPAID.name())
                    .note(StringUtils.hasText(request.getReturnNote())
                            ? request.getReturnNote()
                            : "Làm mất sách")
                    .build();

            CirculationFine savedFine = circulationFineRepository.save(fine);
            generatedFines.add(toFineItemDto(savedFine, copy.getBarcode(), document.getTitle()));
            totalFineAmount += unitPrice;
        }

        // 3. Cap nhat trang thai ban sach & kiem tra hang cho dat truoc (HoldQueue)
        String assignedHoldUserCode = null;
        if (condition == BookReturnCondition.LOST) {
            copy.setStatus(PhysicalCopyStatus.LOST.name());
        } else if (condition == BookReturnCondition.DAMAGED) {
            copy.setStatus(PhysicalCopyStatus.DAMAGED.name());
        } else {
            Optional<HoldQueue> waitingHoldOpt = holdQueueRepository
                    .findFirstByDocumentIdAndStatusOrderByQueuePositionAsc(document.getId(), HoldStatus.WAITING.name());

            if (waitingHoldOpt.isPresent()) {
                HoldQueue nextHold = waitingHoldOpt.get();
                Integer oldPos = nextHold.getQueuePosition();

                LoanPolicy policy = loanPolicyRepository
                        .findByUserTypeAndDocType(nextHold.getUser().getUserType(), document.getDocType())
                        .orElse(null);
                int holdExpiryDays = (policy != null && policy.getHoldExpiryDays() != null)
                        ? policy.getHoldExpiryDays()
                        : DEFAULT_HOLD_EXPIRY_DAYS;

                nextHold.setStatus(HoldStatus.READY_FOR_PICKUP.name());
                nextHold.setPhysicalCopy(copy);
                nextHold.setReadyDate(now);
                nextHold.setPickupDeadline(now.plusDays(holdExpiryDays));
                holdQueueRepository.save(nextHold);

                if (oldPos != null) {
                    holdQueueRepository.decrementQueuePositionsAfter(document.getId(), oldPos);
                }

                copy.setStatus(PhysicalCopyStatus.RESERVED.name());
                assignedHoldUserCode = nextHold.getUser().getUserCode();
            } else {
                copy.setStatus(PhysicalCopyStatus.AVAILABLE.name());
            }
        }

        physicalCopyRepository.save(copy);
        circulationRepository.save(circulation);

        return ReturnResponseDto.builder()
                .circulationId(circulation.getId())
                .barcode(copy.getBarcode())
                .documentId(document.getId())
                .documentTitle(document.getTitle())
                .userCode(circulation.getUser().getUserCode())
                .borrowDate(circulation.getBorrowDate())
                .dueDate(circulation.getDueDate())
                .returnDate(now)
                .overdueDays(overdueDays)
                .totalFineAmount(totalFineAmount)
                .updatedCopyStatus(copy.getStatus())
                .assignedHoldUserCode(assignedHoldUserCode)
                .generatedFines(generatedFines)
                .build();
    }

    @Override
    @Transactional
    public RenewResponseDto renewLoan(RenewRequestDto request) {
        Circulation circulation;
        if (request.getCirculationId() != null) {
            circulation = circulationRepository.findById(request.getCirculationId())
                    .orElseThrow(() -> new AppException(ErrorCode.LOAN_NOT_FOUND));
        } else if (StringUtils.hasText(request.getBarcode())) {
            circulation = circulationRepository.findActiveLoanByBarcode(request.getBarcode().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.LOAN_NOT_FOUND,
                            "Không tìm thấy lượt mượn đang hoạt động cho mã vạch: " + request.getBarcode()));
        } else {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Vui lòng truyền mã vạch của sách (barcode) hoặc ID lượt mượn (circulationId)");
        }

        if (circulation.getReturnDate() != null) {
            throw new AppException(ErrorCode.ALREADY_RETURNED);
        }

        // Kiem tra phan quyen: Neu la READER thi chi duoc gia han luot muon cua chinh minh
        CustomUserDetails currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        boolean isStaff = "LIBRARIAN".equalsIgnoreCase(currentUser.getRole())
                || "ADMIN".equalsIgnoreCase(currentUser.getRole());

        if (!isStaff && !circulation.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Bạn đọc chỉ được phép gia hạn tài liệu do chính mình đang mượn");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (circulation.getDueDate() != null && now.isAfter(circulation.getDueDate())) {
            throw new AppException(ErrorCode.CANNOT_RENEW_OVERDUE);
        }

        PhysicalCopy copy = circulation.getPhysicalCopy();
        Document document = copy.getDocument();

        // Neu dau sach dang co ban doc khac xep hang cho va kho da het ban AVAILABLE thi khong cho gia han
        long waitingHolds = holdQueueRepository.countByDocumentIdAndStatus(document.getId(), HoldStatus.WAITING.name());
        long availableCopies = physicalCopyRepository.countByDocumentIdAndStatus(
                document.getId(), PhysicalCopyStatus.AVAILABLE.name());
        if (waitingHolds > 0 && availableCopies == 0) {
            throw new AppException(ErrorCode.CANNOT_RENEW_ON_HOLD);
        }

        LoanPolicy policy = loanPolicyRepository
                .findByUserTypeAndDocType(circulation.getUser().getUserType(), document.getDocType())
                .orElse(null);

        int maxRenewals = (policy != null && policy.getMaxRenewals() != null)
                ? policy.getMaxRenewals()
                : DEFAULT_MAX_RENEWALS;
        int loanDays = (policy != null && policy.getLoanDays() != null)
                ? policy.getLoanDays()
                : DEFAULT_LOAN_DAYS;

        int currentRenewals = circulation.getRenewalsCount() != null ? circulation.getRenewalsCount() : 0;
        OffsetDateTime oldDueDate = circulation.getDueDate() != null ? circulation.getDueDate() : now;

        if (currentRenewals >= maxRenewals) {
            return RenewResponseDto.builder()
                    .circulationId(circulation.getId())
                    .barcode(copy.getBarcode())
                    .documentTitle(document.getTitle())
                    .oldDueDate(oldDueDate)
                    .newDueDate(oldDueDate)
                    .renewalsCount(currentRenewals)
                    .maxRenewals(maxRenewals)
                    .renewalStatus("EXCEEDED_MAX_RENEWALS")
                    .message("Vượt quá số lần gia hạn tối đa cho phép (" + maxRenewals + " lần)")
                    .build();
        }

        OffsetDateTime newDueDate = oldDueDate.plusDays(loanDays);
        circulation.setRenewalsCount(currentRenewals + 1);
        circulation.setDueDate(newDueDate);
        circulationRepository.save(circulation);

        return RenewResponseDto.builder()
                .circulationId(circulation.getId())
                .barcode(copy.getBarcode())
                .documentTitle(document.getTitle())
                .oldDueDate(oldDueDate)
                .newDueDate(newDueDate)
                .renewalsCount(circulation.getRenewalsCount())
                .maxRenewals(maxRenewals)
                .renewalStatus("SUCCESS")
                .message("Gia hạn tài liệu thành công thêm " + loanDays + " ngày")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CirculationHistoryItemDto> getMyCirculationHistory(String status, PaginationRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();

        // Bang circulation su dung cot borrowDate thay vi createdAt
        String sortBy = request.getSortBy();
        if (!StringUtils.hasText(sortBy) || "createdAt".equalsIgnoreCase(sortBy)) {
            sortBy = "borrowDate";
        }
        Sort sort = "asc".equalsIgnoreCase(request.getSortDir())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<Circulation> page;
        OffsetDateTime now = OffsetDateTime.now();

        if ("BORROWING".equalsIgnoreCase(status)) {
            page = circulationRepository.findByUserIdAndReturnDateIsNull(currentUserId, pageable);
        } else if ("RETURNED".equalsIgnoreCase(status)) {
            page = circulationRepository.findByUserIdAndReturnDateIsNotNull(currentUserId, pageable);
        } else if ("OVERDUE".equalsIgnoreCase(status)) {
            page = circulationRepository.findOverdueByUserId(currentUserId, now, pageable);
        } else {
            page = circulationRepository.findByUserId(currentUserId, pageable);
        }

        return PageResponse.from(page, c -> {
            PhysicalCopy copy = c.getPhysicalCopy();
            Document doc = copy.getDocument();

            String loanStatus;
            if (c.getReturnDate() != null) {
                loanStatus = (c.getDueDate() != null && c.getReturnDate().isAfter(c.getDueDate()))
                        ? "RETURNED_LATE"
                        : "RETURNED";
            } else {
                loanStatus = (c.getDueDate() != null && now.isAfter(c.getDueDate()))
                        ? "OVERDUE"
                        : "BORROWING";
            }

            Long fineSum = circulationFineRepository.sumFineAmountByCirculationId(c.getId());

            return CirculationHistoryItemDto.builder()
                    .circulationId(c.getId())
                    .documentId(doc.getId())
                    .documentTitle(doc.getTitle())
                    .barcode(copy.getBarcode())
                    .callNumber(copy.getCallNumber())
                    .shelfLocation(copy.getShelfLocation())
                    .borrowDate(c.getBorrowDate())
                    .dueDate(c.getDueDate())
                    .returnDate(c.getReturnDate())
                    .renewalsCount(c.getRenewalsCount() != null ? c.getRenewalsCount() : 0)
                    .status(loanStatus)
                    .fineAmount(fineSum != null ? fineSum : 0L)
                    .build();
        });
    }

    @Override
    @Transactional
    public HoldResponseDto placeHold(HoldRequestDto request) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        long availableCopies = physicalCopyRepository.countByDocumentIdAndStatus(
                document.getId(), PhysicalCopyStatus.AVAILABLE.name());
        if (availableCopies > 0) {
            throw new AppException(ErrorCode.HOLD_NOT_ALLOWED_WHEN_AVAILABLE);
        }

        if (circulationRepository.isUserCurrentlyBorrowingDocument(userId, document.getId())) {
            throw new AppException(ErrorCode.ALREADY_BORROWING_DOCUMENT);
        }

        if (holdQueueRepository.findActiveHoldByUserAndDocument(userId, document.getId()).isPresent()) {
            throw new AppException(ErrorCode.HOLD_ALREADY_EXISTS);
        }

        int nextPosition = holdQueueRepository.getNextQueuePosition(document.getId());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime estimatedAvailableDate = calculateEstimatedAvailableDate(document.getId(), nextPosition, now);

        HoldQueue hold = HoldQueue.builder()
                .user(user)
                .document(document)
                .queuePosition(nextPosition)
                .requestDate(now)
                .status(HoldStatus.WAITING.name())
                .build();

        HoldQueue savedHold = holdQueueRepository.save(hold);

        return HoldResponseDto.builder()
                .holdId(savedHold.getId())
                .documentId(document.getId())
                .documentTitle(document.getTitle())
                .queuePosition(nextPosition)
                .requestDate(now)
                .estimatedAvailableDate(estimatedAvailableDate)
                .status(savedHold.getStatus())
                .build();
    }

    @Override
    @Transactional
    public HoldCancelResponseDto cancelHold(Long holdId, String cancelReason) {
        HoldQueue hold = holdQueueRepository.findById(holdId)
                .orElseThrow(() -> new AppException(ErrorCode.HOLD_NOT_FOUND));

        CustomUserDetails currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        boolean isStaff = "LIBRARIAN".equalsIgnoreCase(currentUser.getRole())
                || "ADMIN".equalsIgnoreCase(currentUser.getRole());

        if (!isStaff && !hold.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Bạn đọc không có quyền hủy yêu cầu đặt trước của người khác");
        }

        String currentStatus = hold.getStatus();
        if (!HoldStatus.WAITING.name().equalsIgnoreCase(currentStatus)
                && !HoldStatus.READY_FOR_PICKUP.name().equalsIgnoreCase(currentStatus)) {
            throw new AppException(ErrorCode.HOLD_CANNOT_CANCEL,
                    "Không thể hủy yêu cầu đặt trước đang ở trạng thái: " + currentStatus);
        }

        Integer oldPosition = hold.getQueuePosition();
        PhysicalCopy reservedCopy = hold.getPhysicalCopy();
        Document document = hold.getDocument();
        OffsetDateTime now = OffsetDateTime.now();

        hold.setStatus(HoldStatus.CANCELLED.name());
        hold.setCancelReason(StringUtils.hasText(cancelReason) ? cancelReason.trim() : "Hủy theo yêu cầu");
        hold.setPhysicalCopy(null);
        holdQueueRepository.save(hold);

        if (HoldStatus.WAITING.name().equalsIgnoreCase(currentStatus) && oldPosition != null) {
            holdQueueRepository.decrementQueuePositionsAfter(document.getId(), oldPosition);
        } else if (HoldStatus.READY_FOR_PICKUP.name().equalsIgnoreCase(currentStatus) && reservedCopy != null) {
            Optional<HoldQueue> nextWaitingOpt = holdQueueRepository
                    .findFirstByDocumentIdAndStatusOrderByQueuePositionAsc(document.getId(), HoldStatus.WAITING.name());

            if (nextWaitingOpt.isPresent()) {
                HoldQueue nextWaiting = nextWaitingOpt.get();
                Integer nextPos = nextWaiting.getQueuePosition();
                nextWaiting.setStatus(HoldStatus.READY_FOR_PICKUP.name());
                nextWaiting.setPhysicalCopy(reservedCopy);
                nextWaiting.setReadyDate(now);
                nextWaiting.setPickupDeadline(now.plusDays(DEFAULT_HOLD_EXPIRY_DAYS));
                holdQueueRepository.save(nextWaiting);

                if (nextPos != null) {
                    holdQueueRepository.decrementQueuePositionsAfter(document.getId(), nextPos);
                }
            } else {
                reservedCopy.setStatus(PhysicalCopyStatus.AVAILABLE.name());
                physicalCopyRepository.save(reservedCopy);
            }
        }

        return HoldCancelResponseDto.builder()
                .holdId(hold.getId())
                .documentId(document.getId())
                .documentTitle(document.getTitle())
                .status(HoldStatus.CANCELLED.name())
                .cancelReason(hold.getCancelReason())
                .cancelledAt(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserFinesSummaryDto getMyFines(String status) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        List<CirculationFine> allUserFines = circulationFineRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);

        long totalUnpaid = 0L;
        long totalPaid = 0L;
        int unpaidCount = 0;

        for (CirculationFine f : allUserFines) {
            long amount = f.getFineAmount() != null ? f.getFineAmount() : 0L;
            if (FineStatus.UNPAID.name().equalsIgnoreCase(f.getStatus())) {
                totalUnpaid += amount;
                unpaidCount++;
            } else if (FineStatus.PAID.name().equalsIgnoreCase(f.getStatus())) {
                totalPaid += amount;
            }
        }

        List<CirculationFine> filteredFines = StringUtils.hasText(status)
                ? allUserFines.stream()
                        .filter(f -> status.trim().equalsIgnoreCase(f.getStatus()))
                        .toList()
                : allUserFines;

        List<FineItemDto> fineItems = filteredFines.stream()
                .map(f -> {
                    PhysicalCopy copy = f.getCirculation().getPhysicalCopy();
                    Document doc = copy.getDocument();
                    return toFineItemDto(f, copy.getBarcode(), doc.getTitle());
                })
                .toList();

        return UserFinesSummaryDto.builder()
                .totalUnpaidAmount(totalUnpaid)
                .totalPaidAmount(totalPaid)
                .unpaidCount(unpaidCount)
                .fines(fineItems)
                .build();
    }

    @Override
    @Transactional
    public FinePaymentResponseDto payFine(Long fineId, String paymentNote) {
        CirculationFine fine = circulationFineRepository.findById(fineId)
                .orElseThrow(() -> new AppException(ErrorCode.FINE_NOT_FOUND));

        if (!FineStatus.UNPAID.name().equalsIgnoreCase(fine.getStatus())) {
            throw new AppException(ErrorCode.FINE_ALREADY_PAID);
        }

        fine.setStatus(FineStatus.PAID.name());
        if (StringUtils.hasText(paymentNote)) {
            String existingNote = StringUtils.hasText(fine.getNote()) ? fine.getNote() + " | " : "";
            fine.setNote(existingNote + paymentNote.trim());
        }

        CirculationFine saved = circulationFineRepository.save(fine);
        Circulation circulation = saved.getCirculation();
        User reader = circulation.getUser();
        Document document = circulation.getPhysicalCopy().getDocument();

        return FinePaymentResponseDto.builder()
                .fineId(saved.getId())
                .circulationId(circulation.getId())
                .userCode(reader.getUserCode())
                .fullName(reader.getFullName())
                .documentTitle(document.getTitle())
                .fineAmount(saved.getFineAmount() != null ? saved.getFineAmount() : 0L)
                .status(saved.getStatus())
                .paidAt(saved.getUpdatedAt())
                .note(saved.getNote())
                .build();
    }

    private PenaltyType getOrCreatePenaltyType(String code, String defaultName, String unitName, long defaultPrice) {
        return penaltyTypeRepository.findByCode(code)
                .orElseGet(() -> penaltyTypeRepository.save(PenaltyType.builder()
                        .code(code)
                        .name(defaultName)
                        .unitName(unitName)
                        .unitPrice(defaultPrice)
                        .build()));
    }

    private OffsetDateTime calculateEstimatedAvailableDate(UUID documentId, int queuePosition, OffsetDateTime now) {
        List<Circulation> activeLoans = circulationRepository.findActiveLoansByDocumentIdOrderByDueDateAsc(documentId);
        if (!activeLoans.isEmpty()) {
            int count = activeLoans.size();
            int index = (queuePosition - 1) % count;
            int cycle = (queuePosition - 1) / count;

            OffsetDateTime baseDueDate = activeLoans.get(index).getDueDate();
            if (baseDueDate == null || baseDueDate.isBefore(now)) {
                baseDueDate = now.plusDays(3);
            }
            return baseDueDate.plusDays((long) cycle * DEFAULT_LOAN_DAYS);
        }
        return now.plusDays(7L * queuePosition);
    }

    private FineItemDto toFineItemDto(CirculationFine fine, String barcode, String documentTitle) {
        PenaltyType pt = fine.getPenaltyType();
        String reason = pt != null && StringUtils.hasText(pt.getName()) ? pt.getName() : "Phí phạt lưu thông";
        if (StringUtils.hasText(fine.getNote())) {
            reason = reason + " (" + fine.getNote() + ")";
        }

        return FineItemDto.builder()
                .fineId(fine.getId())
                .circulationId(fine.getCirculation().getId())
                .barcode(barcode)
                .documentTitle(documentTitle)
                .penaltyCode(pt != null ? pt.getCode() : null)
                .reason(reason)
                .quantity(fine.getQuantity() != null ? fine.getQuantity() : 1)
                .unitPrice(pt != null && pt.getUnitPrice() != null ? pt.getUnitPrice() : 0L)
                .fineAmount(fine.getFineAmount() != null ? fine.getFineAmount() : 0L)
                .status(fine.getStatus())
                .note(fine.getNote())
                .createdAt(fine.getCreatedAt())
                .updatedAt(fine.getUpdatedAt())
                .build();
    }
}
