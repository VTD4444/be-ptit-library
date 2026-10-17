package vn.edu.ptit.library.analytics.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.entity.AcquisitionSuggestion;
import vn.edu.ptit.library.analytics.repository.AcquisitionSuggestionRepository;
import vn.edu.ptit.library.analytics.service.AnalyticsService;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.enums.AcquisitionStatus;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final DocumentRepository documentRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DigitalCopyRepository digitalCopyRepository;
    private final UserRepository userRepository;
    private final CirculationRepository circulationRepository;
    private final AcquisitionSuggestionRepository acquisitionSuggestionRepository;

    @Override
    @Transactional(readOnly = true)
    public LibraryOverviewStatsDto getOverviewStatistics() {
        long totalDocs = documentRepository.count();
        long totalCopies = physicalCopyRepository.count();
        long availCopies = physicalCopyRepository.count(); // fallback or filter
        long totalDigital = digitalCopyRepository.count();
        long totalUsers = userRepository.count();
        long activeLoans = circulationRepository.count();
        long pendingSuggestions = acquisitionSuggestionRepository.countByStatus(AcquisitionStatus.PENDING.name());

        return LibraryOverviewStatsDto.builder()
                .totalDocuments(totalDocs)
                .totalPhysicalCopies(totalCopies)
                .availablePhysicalCopies(availCopies)
                .borrowedPhysicalCopies(totalCopies - availCopies > 0 ? totalCopies - availCopies : 0)
                .totalDigitalCopies(totalDigital)
                .totalUsers(totalUsers)
                .activeLoans(activeLoans)
                .pendingAcquisitionSuggestions(pendingSuggestions)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AcquisitionSuggestion> getAcquisitionSuggestions(String status, PaginationRequest request) {
        Page<AcquisitionSuggestion> page;
        if (StringUtils.hasText(status)) {
            page = acquisitionSuggestionRepository.findByStatus(status.trim().toUpperCase(), request.toPageable());
        } else {
            page = acquisitionSuggestionRepository.findAll(request.toPageable());
        }
        return PageResponse.from(page);
    }
}
