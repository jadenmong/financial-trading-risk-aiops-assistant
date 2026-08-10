package com.jadenmong.riskaiops.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jadenmong.riskaiops.service.ReportWorkflowService.ImmutableObjectStore;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;

@Configuration
public class ObjectStorageConfig {
    @Bean
    @ConditionalOnProperty(name = "app.reference-mode", havingValue = "true")
    ImmutableObjectStore referenceObjectStore() {
        return (key, json, html) -> "minio://risk-reports/" + key;
    }

    @Bean
    @ConditionalOnProperty(name = "app.reference-mode", havingValue = "false", matchIfMissing = true)
    ImmutableObjectStore minioObjectStore(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.report-bucket}") String bucket) {
        if (accessKey.isBlank() || secretKey.isBlank()) throw new IllegalStateException("MinIO credentials are required outside reference mode");
        MinioClient client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        return (key, json, html) -> {
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).objectLock(true).build());
                putOnce(client, bucket, key + "/report.json", "application/json", json);
                putOnce(client, bucket, key + "/report.html", "text/html; charset=utf-8", html);
                return "minio://" + bucket + "/" + key;
            } catch (Exception failure) {
                throw new IllegalStateException("immutable report storage unavailable", failure);
            }
        };
    }

    private static void putOnce(MinioClient client, String bucket, String object, String contentType, String value) throws Exception {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(object).build());
            return;
        } catch (io.minio.errors.ErrorResponseException notFound) {
            if (!"NoSuchKey".equals(notFound.errorResponse().code()) && !"NoSuchObject".equals(notFound.errorResponse().code())) throw notFound;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(object).contentType(contentType)
                .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L).build());
    }
}
