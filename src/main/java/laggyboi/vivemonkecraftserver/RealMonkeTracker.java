package laggyboi.vivemonkecraftserver;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players currently have Real Monke (height-only hitbox shrink) enabled.
 *
 * Set by the {@link RealMonkeC2SPayload} receiver, read every time the server
 * computes a player's dimensions (ServerPlayerHitboxMixin). Entries are removed
 * on disconnect so state never leaks between sessions.
 *
 * Unlike the SCALE attribute (which shrinks width, model, reach — everything),
 * this only affects the collision box HEIGHT, server-side, so movement
 * validation agrees with the client's shrunk box and 1-block tunnels work.
 */
public final class RealMonkeTracker {

    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private RealMonkeTracker() {}

    public static void set(UUID player, boolean on) {
        if (on) ENABLED.add(player);
        else    ENABLED.remove(player);
    }

    public static boolean isOn(UUID player) {
        return ENABLED.contains(player);
    }

    public static void remove(UUID player) {
        ENABLED.remove(player);
    }
}
