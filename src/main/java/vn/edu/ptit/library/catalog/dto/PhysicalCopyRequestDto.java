package vn.edu.ptit.library.catalog.dto;

import jakarta.validation.constraints.NotBlank;
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
public class PhysicalCopyRequestDto {

    @NotNull(message = "ID dau sach goc khong duoc de trong")
    private UUID documentId;

    @NotBlank(message = "Ma vach ca biet khong duoc de trong")
    private String barcode;

    private String callNumber;
    private String shelfLocation;
    private String status;
}
