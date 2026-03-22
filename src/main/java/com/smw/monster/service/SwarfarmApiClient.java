package com.smw.monster.service;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysconf.util.S3Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SwarfarmApiClient {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final MeterRegistry meterRegistry;

    @Value("${smw.swarfarm.http.max-attempts:3}")
    private int maxAttempts;

    @Value("${smw.swarfarm.http.retry-backoff-ms:400}")
    private long retryBackoffMs;

    public SwarfarmApiClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            S3Service s3Service,
            MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.s3Service = s3Service;
        this.meterRegistry = meterRegistry;
    }

    public <T> T fetchJson(String apiUrl, Class<T> responseType) {
        return executeWithRetry("json", apiUrl, () -> {
            String response = restTemplate.getForObject(apiUrl, String.class);
            if (response == null || response.isEmpty()) {
                throw new IllegalStateException("빈 응답을 받았습니다.");
            }
            return objectMapper.readValue(response, responseType);
        });
    }

    public String downloadImageToS3(String imageUrl, String fileName, String fallbackContentType) {
        return executeWithRetry("image", imageUrl, () -> restTemplate.execute(
                imageUrl,
                HttpMethod.GET,
                request -> {
                    HttpHeaders headers = request.getHeaders();
                    headers.set(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT);
                    headers.set(HttpHeaders.ACCEPT, "image/*");
                },
                response -> uploadResponseBody(response, fileName, fallbackContentType)
        ));
    }

    private String uploadResponseBody(ClientHttpResponse response, String fileName, String fallbackContentType)
            throws IOException {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("HTTP 응답 코드: " + response.getStatusCode().value());
        }

        InputStream body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("응답 본문이 비어 있습니다.");
        }

        MediaType mediaType = response.getHeaders().getContentType();
        String contentType = mediaType != null ? mediaType.toString() : fallbackContentType;

        return s3Service.uploadImage(body, fileName, contentType);
    }

    private <T> T executeWithRetry(String operation, String target, CheckedSupplier<T> supplier) {
        Exception lastException = null;
        Timer.Sample sample = Timer.start(meterRegistry);

        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                T result = supplier.get();
                recordMetrics(operation, "success", attempt, sample);
                return result;
            } catch (Exception e) {
                lastException = e;

                if (attempt >= Math.max(1, maxAttempts)) {
                    break;
                }

                log.warn("Swarfarm {} 호출 실패 (attempt {}/{}): {} - {}",
                        operation, attempt, maxAttempts, target, e.getMessage());

                sleepBeforeRetry(attempt);
            }
        }

        recordMetrics(operation, "failure", Math.max(1, maxAttempts), sample);
        throw new RuntimeException("Swarfarm " + operation + " 호출 실패: " + target, lastException);
    }

    private void recordMetrics(String operation, String result, int attempt, Timer.Sample sample) {
        Tags tags = Tags.of(
                "operation", operation,
                "result", result,
                "attempt", String.valueOf(attempt)
        );

        sample.stop(Timer.builder("smw.swarfarm.client.duration")
                .description("Swarfarm external client call duration")
                .tags(tags)
                .register(meterRegistry));

        Counter.builder("smw.swarfarm.client.calls")
                .description("Swarfarm external client call count")
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    private void sleepBeforeRetry(int attempt) {
        if (retryBackoffMs <= 0) {
            return;
        }

        long backoff = retryBackoffMs * attempt;
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트가 발생했습니다.", e);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
