package vn.edu.ptit.library.search;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.library.search.util.SearchTextUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextUtilsTest {

    @Test
    void extractTermsLowercasesDropsStopwordsAndPunctuation() {
        List<String> terms = SearchTextUtils.extractTerms("Thuật toán của Học Máy là gì? (SVM, svm)");

        assertThat(terms).containsExactly("thuật", "toán", "học", "máy", "svm");
        assertThat(SearchTextUtils.toTsQuery(terms)).isEqualTo("thuật | toán | học | máy | svm");
    }

    @Test
    void extractTermsKeepsStopwordsWhenQueryHasNothingElse() {
        assertThat(SearchTextUtils.extractTerms("là của")).containsExactly("là", "của");
        assertThat(SearchTextUtils.toTsQuery(SearchTextUtils.extractTerms("  ?! "))).isNull();
    }

    @Test
    void snippetHighlightsWholeWordsAndEscapesHtml() {
        String text = "Mở đầu <script>x</script>. Mạng nơ-ron tích chập được dùng cho ảnh; mạnghơn không khớp.";

        String snippet = SearchTextUtils.buildSnippet(text, List.of("mạng", "ảnh"), 300);

        assertThat(snippet)
                .contains("&lt;script&gt;")
                .doesNotContain("<script>")
                .contains("<mark>Mạng</mark> nơ-ron")
                .contains("<mark>ảnh</mark>")
                .contains("mạnghơn");
    }

    @Test
    void snippetCentresOnDensestMatchWindow() {
        String filler = "lorem ".repeat(100);
        String text = filler + "học máy và học sâu" + " " + filler;

        String snippet = SearchTextUtils.buildSnippet(text, List.of("học", "máy", "sâu"), 120);

        assertThat(snippet).startsWith("… ").endsWith(" …").contains("<mark>sâu</mark>");
    }
}
