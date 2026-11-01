package vn.edu.ptit.library.integration.python.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.ptit.library.search.dto.TextLocationDto;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TfidfSearchResponse {

    private boolean booleanQuery;
    // Sap xep theo diem giam dan
    private List<Passage> passages;
    private int indexedChunks;

    /**
     * Doan van ban thong nhat: cac chunk goi nhau cung khop duoc gop lai thanh mot khoang tu [wordStart, wordEnd).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Passage {
        private UUID documentId;
        private UUID digitalCopyId;
        private List<UUID> chunkIds;
        // Do tuong dong cosine TF-IDF cao nhat trong cac chunk cua doan, trong [0, 1]
        private double score;
        private Integer wordStart;
        private Integer wordEnd;
        private Integer pageStart;
        private Integer pageEnd;
        private String chapter;
        private String section;
        // Van ban thuan (chua escape) quanh vung khop
        private String snippet;
        private List<TextLocationDto> locations;
        // Cac am tiet thuc su khop trong van ban, dung de boc <mark> cho trich doan
        private List<String> highlightTerms;
    }
}
