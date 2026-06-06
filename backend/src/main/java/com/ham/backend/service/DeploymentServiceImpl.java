package com.ham.backend.service;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private final SseService sseService;
    private final AppsV1Api api;

    @Override
    public SseEmitter watchDeployments(String namespace, String clientId) {
        return sseService.watchDeployments(namespace, clientId);
    }

    @Override
    public List<Map<String, Object>> listDeployments(String namespace) {
        try {
            V1DeploymentList deploymentList = api.listNamespacedDeployment(namespace).execute();
            return extractDeploymentDetails(deploymentList);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list deployments for namespace " + namespace, e);
        }
    }

    private List<Map<String, Object>> extractDeploymentDetails(V1DeploymentList deploymentList) {
        List<Map<String, Object>> deployments = new ArrayList<>();
        for (V1Deployment deployment : deploymentList.getItems()) {
            deployments.add(Map.of(
                    "name", deployment.getMetadata().getName(),
                    "status", deployment.getStatus(),
                    "replicas", deployment.getSpec().getReplicas(),
                    "image", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage()));
        }
        return deployments;
    }

    @Override
    public void updateReplicaCount(String namespace, String deploymentName, int newReplicaCount) {
        try {
            V1Deployment deployment = api.readNamespacedDeployment(deploymentName, namespace).execute();
            if (deployment != null) {
                deployment.getSpec().setReplicas(newReplicaCount);
                api.replaceNamespacedDeployment(deploymentName, namespace, deployment).execute();
            }
        } catch (ApiException e) {
            log.error("Failed to update deployment: {}", e.getResponseBody());
            throw new RuntimeException("Failed to update deployment", e);
        }
    }

    @Override
    public void updateImageTag(String namespace, String deploymentName, String newImageTag) {
        try {
            V1Deployment deployment = api.readNamespacedDeployment(deploymentName, namespace).execute();
            if (deployment != null) {
                String currentImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
                String[] imageParts = currentImage.split(":");
                String newImage = imageParts[0] + ":" + newImageTag;
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(newImage);
                api.replaceNamespacedDeployment(deploymentName, namespace, deployment).execute();
                log.debug("Updated image tag to: {}", newImageTag);
            }
        } catch (ApiException e) {
            throw new RuntimeException("Failed to get or update deployment: " + e.getMessage(), e);
        }
    }

    @Override
    public int cleanupSseConnections(String clientId) {
        return sseService.cleanupSseConnections(clientId);
    }
}