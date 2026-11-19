package vn.edu.ptit.library.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteDocumentDto {

    private UUID id;
    private UUID documentId;
    private String title;
    private List<String> authors;
    private String coverUrl;
    private String abstractText;
    private String docType;
    private Integer publicationYear;

    private long totalPhysicalCopies;
    private long availablePhysicalCopies;
    private boolean hasDigitalCopy;
    private Double averageRating;
    private OffsetDateTime favoritedAt;
}
