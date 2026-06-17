package laggyboi.vivemonkecraftserver;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.UUID;

/**
 * Server-side movement enforcement.
 *
 * Two complementary checks run every tick:
 *
 *  1. SPEED CHECK — measures sqrt(dx² + max(dy,0)² + dz²) per tick (horizontal
 *     AND upward movement; downward/falling is excluded so it doesn't interfere
 *     with gravity).  If a player exceeds the limit for more than GRACE_TICKS
 *     consecutive ticks their position is capped in that direction.
 *
 *  2. AIRBORNE CHECK (modEnabled=false only) — if a player is off the ground
 *     for more than MAX_AIR_TICKS consecutive ticks without a legitimate flight
 *     mode (elytra, levitation effect, creative) they are kicked.  This catches
 *     VR zero-G floating that the speed check misses because the player may not
 *     be moving fast — they just never land.
 *
 * What this enforces:
 *   maxJumpSpeed     → speed check caps any directional VR launch
 *   modEnabled=false → speed check + airborne kick together prevent all
 *                      gorilla-locomotion while leaving normal movement intact
 *
 * What CANNOT be enforced server-side (purely client-side signals):
 *   maxHandReachMultiplier / maxArmLength  (grab hitbox position)
 *   maxPullStrength / maxJumpMultiplier    (scale of initial throw — but the
 *     resulting speed IS caught by the speed cap)
 *   minGravityMultiplier  (client physics only — detected indirectly by the
 *     airborne check when modEnabled=false)
 */
public final class MovementEnforcer {

    // ── Constants ─────────────────────────────────────────────────────────────

    /**
     * Consecutive "too-fast" ticks before position correction fires.
     * Ender pearls, knockback, and explosions are a brief burst (1–3 ticks).
     * Sustained VR locomotion stays fast for many ticks in a row.
     */
    private static final int GRACE_TICKS = 5;

    /**
     * Consecutive off-ground ticks (when modEnabled=false) before the player
     * is kicked.  Normal gameplay: a jump peaks at ~12 ticks before landing.
     * VR zero-G can keep a player airborne indefinitely.
     * 30 ticks = 1.5 s — enough grace for a double-jump off a ledge.
     */
    private static final int MAX_AIR_TICKS = 30;

    /**
     * Consecutive over-speed ticks before the player is kicked.
     * The first GRACE_TICKS are ignored (pearls, knockback).
     * Ticks GRACE+1 … KICK_STREAK: position is corrected each tick (rubber-band).
     * After KICK_STREAK total ticks the player is kicked — sustained fast movement
     * means correction alone isn't stopping them (hacked client, etc.).
     * 60 ticks = 3 s of continuous correction before the kick fires.
     */
    private static final int KICK_STREAK_TICKS = 60;

    /**
     * Fallback speed cap when modEnabled=false.
     * Sprinting + Speed II ≈ 0.45 b/t; 0.6 leaves a small buffer for lag.
     */
    private static final double VANILLA_MAX_SPEED = 0.6;

    // ── Per-player state ──────────────────────────────────────────────────────

    private static final HashMap<UUID, Vec3>    lastValidPos  = new HashMap<>();
    private static final HashMap<UUID, Integer> fastStreak    = new HashMap<>();
    private static final HashMap<UUID, Integer> airStreak     = new HashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private MovementEnforcer() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MovementEnforcer::onTick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            lastValidPos.remove(id);
            fastStreak.remove(id);
            airStreak.remove(id);
        });
    }

    // ── Per-tick enforcement ──────────────────────────────────────────────────

    private static void onTick(MinecraftServer server) {

        // Determine whether any enforcement is needed this tick.
        final boolean disableMod  = !ServerModConfig.modEnabled;
        final double  speedLimit;
        if (disableMod) {
            speedLimit = VANILLA_MAX_SPEED;
        } else if (ServerModConfig.maxJumpSpeed > 0.0) {
            speedLimit = ServerModConfig.maxJumpSpeed;
        } else {
            // No restrictions at all — clear state and skip.
            lastValidPos.clear();
            fastStreak.clear();
            airStreak.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id  = player.getUUID();
            Vec3 pos = player.position();

            // ── Exemptions ────────────────────────────────────────────────────
            // These bypass all checks so legitimate fast/airborne states aren't
            // incorrectly flagged.  Operators (level 2+) are also exempt so admins
            // can always use the mod on their own server.
            if (VivemonkecraftServerMod.hasOpLevel(player, ServerModConfig.opBypassLevel) // op bypass
                    || ServerModConfig.opBypassLevel == 0           // 0 = everyone exempt
                    || player.isCreative()
                    || player.isSpectator()
                    || player.isDeadOrDying()
                    || player.isPassenger()         // minecart, boat, horse …
                    || player.isFallFlying()         // elytra
                    || player.isInWater()
                    || player.isInLava()
                    || hasLevitation(player)) {
                lastValidPos.put(id, pos);
                fastStreak.put(id, 0);
                airStreak.put(id, 0);
                continue;
            }

            // ── Airborne check (modEnabled=false only) ────────────────────────
            // Detects zero-G floating: player is never onGround but not actually
            // falling — the speed check alone can't catch this because the player
            // may be nearly stationary while floating.
            if (disableMod) {
                if (!player.onGround()) {
                    int air = airStreak.getOrDefault(id, 0) + 1;
                    airStreak.put(id, air);
                    if (air > MAX_AIR_TICKS) {
                        player.connection.disconnect(Component.literal(
                                "VR locomotion is disabled on this server."));
                        lastValidPos.remove(id);
                        fastStreak.remove(id);
                        airStreak.remove(id);
                        VivemonkecraftServerMod.LOGGER.info(
                                "[ViveMonkeCraft-Server] Kicked {} — airborne {}+ ticks "
                                + "with modEnabled=false",
                                player.getName().getString(), MAX_AIR_TICKS);
                        continue;
                    }
                } else {
                    airStreak.put(id, 0);
                }
            }

            // ── Speed check ───────────────────────────────────────────────────
            Vec3 lastPos = lastValidPos.get(id);
            if (lastPos == null) {
                lastValidPos.put(id, pos);
                fastStreak.put(id, 0);
                continue;
            }

            double dx    = pos.x - lastPos.x;
            double dy    = pos.y - lastPos.y;
            double dz    = pos.z - lastPos.z;

            // Include upward movement (VR launches) but NOT downward (gravity/falling).
            // This prevents false-positives when a player jumps off a cliff.
            double upDy  = Math.max(dy, 0.0);
            double dist  = Math.sqrt(dx * dx + upDy * upDy + dz * dz);

            if (dist <= speedLimit) {
                lastValidPos.put(id, pos);
                fastStreak.put(id, 0);
                continue;
            }

            // Over the speed limit.
            int streak = fastStreak.getOrDefault(id, 0) + 1;
            fastStreak.put(id, streak);

            if (streak <= GRACE_TICKS) {
                // Grace window — allow brief fast events (ender pearls, knockback).
                lastValidPos.put(id, pos);
                continue;
            }

            // Kick if they have been corrected continuously for too long.
            // If correction alone hasn't stopped them it means the client is ignoring it.
            if (streak > KICK_STREAK_TICKS) {
                player.connection.disconnect(Component.literal(
                        "Movement speed limit exceeded."));
                lastValidPos.remove(id);
                fastStreak.remove(id);
                airStreak.remove(id);
                VivemonkecraftServerMod.LOGGER.info(
                        "[ViveMonkeCraft-Server] Kicked {} — exceeded speed limit "
                        + "({} b/t > {}) for {}+ ticks",
                        player.getName().getString(),
                        String.format("%.2f", dist),
                        String.format("%.2f", speedLimit),
                        KICK_STREAK_TICKS);
                continue;
            }

            // Sustained fast movement — cap to exactly speedLimit in the same direction.
            double scale = speedLimit / dist;
            double cx    = lastPos.x + dx   * scale;
            double cy    = (dy >= 0)
                           ? lastPos.y + dy * scale   // cap upward movement
                           : pos.y;                    // preserve actual Y when falling
            double cz    = lastPos.z + dz   * scale;

            player.connection.teleport(cx, cy, cz, player.getYRot(), player.getXRot());
            lastValidPos.put(id, new Vec3(cx, cy, cz));

            VivemonkecraftServerMod.LOGGER.info(
                    "[ViveMonkeCraft-Server] Corrected {}'s position ({} b/t > limit {})",
                    player.getName().getString(),
                    String.format("%.2f", dist),
                    String.format("%.2f", speedLimit));

            // Keep streak elevated so next over-limit tick is corrected immediately.
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasLevitation(ServerPlayer player) {
        return player.getActiveEffects().stream().anyMatch(
                e -> e.getEffect().value()
                        == net.minecraft.world.effect.MobEffects.LEVITATION.value());
    }
}
