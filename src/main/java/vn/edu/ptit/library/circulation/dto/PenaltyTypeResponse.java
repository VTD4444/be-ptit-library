package vn.edu.ptit.library.circulation.dto;

import vn.edu.ptit.library.circulation.entity.PenaltyType;

public record PenaltyTypeResponse(Long id, String code, String name, String unitName, Long unitPrice) {
    public static PenaltyTypeResponse from(PenaltyType type) {
        return new PenaltyTypeResponse(type.getId(), type.getCode(), type.getName(), type.getUnitName(), type.getUnitPrice());
    }
}
