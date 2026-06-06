package com.ham.backend.controller;

import com.ham.backend.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * SSE 관련 API를 정의합니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
public class SseController {

    private final SseService sseService;

    /**
     * 클라이언트 ID와 관련된 모든 SSE 연결을 정리합니다.
     */
    @PostMapping("/cleanup/{clientId}")
    public String cleanupSseConnections(@PathVariable String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId is required.");
        }
        int cleanedCount = sseService.cleanupSseConnections(clientId);
        log.debug("✔️ Cleanup request for clientId: {}, cleaned connections: {}", clientId, cleanedCount);
        return "SSE connections cleaned: " + cleanedCount;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public String handleIllegalArgumentException(IllegalArgumentException e) {
        return e.getMessage();
    }
}