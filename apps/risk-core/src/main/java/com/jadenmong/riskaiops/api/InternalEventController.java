package com.jadenmong.riskaiops.api;

import java.security.Principal;
import java.security.cert.X509Certificate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.jadenmong.riskaiops.domain.EventEnvelope;
import com.jadenmong.riskaiops.service.IngestionEventService;

@RestController
@RequestMapping("/internal/v1")
public class InternalEventController {
    private final IngestionEventService ingestion;
    private final boolean requireSourceMtls;

    public InternalEventController(IngestionEventService ingestion, @Value("${app.require-source-mtls:false}") boolean requireSourceMtls) {
        this.ingestion = ingestion;
        this.requireSourceMtls = requireSourceMtls;
    }

    @PostMapping("/events")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'ingest:write')")
    public ResponseEntity<IngestionEventService.IngestionResult> ingest(@Valid @RequestBody EventEnvelope event,
                                                                        Principal principal,
                                                                        HttpServletRequest request) {
        if (requireSourceMtls && !hasClientCertificate(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "source mTLS client certificate is required");
        }
        return ResponseEntity.accepted().body(ingestion.ingest(event, actor(principal)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(java.util.Map.of("code", "INVALID_EVENT", "message", exception.getMessage()));
    }

    private static boolean hasClientCertificate(HttpServletRequest request) {
        Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
        return value instanceof X509Certificate[] certificates && certificates.length > 0;
    }

    private static String actor(Principal principal) { return principal == null ? "reference-source" : principal.getName(); }
}
