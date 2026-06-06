package com.ham.backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DeploymentEvent extends ApplicationEvent {
    private final String namespace;
    private final String data;

    public DeploymentEvent(Object source, String namespace, String data) {
        super(source);
        this.namespace = namespace;
        this.data = data;
    }
}