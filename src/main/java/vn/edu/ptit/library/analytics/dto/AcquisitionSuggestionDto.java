package vn.edu.ptit.library.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquisitionSuggestionDto {

    private Long id;
    private Long departmentId;
    private String departmentName;
    private String topic;
    private Map<String, Object> sourceQueryCluster;
    private Integer queryCount;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
