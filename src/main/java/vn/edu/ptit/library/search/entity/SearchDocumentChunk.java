package vn.edu.ptit.library.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.common.audit.BaseCreatedEntity;

import java.util.UUID;

@Entity
@Table(name = "search_document_chunks", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocumentChunk extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "digital_copy_id", nullable = false)
    private DigitalCopy digitalCopy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_config_id")
    private SearchVectorConfig searchConfig;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "chapter")
    private String chapter;

    @Column(name = "section")
    private String section;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "printed_page_label")
    private String printedPageLabel;

    @Column(name = "chunk_text", columnDefinition = "text")
    private String chunkText;

    @Column(name = "token_count")
    private Integer tokenCount;
}
