package com.ham.backend.controller;

import com.ham.backend.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Deployment 관련 API를 정의합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/deployments")
@Slf4j
public class DeploymentController {

    private final DeploymentService deploymentService;

    /**
     * 지정된 네임스페이스의 Deployment를 감시하기 위해 Server-Sent Events(SSE) 연결을 설정합니다.
     */
    @GetMapping(value = "/{namespace}/watch/{clientId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter watchDeployments(@PathVariable String namespace, @PathVariable String clientId) {
        return deploymentService.watchDeployments(namespace, clientId);
    }

    /**
     * 지정된 네임스페이스의 Deployment 목록을 조회합니다.
     */
    @GetMapping("/{namespace}")
    public List<Map<String, Object>> listDeployments(@PathVariable String namespace) {
        return deploymentService.listDeployments(namespace);
    }

    /**
     * 지정된 네임스페이스의 Deployment를 생성합니다.
     */
    @PostMapping("/{namespace}/{deploymentName}/replicas/{replicas}")
    public String updateDeploymentReplicas(@PathVariable String namespace,
            @PathVariable String deploymentName,
            @PathVariable int replicas) {
        deploymentService.updateReplicaCount(namespace, deploymentName, replicas);
        return "Updated successfully";
    }

    /**
     * 지정된 네임스페이스의 Deployment의 이미지 태그를 업데이트합니다.
     */
    @PostMapping("/{namespace}/{deploymentName}/image/{image}")
    public String updateDeploymentImageTag(@PathVariable String namespace,
            @PathVariable String deploymentName,
            @PathVariable String image) {
        deploymentService.updateImageTag(namespace, deploymentName, image);
        return "Updated successfully";
    }
}