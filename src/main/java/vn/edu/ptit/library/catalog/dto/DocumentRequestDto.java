package vn.edu.ptit.library.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class DocumentRequestDto {

    @NotBlank(message = "Tieu de khong duoc de trong")
    private String title;

    private String isbn;
    private List<String> authors;
    private String publisher;

    @Min(value = 1000, message = "Nam xuat ban khong hop le")
    @Max(value = 9999, message = "Nam xuat ban khong hop le")
    private Integer publicationYear;

    private String abstractText;
    private String docType;
    private String classificationNumber;
    private String language;
    private String coverUrl;
    private List<Long> departmentIds;
}
