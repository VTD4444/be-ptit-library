package vn.edu.ptit.library.integration.python.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponse {

    private List<Double> embedding;
    private int dimensions;
    private String modelName;

    /**
     * Chuyen danh sach vector sang chuoi JSON array hop le de truyen vao CAST(:queryVector AS vector) cua PostgreSQL pgvector.
     * Vi du: "[0.0123,-0.0456,...]"
     */
    public String toPgVectorString() {
        if (embedding == null || embedding.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
