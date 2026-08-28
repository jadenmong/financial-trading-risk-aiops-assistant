package com.jadenmong.riskaiops.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jadenmong.riskaiops.service.ReportWorkflowService;
import com.jadenmong.riskaiops.service.ReportWorkflowService.ImmutableObjectStore;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;

@Configuration
public class ObjectStorageConfig {
    @Bean
    @ConditionalOnProperty(name = "app.reference-mode", havingValue = "true")
    ImmutableObjectStore referenceObjectStore() {
        var documents = new ConcurrentHashMap<String, ReportWorkflowService.ReportContent>();
        return new ImmutableObjectStore() {
            @Override
            public String putIfAbsent(String key, String json, String html) {
                String uri = "minio://risk-reports/" + key;
                documents.putIfAbsent(uri, new ReportWorkflowService.ReportContent(json, html));
                return uri;
            }

            @Override
            public ReportWorkflowService.ReportContent get(String objectUri) {
                var content = documents.get(objectUri);
                if (content == null) throw new ReportWorkflowService.NotFound("Report content is unavailable");
                return content;
            }
        };
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
        return new ImmutableObjectStore() {
            @Override
            public String putIfAbsent(String key, String json, String html) {
                try {
                    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).objectLock(true).build());
                    putOnce(client, bucket, key + "/report.json", "application/json", json);
                    putOnce(client, bucket, key + "/report.html", "text/html; charset=utf-8", html);
                    return "minio://" + bucket + "/" + key;
                } catch (Exception failure) {
                    throw new IllegalStateException("immutable report storage unavailable", failure);
                }
            }

            @Override
            public ReportWorkflowService.ReportContent get(String objectUri) {
                String key = objectKey(objectUri, bucket);
                try {
                    return new ReportWorkflowService.ReportContent(
                            read(client, bucket, key + "/report.json"),
                            read(client, bucket, key + "/report.html"));
                } catch (io.minio.errors.ErrorResponseException missing) {
                    if ("NoSuchKey".equals(missing.errorResponse().code()) || "NoSuchObject".equals(missing.errorResponse().code())) {
                        throw new ReportWorkflowService.NotFound("Report content is unavailable");
                    }
                    throw new IllegalStateException("immutable report storage unavailable", missing);
                } catch (Exception failure) {
                    throw new IllegalStateException("immutable report storage unavailable", failure);
                }
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

    private static String objectKey(String objectUri, String bucket) {
        String prefix = "minio://" + bucket + "/";
        if (!objectUri.startsWith(prefix)) throw new IllegalArgumentException("invalid report object URI");
        String key = objectUri.substring(prefix.length());
        if (!key.matches("^[0-9a-f-]{36}/[0-9a-f]{64}$")) throw new IllegalArgumentException("invalid report object key");
        return key;
    }

    private static String read(MinioClient client, String bucket, String object) throws Exception {
        try (InputStream input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
