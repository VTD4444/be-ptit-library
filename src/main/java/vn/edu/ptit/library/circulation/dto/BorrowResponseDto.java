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
public class BorrowResponseDto {

    private String userCode;
    private String fullName;
    private int totalBorrowed;
    private long currentActiveLoans;
    private List<BorrowedItemDto> items;
    private List<String> warnings;
}
