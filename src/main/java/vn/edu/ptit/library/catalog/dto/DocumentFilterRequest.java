package vn.edu.ptit.library.catalog.dto;

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
public class DocumentFilterRequest {

    private String keyword;
    private Long departmentId;
    private String docType;
    private Long authorId;
    private Integer publicationYear;
}
