package laggyboi.vivemonkecraftserver;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side half of the no-fall-damage wall slide.
 *
 * Fall damage is computed on the server from ITS own copy of each player's
 * fallDistance, so a client-only install can't cancel it on a dedicated server.
 * The client mod sends a {@link WallSlideC2SPayload} keepalive every tick it is
 * gripping (climbing or sliding); we keep that player's fallDistance at zero for
 * as long as the keepalives arrive.
 *
 * Result mirrors singleplayer exactly:
 *   • slide all the way down  → fallDistance never builds up → no damage
 *   • let go partway (a fail) → keepalives stop, fallDistance accrues from the
 *                               release point → damage only for the remaining drop
 *
 * GRACE_TICKS is a safety net: each keepalive tops the counter back up, and it
 * decays one per tick, so a single dropped keepalive doesn't flicker suppression
 * off mid-slide — and a dropped release packet can't grant permanent immunity
 * (the counter simply runs out a few ticks later).
 *
 * The reset runs at END_SERVER_TICK, which keeps fallDistance ~0 entering every
 * tick; the at-most-one-tick of fall accumulated before a landing is negligible.
 */
public final class FallSuppressTracker {

    /** Ticks of suppression granted per keepalive. ~0.2 s of slack for lost packets. */
    private static final int GRACE_TICKS = 4;

    /** player → ticks of fall suppression remaining. */
    private static final Map<UUID, Integer> GRACE = new ConcurrentHashMap<>();

    private FallSuppressTracker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FallSuppressTracker::onTick);
    }

    /** A gripping keepalive arrived — refresh this player's suppression window. */
    public static void keepAlive(UUID player) {
        GRACE.put(player, GRACE_TICKS);
    }

    /** The client reported it stopped gripping — end suppression now. */
    public static void release(UUID player) {
        GRACE.remove(player);
    }

    /** Drop all state for a leaving player. */
    public static void remove(UUID player) {
        GRACE.remove(player);
    }

    private static void onTick(MinecraftServer server) {
        if (GRACE.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer left = GRACE.get(player.getUUID());
            if (left == null) continue;
            player.resetFallDistance();
            if (left <= 1) GRACE.remove(player.getUUID());
            else           GRACE.put(player.getUUID(), left - 1);
        }
        // Players who disconnected without a release packet are pruned via remove()
        // on DISCONNECT; any remaining orphan entry decays harmlessly above.
    }
}
