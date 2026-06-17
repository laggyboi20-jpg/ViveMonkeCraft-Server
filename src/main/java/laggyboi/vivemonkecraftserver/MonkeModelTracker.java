package laggyboi.vivemonkecraftserver;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players currently have the monke model (legless look) enabled.
 * Fed by {@link MonkeModelC2SPayload}; broadcast to all clients via
 * {@link MonkeModelS2CPayload} so every mod user renders them legless.
 * Cleared per player on disconnect.
 */
public final class MonkeModelTracker {

    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private MonkeModelTracker() {}

    public static void set(UUID player, boolean on) {
        if (on) ENABLED.add(player);
        else    ENABLED.remove(player);
    }

    public static boolean isOn(UUID player) {
        return ENABLED.contains(player);
    }

    public static Set<UUID> all() {
        return ENABLED;
    }

    public static void remove(UUID player) {
        ENABLED.remove(player);
    }
}
