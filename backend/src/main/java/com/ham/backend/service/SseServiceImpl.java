package com.ham.backend.service;

import com.ham.backend.event.DeploymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseServiceImpl implements SseService {

    private final WatchService watchService;
    private static final long SSE_TIMEOUT = 600_000L;
    private final Map<String, SseEmitter> sseEmitterRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<SseEmitter>> namespaceEmitters = new ConcurrentHashMap<>();
    private final ConcurrentMap<SseEmitter, String> emitterToClientIdMap = new ConcurrentHashMap<>();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public SseEmitter watchDeployments(String namespace, String clientId) {
        removeExistingEmitter(clientId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("✔️ SSE connection initialized for namespace: {}, clientId: {}", namespace, clientId);

        sseEmitterRegistry.put(clientId, emitter);
        addEmitterToNamespace(namespace, clientId, emitter);
        sendInitialConnectionMessage(emitter, namespace);
        initializeNamespaceWatchIfAbsent(namespace);
        setupSseCallbacks(emitter, namespace, clientId);

        return emitter;
    }

    private void initializeNamespaceWatchIfAbsent(String namespace) {
        if (!namespaceEmitters.containsKey(namespace)) {
            watchService.startNamespaceWatch(namespace);
        } else {
            log.info("⏩ Namespace '{}' already has a Watch registered. Skipping...", namespace);
        }
    }

    @EventListener
    public void handleDeploymentEvent(DeploymentEvent event) {
        log.info("📥 DeploymentEvent received for namespace: {}, data: {}", event.getNamespace(), event.getData());
        sseExecutor.submit(() -> notifyEmitters(event.getNamespace(), event.getData()));
    }

    public void notifyEmitters(String namespace, String data) {
        List<SseEmitter> emitters = namespaceEmitters.getOrDefault(namespace, new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(data));
                log.info("📤 SSE data sent to namespace: {}", namespace);
            } catch (IOException ioException) {
                String errorMessage = ioException.getMessage();
                if (errorMessage != null && (errorMessage.contains("Connection reset by peer") ||
                        errorMessage.contains("Software caused connection abort") ||
                        errorMessage.contains("현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다"))) {
                    log.warn("⚠️ Client disconnected for namespace: {}. Removing emitter.", namespace);
                } else {
                    log.error("❌ Unexpected IOException for namespace: {}, error: {}", namespace, errorMessage);
                }
                removeEmitterFromNamespace(namespace, emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            } catch (Exception e) {
                log.error("❌ Unexpected error for namespace: {}, error: {}", namespace, e.getMessage());
                removeEmitterFromNamespace(namespace, emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }

    private void addEmitterToNamespace(String namespace, String clientId, SseEmitter emitter) {
        initializeNamespaceWatchIfAbsent(namespace);

        CopyOnWriteArrayList<SseEmitter> emitters = namespaceEmitters.computeIfAbsent(namespace,
                key -> new CopyOnWriteArrayList<>());
        Optional<SseEmitter> existingEmitter = emitters.stream()
                .filter(e -> clientId.equals(emitterToClientIdMap.get(e)))
                .findFirst();
        existingEmitter.ifPresent(e -> {
            log.warn("⚠️ Duplicate clientId '{}' detected in namespace '{}'. Removing old emitter.", clientId,
                    namespace);
            removeEmitterFromNamespace(namespace, e);
            try {
                e.complete();
            } catch (Exception ignored) {}
        });
        emitters.add(emitter);
        emitterToClientIdMap.put(emitter, clientId);
        log.info("➕ Added clientId: {} to namespace: {}", clientId, namespace);
        logNamespaceState();
    }

    private void logNamespaceState() {
        log.info("📊 Current Namespace-State Mapping:");
        namespaceEmitters.forEach((ns, emitters) -> {
            List<String> clients = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                String clientId = emitterToClientIdMap.get(emitter);
                clients.add(clientId != null ? clientId : "(unknown)");
            }
            log.info("Namespace: {} -> Connected Clients: {}", ns, clients);
        });
    }

    private void removeExistingEmitter(String clientId) {
        SseEmitter existingEmitter = sseEmitterRegistry.get(clientId);
        if (existingEmitter != null) {
            List<String> oldNamespaces = findNamespacesByClientId(clientId);
            if (!oldNamespaces.isEmpty()) {
                for (String oldNamespace : oldNamespaces) {
                    removeEmitterFromNamespace(oldNamespace, existingEmitter);
                }
            }
            try {
                existingEmitter.complete();
            } catch (Exception e) {
                log.warn("Emitter completion failed: {}", e.getMessage());
            }
        }
    }

    private List<String> findNamespacesByClientId(String clientId) {
        List<String> namespaces = new ArrayList<>();
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : namespaceEmitters.entrySet()) {
            List<SseEmitter> emitters = entry.getValue();
            for (SseEmitter emitter : emitters) {
                if (sseEmitterRegistry.get(clientId) == emitter) {
                    namespaces.add(entry.getKey());
                }
            }
        }
        return namespaces;
    }

    private void sendInitialConnectionMessage(SseEmitter emitter, String namespace) {
        String initialMessage = String.format(
                "{\"message\": \"Connection successful\", \"namespace\": \"%s\", \"status\": \"connected\"}",
                namespace);
        try {
            emitter.send(SseEmitter.event().data(initialMessage));
        } catch (IOException e) {
            log.error("❌ Failed to send initial connection message: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private void setupSseCallbacks(SseEmitter emitter, String namespace, String clientId) {
        emitter.onCompletion(() -> {
            log.info("✔️ SSE connection completed for clientId: {} and namespace: {}", clientId, namespace);
            sseEmitterRegistry.remove(clientId);
            removeEmitterFromNamespace(namespace, emitter);
            emitterToClientIdMap.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.warn("⚠️ SSE connection timed out for clientId: {}, namespace: {}", clientId, namespace);
            emitter.complete();
        });
        emitter.onError(error -> {
            log.error("❌ SSE connection error for clientId: {}, namespace: {}", clientId, namespace);
            emitter.complete();
        });
    }

    private void removeEmitterFromNamespace(String namespace, SseEmitter emitter) {
        List<SseEmitter> emitters = namespaceEmitters.get(namespace);
        if (emitters == null) return;

        emitters.remove(emitter);
        String clientId = emitterToClientIdMap.remove(emitter);
        if (clientId != null) {
            sseEmitterRegistry.remove(clientId);
        }
        log.info("🔄 Removed emitter for namespace: {}", namespace);
        if (emitters.isEmpty()) {
            log.info("⚠️ No more emitters for namespace: {}. Stopping Watch.", namespace);
            watchService.stopNamespaceWatch(namespace);
            namespaceEmitters.remove(namespace);
        }
    }

    public int cleanupSseConnections(String clientId) {
        int cleanedCount = 0;
        List<SseEmitter> emittersToRemove = emitterToClientIdMap.entrySet()
                .stream()
                .filter(entry -> clientId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        for (SseEmitter emitterToRemove : emittersToRemove) {
            List<String> namespaces = findNamespacesByClientId(clientId);
            if (!namespaces.isEmpty()) {
                for (String namespace : namespaces) {
                    log.info("🔄 Removing emitter for clientId: {} from namespace: {}", clientId, namespace);
                    removeEmitterFromNamespace(namespace, emitterToRemove);
                    cleanedCount++;
                }
            }
            try {
                emitterToRemove.complete();
            } catch (Exception e) {
                log.warn("⚠️ Failed to complete emitter during cleanup for clientId: {}", clientId);
            }
            log.info("✔️ Emitter removed for clientId: {}", clientId);
        }
        return cleanedCount;
    }

    @PreDestroy
    public void shutdown() {
        log.info("⚙️ Shutting down executor service in SseServiceImpl...");
        sseExecutor.shutdown();
    }
}