package com.ham.backend.controller;

import com.ham.backend.service.NamespaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Namespace 관련 API를 정의합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/namespaces")
public class NamespaceController {

    private final NamespaceService namespaceService;

    /**
     * Namespace 목록을 조회합니다.
     */
    @GetMapping
    public List<String> listNamespaces() {
        return namespaceService.listNamespaces();
    }
}