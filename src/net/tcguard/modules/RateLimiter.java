package net.tcguard.modules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {

    private final Map<UUID, WindowCounter> globalPacketCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, WindowCounter>> specificPacketCounters = new ConcurrentHashMap<>();
    private final Map<UUID, WindowCounter> commandCounters = new ConcurrentHashMap<>();
    private final Map<UUID, WindowCounter> chatCounters = new ConcurrentHashMap<>();

    private static final int MAX_ENTRIES = 3000;
    private static final long TTL_EXPIRATION_MS = 60000L; // 60s idle TTL

    public boolean checkGlobalPacketRate(UUID uuid, int maxPerSecond) {
        checkCapacity();
        WindowCounter counter = globalPacketCounters.computeIfAbsent(uuid, k -> new WindowCounter());
        return counter.incrementAndCheck(maxPerSecond);
    }

    public boolean checkSpecificPacketRate(UUID uuid, String packetType, int maxPerSecond) {
        Map<String, WindowCounter> map = specificPacketCounters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        WindowCounter counter = map.computeIfAbsent(packetType, k -> new WindowCounter());
        return counter.incrementAndCheck(maxPerSecond);
    }

    public boolean checkCommandRate(UUID uuid, int maxCommandsPerSecond) {
        WindowCounter counter = commandCounters.computeIfAbsent(uuid, k -> new WindowCounter());
        return counter.incrementAndCheck(maxCommandsPerSecond);
    }

    public boolean checkChatRate(UUID uuid, int maxChatPerSecond) {
        WindowCounter counter = chatCounters.computeIfAbsent(uuid, k -> new WindowCounter());
        return counter.incrementAndCheck(maxChatPerSecond);
    }

    private void checkCapacity() {
        if (globalPacketCounters.size() > MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            globalPacketCounters.entrySet().removeIf(e -> (now - e.getValue().getWindowStart()) > TTL_EXPIRATION_MS);
            specificPacketCounters.entrySet().removeIf(e -> {
                Map<String, WindowCounter> inner = e.getValue();
                return inner.isEmpty() || inner.values().stream().allMatch(c -> (now - c.getWindowStart()) > TTL_EXPIRATION_MS);
            });
            commandCounters.entrySet().removeIf(e -> (now - e.getValue().getWindowStart()) > TTL_EXPIRATION_MS);
            chatCounters.entrySet().removeIf(e -> (now - e.getValue().getWindowStart()) > TTL_EXPIRATION_MS);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        globalPacketCounters.remove(uuid);
        specificPacketCounters.remove(uuid);
        commandCounters.remove(uuid);
        chatCounters.remove(uuid);
    }

    public void clearAll() {
        globalPacketCounters.clear();
        specificPacketCounters.clear();
        commandCounters.clear();
        chatCounters.clear();
    }

    public static class WindowCounter {
        private int count = 0;
        private volatile long windowStart = System.currentTimeMillis();

        public synchronized boolean incrementAndCheck(int maxLimit) {
            long now = System.currentTimeMillis();
            if (now - windowStart > 1000L) {
                count = 1;
                windowStart = now;
                return false;
            }
            count++;
            return count > maxLimit;
        }

        public synchronized int getCount() {
            return count;
        }

        public long getWindowStart() {
            return windowStart;
        }
    }
}
