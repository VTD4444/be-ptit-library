package vn.edu.ptit.library.integration.python.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimilarRebuildResponse {

    private String modelVersion;
    private int documentsProcessed;
    private int pairsSaved;
    private double durationMs;
}
