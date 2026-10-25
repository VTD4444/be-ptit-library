package vn.edu.ptit.library.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewResponseDto {

    private Long circulationId;
    private String barcode;
    private String documentTitle;
    private OffsetDateTime oldDueDate;
    private OffsetDateTime newDueDate;
    private int renewalsCount;
    private int maxRenewals;
    private String renewalStatus;
    private String message;
}
