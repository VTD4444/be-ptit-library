package vn.edu.ptit.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${app.python-worker.base-url:http://localhost:8000}")
    private String pythonWorkerBaseUrl;

    @Value("${app.python-worker.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean(name = "pythonWorkerRestClient")
    public RestClient pythonWorkerRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .baseUrl(pythonWorkerBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
