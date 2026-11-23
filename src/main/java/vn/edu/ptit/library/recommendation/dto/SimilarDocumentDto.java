package vn.edu.ptit.library.recommendation.dto;

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
public class SimilarDocumentDto {

    private UUID documentId;
    private String title;
    private List<String> authors;
    private String coverUrl;
    private String abstractText;
    private String docType;
    private String classificationNumber;
    private Integer publicationYear;
    private long totalPhysicalCopies;
    private long availablePhysicalCopies;
    private boolean hasDigitalCopy;
    private double similarityScore;
    private String modelVersion;
    private String source;
}
