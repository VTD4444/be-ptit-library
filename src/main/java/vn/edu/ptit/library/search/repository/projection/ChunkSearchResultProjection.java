package vn.edu.ptit.library.search.repository.projection;

import java.util.UUID;

public interface ChunkSearchResultProjection {

    UUID getId();

    UUID getDocumentId();

    String getTitle();

    String getChunkText();

    Integer getPageNumber();

    String getChapter();

    String getSection();

    Double getSemanticScore();
}
