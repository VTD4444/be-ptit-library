package vn.edu.ptit.library.integration.python;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.integration.python.dto.EmbeddingRequest;
import vn.edu.ptit.library.integration.python.dto.EmbeddingResponse;
import vn.edu.ptit.library.integration.python.dto.PdfIndexRequest;

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

    public EmbeddingResponse generateEmbedding(String text, String modelName) {
        try {
            return restClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EmbeddingRequest.builder().text(text).modelName(modelName).build())
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientException ex) {
            log.error("Loi khi sinh vector embedding tu Python Worker: {}", ex.getMessage());
            throw new AppException(ErrorCode.PYTHON_WORKER_ERROR, "Không thể sinh vector ngữ nghĩa cho văn bản: " + ex.getMessage());
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
