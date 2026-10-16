package com.platform.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PlatformEventService {

    public record PublishedEvent(String topic, String action, String payloadId, Map<String, Object> metadata) {}

    private final List<PublishedEvent> publishedEvents = new CopyOnWriteArrayList<>();

    public PlatformEventService() {
    }

    public void publishEvent(String topic, String action, String payloadId, Map<String, Object> metadata) {
        PublishedEvent event = new PublishedEvent(topic, action, payloadId, metadata);
        publishedEvents.add(event);
    }

    public List<PublishedEvent> getPublishedEvents() {
        return List.copyOf(publishedEvents);
    }

    public boolean hasEvent(String topic, String action) {
        return publishedEvents.stream()
                .anyMatch(e -> topic.equals(e.topic()) && action.equals(e.action()));
    }

    public void clearEvents() {
        publishedEvents.clear();
    }
}
