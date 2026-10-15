package com.platform.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PlatformCacheService {

    private final Map<String, Object> localStore = new ConcurrentHashMap<>();
    private final AtomicInteger cacheHitCount = new AtomicInteger(0);

    public PlatformCacheService() {
    }

    public boolean isConnected() {
        return localStore != null;
    }

    public void put(String key, Object value) {
        localStore.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (localStore.containsKey(key)) {
            cacheHitCount.incrementAndGet();
            return (T) localStore.get(key);
        }
        return null;
    }

    public boolean containsKey(String key) {
        return localStore.containsKey(key);
    }

    public void evict(String key) {
        localStore.remove(key);
    }

    public void evictByPrefix(String prefix) {
        Set<String> keys = Set.copyOf(localStore.keySet());
        for (String k : keys) {
            if (k.startsWith(prefix)) {
                evict(k);
            }
        }
    }

    public int getCacheHitCount() {
        return cacheHitCount.get();
    }

    public void clearAll() {
        localStore.clear();
        cacheHitCount.set(0);
    }
}
