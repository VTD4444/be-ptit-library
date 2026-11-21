package vn.edu.ptit.library.integration.python;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.integration.python.dto.EasePredictRequest;
import vn.edu.ptit.library.integration.python.dto.EasePredictResponse;
import vn.edu.ptit.library.integration.python.dto.EaseRebuildRequest;
import vn.edu.ptit.library.integration.python.dto.EaseRebuildResponse;
import vn.edu.ptit.library.integration.python.dto.PdfIndexRequest;
import vn.edu.ptit.library.integration.python.dto.SimilarRebuildRequest;
import vn.edu.ptit.library.integration.python.dto.SimilarRebuildResponse;
import vn.edu.ptit.library.integration.python.dto.TfidfSearchRequest;
import vn.edu.ptit.library.integration.python.dto.TfidfSearchResponse;

import java.util.Map;

@Slf4j
@Component
public class PythonWorkerClient {

    private final RestClient restClient;

    public PythonWorkerClient(@Qualifier("pythonWorkerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean requestPdfIndexing(PdfIndexRequest request) {
        try {
            restClient.post()
                    .uri("/api/extract-index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.error("Loi khi goi sang Python Worker bóc tách PDF: {}", ex.getMessage());
            throw new AppException(ErrorCode.PYTHON_WORKER_ERROR, "Không thể kết nối tới dịch vụ bóc tách PDF: " + ex.getMessage());
        }
    }

    /**
     * Tim kiem tren Python Worker: loc Boolean (inverted file) + xep hang TF-IDF/cosine,
     * gop cac chunk goi nhau thanh doan thong nhat va dinh vi tu khop (positional index).
     */
    public TfidfSearchResponse searchTfidf(TfidfSearchRequest request) {
        try {
            return restClient.post()
                    .uri("/api/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TfidfSearchResponse.class);
        } catch (RestClientException ex) {
            log.error("Loi khi tim kiem TF-IDF tren Python Worker: {}", ex.getMessage());
            throw new AppException(ErrorCode.PYTHON_WORKER_ERROR, "Không thể tìm kiếm trên dịch vụ TF-IDF: " + ex.getMessage());
        }
    }

    /**
     * Yeu cau Python Worker tinh lai ma tran do tuong dong noi dung (TF-IDF Weighted) va luu vao DB.
     */
    public SimilarRebuildResponse rebuildSimilarBooks(SimilarRebuildRequest request) {
        try {
            return restClient.post()
                    .uri("/api/recommendations/similar/rebuild")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SimilarRebuildResponse.class);
        } catch (RestClientException ex) {
            log.error("Loi khi tinh lai sach tuong tu tren Python Worker: {}", ex.getMessage());
            throw new AppException(ErrorCode.PYTHON_WORKER_ERROR,
                    "Không thể tính toán sách tương tự trên Python Worker: " + ex.getMessage());
        }
    }

    /**
     * Yeu cau Python Worker huan luyen lai ma tran EASE va luu snapshot vao recommendations_cache.
     */
    public EaseRebuildResponse rebuildPersonalEase(EaseRebuildRequest request) {
        try {
            return restClient.post()
                    .uri("/api/recommendations/me/rebuild")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EaseRebuildResponse.class);
        } catch (RestClientException ex) {
            log.error("Loi khi huan luyen lai EASE tren Python Worker: {}", ex.getMessage());
            throw new AppException(ErrorCode.PYTHON_WORKER_ERROR,
                    "Không thể huấn luyện mô hình gợi ý cá nhân hóa trên Python Worker: " + ex.getMessage());
        }
    }

    /**
     * Goi Python Worker cham diem Online Scoring cho 1 ban doc dua tren ma tran B trong RAM.
     */
    public EasePredictResponse predictPersonalEase(EasePredictRequest request) {
        try {
            return restClient.post()
                    .uri("/api/recommendations/me/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EasePredictResponse.class);
        } catch (RestClientException ex) {
            log.warn("Khong goi duoc Online Scoring tren Python Worker: {}", ex.getMessage());
            throw new AppException(ErrorCode.PYTHON_WORKER_ERROR,
                    "Không thể gọi gợi ý cá nhân hóa từ Python Worker: " + ex.getMessage());
        }
    }

    public boolean isWorkerHealthy() {
        try {
            Map<?, ?> response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            return response != null && "ok".equalsIgnoreCase(String.valueOf(response.get("status")));
        } catch (Exception ex) {
            log.warn("Python AI Worker hien khong san sang: {}", ex.getMessage());
            return false;
        }
    }
}
