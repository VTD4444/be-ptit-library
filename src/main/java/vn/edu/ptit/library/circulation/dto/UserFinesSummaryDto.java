package vn.edu.ptit.library.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFinesSummaryDto {

    private long totalUnpaidAmount;
    private long totalPaidAmount;
    private int unpaidCount;
    private List<FineItemDto> fines;
}
