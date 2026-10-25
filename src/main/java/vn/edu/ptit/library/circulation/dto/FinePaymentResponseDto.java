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
public class FinePaymentResponseDto {

    private Long fineId;
    private Long circulationId;
    private String userCode;
    private String fullName;
    private String documentTitle;
    private long fineAmount;
    private String status;
    private OffsetDateTime paidAt;
    private String note;
}
