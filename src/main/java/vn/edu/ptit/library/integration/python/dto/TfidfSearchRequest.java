package vn.edu.ptit.library.integration.python.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collection;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TfidfSearchRequest {

    // Cau hoi / doan van, hoac truy van Boolean: AND, OR, NOT, "cum tu", ( )
    private String query;
    private int limit;

    // true: moi tai lieu chi lay doan khop nhat; false: tat ca doan khop (vi tri trong mot tai lieu)
    private boolean bestPerDocument;

    // null => tim tren toan kho; co gia tri => chi tim trong nhung tai lieu nay
    private Collection<UUID> documentIds;

    private double minScore;
    private int maxLocations;
}
