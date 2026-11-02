package vn.edu.ptit.library.search.dto;

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
public class DocumentMatchesResponse {

    private UUID documentId;
    private String query;
    private String searchMode;
    private boolean booleanQuery;
    private List<String> highlightTerms;
    private List<Match> matches;

    /**
     * Mot doan khop: cac chunk goi nhau (overlap) da duoc gop thanh mot khoang tu thong nhat,
     * nen moi vi tri chi xuat hien mot lan du nam trong vung goi cua hai chunk.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Match {
        private List<UUID> chunkIds;
        private double matchScore;
        private Integer pageNumber;
        private Integer pageEnd;
        private String chapter;
        private String section;
        // Khoang tu [wordStart, wordEnd) cua doan trong dong tu chuan cua ban so
        private Integer wordStart;
        private Integer wordEnd;
        private String snippet;

        // Vi tri chinh xac tung tu/cum tu khop (trang, doan, cau, toa do) de highlight khi doc
        private List<TextLocationDto> locations;
    }
}
