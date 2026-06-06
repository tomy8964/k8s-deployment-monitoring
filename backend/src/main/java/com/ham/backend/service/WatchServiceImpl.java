package com.ham.backend.service;

import com.ham.backend.event.DeploymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.Watch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchServiceImpl implements WatchService {

    private final ApiClient apiClient;
    private final AppsV1Api api;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ConcurrentMap<String, Watch<V1Deployment>> namespaceWatchRegistry = new ConcurrentHashMap<>();

    public void startNamespaceWatch(String namespace) {
        log.info("🔄 Creating and starting Watch for namespace: {}", namespace);
        executorService.submit(() -> {
            Watch<V1Deployment> watch = null;
            try {
                watch = createDeploymentWatch(namespace);
                Watch<V1Deployment> previous = namespaceWatchRegistry.putIfAbsent(namespace, watch);
                if (previous != null) {
                    // 누군가 먼저 넣었으면 현재 것은 바로 종료
                    watch.close();
                    return;
                }

                for (Watch.Response<V1Deployment> event : watch) {
                    log.info("📥 Received event for namespace: {}, event: {}", namespace, event.type);
                    eventPublisher.publishEvent(new DeploymentEvent(this, namespace, formatDeploymentEventData(event)));
                }
            } catch (Exception e) {
                log.error("❌ Error while watching namespace {}: {}", namespace, e.getMessage());
            } finally {
                namespaceWatchRegistry.remove(namespace);
                if (watch != null) {
                    try {
                        watch.close();
                    } catch (IOException ignored) {}
                }
                log.info("✔️ Watch stopped for namespace: {}", namespace);
            }
        });
    }

    public void stopNamespaceWatch(String namespace) {
        Watch<V1Deployment> watch = namespaceWatchRegistry.remove(namespace);
        if (watch != null) {
            try {
                watch.close();
                log.info("✔️ Watch for namespace: {} has been stopped.", namespace);
            } catch (IOException e) {
                log.error("❌ Error while closing Watch for namespace: {}", namespace, e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("⚙️ Shutting down executor service in WatchServiceImpl...");
        executorService.shutdown();
    }

    protected Watch<V1Deployment> createDeploymentWatch(String namespace) throws ApiException {
        AppsV1Api.APIlistNamespacedDeploymentRequest request = api.listNamespacedDeployment(namespace).watch(true);
        return Watch.createWatch(
                apiClient,
                request.buildCall(null),
                new TypeToken<Watch.Response<V1Deployment>>() {
                }.getType());
    }

    private String formatDeploymentEventData(Watch.Response<V1Deployment> event) throws IOException {
        String deploymentName = event.object.getMetadata().getName();
        int replicas = event.object.getSpec().getReplicas() != null ? event.object.getSpec().getReplicas() : 0;
        int availableReplicas = 0;
        int readyReplicas = 0;
        Object statusObj = null;

        if (event.object.getStatus() != null) {
            statusObj = event.object.getStatus();
            if (event.object.getStatus().getAvailableReplicas() != null) {
                availableReplicas = event.object.getStatus().getAvailableReplicas();
            }
            if (event.object.getStatus().getReadyReplicas() != null) {
                readyReplicas = event.object.getStatus().getReadyReplicas();
            }
        }

        String image = "unknown";
        if (event.object.getSpec().getTemplate().getSpec() != null &&
            event.object.getSpec().getTemplate().getSpec().getContainers() != null &&
            !event.object.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {
            image = event.object.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("type", event.type);
        data.put("name", deploymentName);
        data.put("replicas", replicas);
        data.put("availableReplicas", availableReplicas);
        data.put("readyReplicas", readyReplicas);
        data.put("status", statusObj);
        data.put("image", image);

        return objectMapper.writeValueAsString(data);
    }
}