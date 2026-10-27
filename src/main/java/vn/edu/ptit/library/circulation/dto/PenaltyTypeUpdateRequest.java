package vn.edu.ptit.library.circulation.dto;

import jakarta.validation.constraints.*;

public record PenaltyTypeUpdateRequest(
        @NotNull @Positive Long id,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 50) String unitName,
        @NotNull @PositiveOrZero Long unitPrice) {
    public PenaltyTypeUpdateRequest {
        code = code == null ? null : code.trim();
        name = name == null ? null : name.trim();
        unitName = unitName == null ? null : unitName.trim();
    }
}
