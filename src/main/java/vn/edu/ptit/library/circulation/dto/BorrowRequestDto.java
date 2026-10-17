package vn.edu.ptit.library.circulation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowRequestDto {

    @NotNull(message = "Ma dinh danh ban sach vat ly khong duoc de trong")
    private UUID physicalCopyId;

    @NotNull(message = "Ma dinh danh ban doc khong duoc de trong")
    private UUID userId;

    private String borrowNote;
}
