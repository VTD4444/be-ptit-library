package vn.edu.ptit.library.circulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class BorrowRequestDto {

    @NotBlank(message = "Ma ban doc (userCode) khong duoc de trong")
    private String userCode;

    @NotEmpty(message = "Danh sach ma vach sach can muon (barcodes) khong duoc de trong")
    private List<@NotBlank(message = "Ma vach sach khong duoc de trong") String> barcodes;

    private String borrowNote;
}
