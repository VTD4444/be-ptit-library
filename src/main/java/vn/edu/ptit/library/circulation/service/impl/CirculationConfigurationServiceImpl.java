package vn.edu.ptit.library.circulation.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.circulation.dto.*;
import vn.edu.ptit.library.circulation.entity.PenaltyType;
import vn.edu.ptit.library.circulation.repository.LoanPolicyRepository;
import vn.edu.ptit.library.circulation.repository.PenaltyTypeRepository;
import vn.edu.ptit.library.circulation.service.CirculationConfigurationService;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CirculationConfigurationServiceImpl implements CirculationConfigurationService {
    private final LoanPolicyRepository policyRepository;
    private final PenaltyTypeRepository penaltyRepository;

    @Override
    public List<PolicyResponse> getPolicies() {
        return policyRepository.findAll(Sort.by("id")).stream().map(PolicyResponse::from).toList();
    }

    @Override
    @Transactional
    public PolicyResponse updatePolicy(Long id, PolicyUpdateRequest request) {
        var policy = policyRepository.findById(id).orElseThrow(() ->
                new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay chinh sach muon tra"));
        policy.setUserType(request.userType().name());
        policy.setDocType(request.docType());
        policy.setLoanDays(request.loanDays());
        policy.setMaxLoans(request.maxLoans());
        policy.setMaxRenewals(request.maxRenewals());
        policy.setHoldExpiryDays(request.holdExpiryDays());
        return PolicyResponse.from(policyRepository.save(policy));
    }

    @Override
    public List<PenaltyTypeResponse> getPenaltyTypes() {
        return penaltyRepository.findAll(Sort.by("id")).stream().map(PenaltyTypeResponse::from).toList();
    }

    @Override
    @Transactional
    public PenaltyTypeResponse createPenaltyType(PenaltyTypeCreateRequest request) {
        checkCode(request.code(), null);
        var type = PenaltyType.builder().code(request.code()).name(request.name())
                .unitName(request.unitName()).unitPrice(request.unitPrice()).build();
        return PenaltyTypeResponse.from(penaltyRepository.save(type));
    }

    @Override
    @Transactional
    public PenaltyTypeResponse updatePenaltyType(PenaltyTypeUpdateRequest request) {
        var type = penaltyRepository.findById(request.id()).orElseThrow(() ->
                new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay loai phat"));
        checkCode(request.code(), request.id());
        type.setCode(request.code());
        type.setName(request.name());
        type.setUnitName(request.unitName());
        type.setUnitPrice(request.unitPrice());
        return PenaltyTypeResponse.from(penaltyRepository.save(type));
    }

    private void checkCode(String code, Long currentId) {
        // This check does not replace a database unique constraint under concurrent writes.
        penaltyRepository.findByCode(code).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), currentId)) {
                throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Ma loai phat da ton tai");
            }
        });
    }
}
