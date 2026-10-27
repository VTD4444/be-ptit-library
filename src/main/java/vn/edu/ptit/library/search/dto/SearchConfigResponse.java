package vn.edu.ptit.library.search.dto;

import vn.edu.ptit.library.search.entity.SearchVectorConfig;

public record SearchConfigResponse(Long id, Integer chunkSize, Integer overlap, String strategy,
                                   String embeddingModel, Integer vectorDimensions, Boolean isActive) {
    public static SearchConfigResponse from(SearchVectorConfig config) {
        return new SearchConfigResponse(config.getId(), config.getChunkSize(), config.getOverlap(),
                config.getStrategy(), config.getEmbeddingModel(), config.getVectorDimensions(), config.getIsActive());
    }
}
