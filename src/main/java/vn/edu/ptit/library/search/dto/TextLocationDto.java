package vn.edu.ptit.library.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Vi tri chinh xac cua mot tu/cum tu khop trong tai lieu (positional index - Lecture 8):
 * trang, doan, cau, vi tri tu va toa do de highlight tren trinh doc PDF.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextLocationDto {

    private int page;
    // So trang in tren sach (vd: "xii", "15") neu PDF co page label
    private String printedPageLabel;
    // Doan va cau, dem tu 1 tren toan tai lieu
    private int paragraph;
    private int sentence;
    // Vi tri tu (bao gom ca hai dau) trong dong tu chuan cua ban so
    private int wordStart;
    private int wordEnd;
    private String text;

    // Moi phan tu [x0, y0, x1, y1] theo don vi diem PDF, goc toa do o goc tren-trai trang;
    // chia cho pageWidth/pageHeight de ra ty le khi ve len canvas
    private List<List<Double>> rects;
    private double pageWidth;
    private double pageHeight;
}
