package vn.edu.ptit.library.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhysicalCopyDto {

    private UUID id;
    private UUID documentId;
    private String barcode;
    private String callNumber;
    private String shelfLocation;
    private String status;
    private OffsetDateTime accessionDate;
    private OffsetDateTime lastInventoryDate;
}
