package vn.edu.ptit.library.circulation.dto;

import vn.edu.ptit.library.circulation.entity.LoanPolicy;

public record PolicyResponse(Long id, String userType, String docType, Integer loanDays,
                             Integer maxLoans, Integer maxRenewals, Integer holdExpiryDays) {
    public static PolicyResponse from(LoanPolicy policy) {
        return new PolicyResponse(policy.getId(), policy.getUserType(), policy.getDocType(),
                policy.getLoanDays(), policy.getMaxLoans(), policy.getMaxRenewals(), policy.getHoldExpiryDays());
    }
}
