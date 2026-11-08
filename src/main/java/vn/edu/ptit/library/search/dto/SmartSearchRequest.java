package vn.edu.ptit.library.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class SmartSearchRequest {

    // Doan van ban hoac cau hoi
    @NotBlank(message = "Cau truy van khong duoc de trong")
    @Size(max = 2000, message = "Cau truy van toi da 2000 ky tu")
    private String query;

    // Bo loc (tuy chon)
    private String language;
    private String docType;
    private Long departmentId;

    // Tuy chon: tfidf | vector | hybrid. Bo trong thi dung SEARCH_MODE cua worker
    private String searchMode;

    @Builder.Default
    @Min(value = 1, message = "limit toi thieu la 1")
    @Max(value = 50, message = "limit toi da la 50")
    private int limit = 10;
}
