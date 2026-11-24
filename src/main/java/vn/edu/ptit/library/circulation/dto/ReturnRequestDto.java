package vn.edu.ptit.library.circulation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.ptit.library.common.enums.BookReturnCondition;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestDto {

    @NotBlank(message = "Ma vach cuon sach (barcode) khong duoc de trong")
    private String barcode;

    @Builder.Default
    private BookReturnCondition condition = BookReturnCondition.NORMAL;

    private String returnNote;

    private Long additionalPenaltyTypeId;

    @Min(value = 0, message = "So tien phat bo sung khong duoc am")
    private Long additionalFineAmount;

    private String additionalFineNote;
}
