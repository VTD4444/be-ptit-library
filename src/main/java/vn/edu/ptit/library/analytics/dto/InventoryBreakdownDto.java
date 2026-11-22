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
public class InventoryBreakdownDto {

    private long available;
    private long borrowed;
    private long reserved;
    private long maintenance;
    private long damaged;
    private long lost;
}
