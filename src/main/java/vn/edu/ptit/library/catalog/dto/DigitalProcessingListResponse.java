package vn.edu.ptit.library.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.ptit.library.common.dto.PageResponse;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalProcessingListResponse {

    /** So ban so theo processing_status tren toan he thong (khong phu thuoc bo loc). */
    private Map<String, Long> statusCounts;
    private PageResponse<DigitalProcessingItemDto> page;
}
