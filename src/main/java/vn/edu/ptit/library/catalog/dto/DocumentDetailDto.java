package vn.edu.ptit.library.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDetailDto {

    private UUID id;
    private String title;
    private String isbn;
    private List<String> authors;
    private String publisher;
    private Integer publicationYear;
    private String language;
    private String docType;
    private String classificationNumber;
    private String abstractText;
    private String coverUrl;
    private Map<String, Object> metadata;

    private List<Long> departmentIds;
    private List<String> departmentNames;

    private long totalPhysicalCopies;
    private long availablePhysicalCopies;

    private boolean hasDigitalCopy;
    private UUID digitalCopyId;
    private String digitalProcessingStatus;
    private Integer pageCount;

    private Double averageRating;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
