package com.ham.backend.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseService {
    SseEmitter watchDeployments(String namespace, String clientId);

    void notifyEmitters(String namespace, String data);

    int cleanupSseConnections(String clientId);
}
