package vn.edu.ptit.library.integration.python.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EasePredictResponse {

    private boolean modelReady;
    private String modelVersion;
    private boolean personalizationEnabled;
    private List<EasePredictItemDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EasePredictItemDto {
        private UUID documentId;
        private double score;
        private int rank;
        private String reason;
        private String recModelVersion;
    }
}
