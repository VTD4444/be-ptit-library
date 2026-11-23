package vn.edu.ptit.library.integration.python.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EaseRebuildRequest {

    private String modelVersion;
    private double regLambda;
    private int cacheTopK;
    private int cacheTtlHours;
    private boolean onlyAvailable;
}
