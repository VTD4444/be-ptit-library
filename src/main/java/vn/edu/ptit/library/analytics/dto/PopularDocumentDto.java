package vn.edu.ptit.library.analytics.dto;

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
public class PopularDocumentDto {

    private int rank;
    private UUID documentId;
    private String title;
    private List<String> authors;
    private String docType;
    private Integer publicationYear;
    private String coverUrl;
    private long totalPhysicalCopies;
    private long availablePhysicalCopies;
    private long borrowCount;
    private long favoriteCount;
    private long reviewCount;
    private Double averageRating;
    private long interactionCount;
}
