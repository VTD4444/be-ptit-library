package vn.edu.ptit.library.search.dto;

import jakarta.validation.constraints.NotBlank;
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
public class ContentSearchRequest {

    @NotBlank(message = "Noi dung doan van truy van khong duoc de trong")
    private String query;

    @Builder.Default
    private int limit = 10;

    @Builder.Default
    private String searchType = "HYBRID"; // VECTOR, BM25, HYBRID
}
