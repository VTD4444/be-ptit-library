package vn.edu.ptit.library.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequestDto {

    @NotNull(message = "Danh gia sao khong duoc de trong")
    @Min(value = 1, message = "Danh gia thap nhat la 1 sao")
    @Max(value = 5, message = "Danh gia cao nhat la 5 sao")
    private Integer rating;

    private String comment;
}
