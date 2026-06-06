package com.ham.backend.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface DeploymentService {
    SseEmitter watchDeployments(String namespace, String clientId);

    List<Map<String, Object>> listDeployments(String namespace);

    void updateReplicaCount(String namespace, String deploymentName, int newReplicaCount);

    void updateImageTag(String namespace, String deploymentName, String newImageTag);

    int cleanupSseConnections(String clientId);
}
