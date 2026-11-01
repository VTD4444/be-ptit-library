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
public class SmartSearchResponse {

    private String query;

    // TFIDF: TF-IDF + cosine tren Python Worker; KEYWORD_ONLY: Worker khong san sang, du phong Full-Text Search
    private String searchMode;
    // Truy van co dung toan tu Boolean (AND, OR, NOT, "cum tu") de loc tai lieu
    private boolean booleanQuery;
    private List<String> highlightTerms;
    private List<Item> results;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private UUID documentId;
        private String title;
        private String docType;
        private String language;
        private Integer publicationYear;

        // Diem khop noi dung (%) = do tuong dong cosine x 100, lam tron 1 chu so thap phan
        private double matchScore;

        // Trich doan chua ngu canh khop nhat (HTML da escape, tu khoa boc trong <mark>)
        private String snippet;

        // Doan khop nhat (cac chunk goi nhau da duoc gop) va vi tri cua no
        private UUID digitalCopyId;
        private List<UUID> chunkIds;
        private Integer pageNumber;
        private Integer pageEnd;
        private String chapter;
        private String section;
        private List<TextLocationDto> locations;
    }
}
