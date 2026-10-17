package vn.edu.ptit.library.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentSearchResultDto {

    private UUID documentId;
    private String documentTitle;
    private UUID chunkId;
    private String chunkText;
    private Integer pageNumber;
    private String chapter;
    private String section;
    private Double relevanceScore;

    // Thong tin ban giay phuc vu muon sach truc tiep (Muc 2.3 c trong De cuong)
    private long availablePhysicalCopies;
    private String shelfLocation;
}
