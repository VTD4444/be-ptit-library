package vn.edu.ptit.library.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationItemDto {

    private UUID documentId;
    private String title;
    private String docType;
    private Double score;
    private Integer rank;
    private String reason;
    private String recModelVersion;
}
