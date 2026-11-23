package vn.edu.ptit.library.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import vn.edu.ptit.library.catalog.dto.DocumentFilterRequest;
import vn.edu.ptit.library.catalog.repository.DocumentSpecification;
import vn.edu.ptit.library.search.repository.ContentSearchRepository;
import vn.edu.ptit.library.search.repository.ContentSearchRepository.DocumentFilter;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchFilterTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void documentSpecificationHandlesAuthorNameAndLanguage() {
        assertThat(DocumentSpecification.hasAuthorName(null)).isNull();
        assertThat(DocumentSpecification.hasAuthorName("   ")).isNull();
        assertThat(DocumentSpecification.hasAuthorName("Nguyễn Văn A")).isNotNull();

        assertThat(DocumentSpecification.hasLanguage(null)).isNull();
        assertThat(DocumentSpecification.hasLanguage(" ")).isNull();
        assertThat(DocumentSpecification.hasLanguage("vi")).isNotNull();

        DocumentFilterRequest request = DocumentFilterRequest.builder()
                .keyword("mạng")
                .authorName("Nguyễn")
                .language("vi")
                .publicationYear(2024)
                .docType("textbook")
                .departmentId(1L)
                .build();
        assertThat(DocumentSpecification.filter(request)).isNotNull();
    }

    @Test
    void contentSearchDocumentFilterAppliesAuthorNameAndPublicationYearInSql() {
        DocumentFilter empty = new DocumentFilter(null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        DocumentFilter withAuthor = new DocumentFilter("vi", "textbook", 2L, "  Trần Minh  ", 2024);
        assertThat(withAuthor.isEmpty()).isFalse();

        ContentSearchRepository repository = new ContentSearchRepository(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of());

        repository.findDocumentIds(withAuthor);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), paramsCaptor.capture(), eq(UUID.class));

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat(sql).contains("public.document_authors");
        assertThat(sql).contains("public.authors");
        assertThat(sql).contains("d.publication_year = :publicationYear");
        assertThat(params.getValue("authorNamePattern")).isEqualTo("%Trần Minh%");
        assertThat(params.getValue("publicationYear")).isEqualTo(2024);
        assertThat(params.getValue("language")).isEqualTo("vi");
        assertThat(params.getValue("docType")).isEqualTo("textbook");
        assertThat(params.getValue("departmentId")).isEqualTo(2L);
    }
}
