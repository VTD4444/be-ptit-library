package vn.edu.ptit.library.circulation.dto;

import jakarta.validation.constraints.*;
import vn.edu.ptit.library.common.enums.UserType;

public record PolicyUpdateRequest(
        @NotNull UserType userType,
        @NotBlank String docType,
        @NotNull @Positive Integer loanDays,
        @NotNull @Positive Integer maxLoans,
        @NotNull @PositiveOrZero Integer maxRenewals,
        @NotNull @PositiveOrZero Integer holdExpiryDays) {
    public PolicyUpdateRequest {
        docType = docType == null ? null : docType.trim();
    }
}
