package com.ham.backend.service;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Namespace 관련 비즈니스 로직을 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NamespaceServiceImpl implements NamespaceService {

    private final CoreV1Api api;

    /**
     * Namespace 목록을 조회합니다.
     */
    public List<String> listNamespaces() {
        try {
            V1NamespaceList namespaceList = api.listNamespace().execute();
            List<String> namespaces = new ArrayList<>();
            for (V1Namespace ns : namespaceList.getItems()) {
                namespaces.add(ns.getMetadata().getName());
            }
            return namespaces;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get namespaces", e);
        }
    }
}