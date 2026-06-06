package com.ham.backend.service;

public interface WatchService {
    void stopNamespaceWatch(String namespace);

    void startNamespaceWatch(String namespace);
}
