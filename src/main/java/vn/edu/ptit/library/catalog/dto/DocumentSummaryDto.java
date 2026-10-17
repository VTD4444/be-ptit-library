package vn.edu.ptit.library.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSummaryDto {

    private UUID id;
    private String title;
    private String isbn;
    private String publisher;
    private Integer publicationYear;
    private String language;
    private String docType;
    private String classificationNumber;
    private List<String> authors;
    private long totalPhysicalCopies;
    private long availablePhysicalCopies;
    private boolean hasDigitalCopy;
    private Double averageRating;
}
