package vn.edu.ptit.library.integration.python.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EaseRebuildResponse {

    private String modelVersion;
    private double regLambda;
    private int usersCount;
    private int documentsCount;
    private int positivePairs;
    private int cachedRows;
    private double durationMs;
}
