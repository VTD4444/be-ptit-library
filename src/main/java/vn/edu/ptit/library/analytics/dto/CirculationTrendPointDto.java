package vn.edu.ptit.library.analytics.dto;

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
public class CirculationTrendPointDto {

    private String label;
    private String date;
    private long borrowCount;
    private long returnCount;
    private long overdueCount;
}
