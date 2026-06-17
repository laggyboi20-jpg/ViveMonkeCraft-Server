package laggyboi.vivemonkecraftserver;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads server-side settings from
 * <config-dir>/vivemonkecraft-server.properties.
 *
 * Two kinds of settings:
 *
 *  ENFORCED LIMITS (backed up server-side by MovementEnforcer):
 *   modEnabled    – whether VR locomotion is allowed at all.
 *   maxJumpSpeed  – hard cap on launch / movement speed (blocks per tick),
 *                   enforced via position correction + kick. 0 = no cap.
 *   opBypassLevel – minimum op level (0–4) that bypasses everything.
 *
 *  ALLOWANCES (sent to clients; they replace the client mod's built-in default
 *  caps, which restrict players WITHOUT cheats/op):
 *   The client mod clamps non-privileged players to sensible defaults (e.g.
 *   push 2.5). Setting an allowance here grants EVERY player on this server
 *   that limit instead — e.g. allowPushStrength=5.0 lets all players run
 *   push 5 without needing op. Allowances can also be set BELOW the client
 *   defaults to tighten them.
 *   Max-type allowances: 0 = not set (client defaults apply).
 *   Min-type allowances (gravity / air friction, where LOWER = stronger):
 *   -1 = not set; 0 allows everything (e.g. allowGravityMin=0 permits zero-G).
 *
 * Call {@link #load()} once during mod initialisation.
 */
public final class ServerModConfig {

    // ── Enforced limits ─────────────────────────────────────────────────────

    /** When false the client mod should disable all VR locomotion. */
    public static boolean modEnabled    = true;

    /** Max movement speed (blocks per tick). 0 = no cap. */
    public static double  maxJumpSpeed  = 0.0;

    /**
     * Minimum op permission level that bypasses all restrictions.
     * Minecraft levels: 0 = everyone, 1 = bypass spawn protection,
     * 2 = most commands, 3 = kick/ban, 4 = server owner.
     */
    public static int     opBypassLevel = 2;

    // ── Allowances (0 = not set for max-type, -1 = not set for min-type) ───

    /** Push/pull multiplier all players may use (client default cap: 2.5). */
    public static double allowPushStrength   = 0.0;
    /** Jump/throw multiplier all players may use (client default cap: 1.8). */
    public static double allowJumpMultiplier = 0.0;
    /** maxJumpSpeed setting all players may use (client default cap: 1.5). */
    public static double allowMaxJumpSpeed   = 0.0;
    /** Hand-reach multiplier all players may use (client default cap: 2.5). */
    public static double allowHandReach      = 0.0;
    /** Arm length (blocks) all players may use (client default cap: 3.0). */
    public static double allowArmLength      = 0.0;
    /** Step height (blocks) all players may use (client default cap: 1.5). */
    public static double allowStepHeight     = 0.0;
    /** Hand grab radius all players may use (client default cap: 0.15). */
    public static double allowHandRadius     = 0.0;
    /** Lowest gravity multiplier all players may use (client default min: 1.0). */
    public static double allowGravityMin     = -1.0;
    /** Lowest air friction all players may use (client default min: 0.8). */
    public static double allowAirFrictionMin = -1.0;

    private static final String FILE_NAME = "vivemonkecraft-server.properties";

    private ServerModConfig() {}

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (!Files.exists(file)) {
            writeDefaults(file);
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            VivemonkecraftServerMod.LOGGER.error(
                    "[ViveMonkeCraft-Server] Could not read config: {}", e.getMessage());
            return;
        }

        modEnabled    = parseBool(props, "modEnabled",    modEnabled);
        maxJumpSpeed  = parseDouble(props, "maxJumpSpeed",  maxJumpSpeed);
        opBypassLevel = parseInt(props,  "opBypassLevel",  opBypassLevel, 0, 4);

        allowPushStrength   = parseDouble(props, "allowPushStrength",   allowPushStrength);
        allowJumpMultiplier = parseDouble(props, "allowJumpMultiplier", allowJumpMultiplier);
        allowMaxJumpSpeed   = parseDouble(props, "allowMaxJumpSpeed",   allowMaxJumpSpeed);
        allowHandReach      = parseDouble(props, "allowHandReach",      allowHandReach);
        allowArmLength      = parseDouble(props, "allowArmLength",      allowArmLength);
        allowStepHeight     = parseDouble(props, "allowStepHeight",     allowStepHeight);
        allowHandRadius     = parseDouble(props, "allowHandRadius",     allowHandRadius);
        allowGravityMin     = parseDouble(props, "allowGravityMin",     allowGravityMin);
        allowAirFrictionMin = parseDouble(props, "allowAirFrictionMin", allowAirFrictionMin);

        VivemonkecraftServerMod.LOGGER.info(
                "[ViveMonkeCraft-Server] Config loaded — modEnabled={}, maxJumpSpeed={}, opBypassLevel={}, "
                + "allowances: push={}, jumpMult={}, jumpSpeed={}, reach={}, arm={}, step={}, radius={}, "
                + "gravityMin={}, airFrictionMin={}",
                modEnabled, maxJumpSpeed, opBypassLevel,
                allowPushStrength, allowJumpMultiplier, allowMaxJumpSpeed, allowHandReach,
                allowArmLength, allowStepHeight, allowHandRadius, allowGravityMin, allowAirFrictionMin);
    }

    private static void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
                w.println("# ============================================================");
                w.println("# ViveMonke(Quest)Craft  —  Server Configuration");
                w.println("# ============================================================");
                w.println("#");
                w.println("# Changes take effect on the NEXT server restart.");
                w.println("# ============================================================");
                w.println();
                w.println("# ----- ENFORCED LIMITS (backed up server-side) -----");
                w.println();
                w.println("# Set to false to disallow VR locomotion for all non-op players.");
                w.println("# Enforced server-side: players who float or move too fast are kicked / corrected.");
                w.println("modEnabled=true");
                w.println();
                w.println("# Hard cap on launch / movement speed in blocks per tick.");
                w.println("# Enforced server-side: exceeding this for 5+ consecutive ticks corrects the player's position.");
                w.println("# Recommended values: 1.0 = moderate, 1.5 = fast, 2.0 = very fast.");
                w.println("# 0.0 = no cap.");
                w.println("maxJumpSpeed=0.0");
                w.println();
                w.println("# Minimum Minecraft operator level that bypasses ALL restrictions.");
                w.println("# 0 = everyone bypasses, 2 = standard /op (default), 4 = server owner only.");
                w.println("opBypassLevel=2");
                w.println();
                w.println("# ----- ALLOWANCES (what every player may set, without needing op) -----");
                w.println("#");
                w.println("# The client mod clamps players WITHOUT cheats/op to built-in defaults.");
                w.println("# Each allowance below replaces that default for everyone on this server.");
                w.println("# Example: allowPushStrength=5.0 lets every player set push up to 5.");
                w.println("# Allowances can also be LOWER than the client defaults to tighten them.");
                w.println("# 0 = not set (client default cap applies).");
                w.println();
                w.println("# Push/pull multiplier (client default cap: 2.5)");
                w.println("allowPushStrength=0");
                w.println();
                w.println("# Jump/throw multiplier (client default cap: 1.8)");
                w.println("allowJumpMultiplier=0");
                w.println();
                w.println("# Max launch speed setting (client default cap: 1.5)");
                w.println("allowMaxJumpSpeed=0");
                w.println();
                w.println("# Hand-reach multiplier (client default cap: 2.5)");
                w.println("allowHandReach=0");
                w.println();
                w.println("# Max arm length in blocks (client default cap: 3.0)");
                w.println("allowArmLength=0");
                w.println();
                w.println("# Step height in blocks (client default cap: 1.5)");
                w.println("allowStepHeight=0");
                w.println();
                w.println("# Hand grab radius (client default cap: 0.15)");
                w.println("allowHandRadius=0");
                w.println();
                w.println("# Lowest GRAVITY multiplier players may use. Lower = floatier = stronger.");
                w.println("# Client default min: 1.0 (no reduction). 0.5 = allow half gravity,");
                w.println("# 0 = allow full zero-G. -1 = not set (client default applies).");
                w.println("allowGravityMin=-1");
                w.println();
                w.println("# Lowest AIR FRICTION players may use. Lower = throws carry further.");
                w.println("# Client default min: 0.8. 0 = allow no drag at all. -1 = not set.");
                w.println("allowAirFrictionMin=-1");
            }
            VivemonkecraftServerMod.LOGGER.info(
                    "[ViveMonkeCraft-Server] Default config written to {}", file);
        } catch (IOException e) {
            VivemonkecraftServerMod.LOGGER.error(
                    "[ViveMonkeCraft-Server] Failed to write default config: {}", e.getMessage());
        }
    }

    private static boolean parseBool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }

    private static double parseDouble(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) {
            VivemonkecraftServerMod.LOGGER.warn(
                    "[ViveMonkeCraft-Server] Invalid value for '{}': '{}', using {}", key, v.trim(), fallback);
            return fallback;
        }
    }

    private static int parseInt(Properties p, String key, int fallback, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            int val = Integer.parseInt(v.trim());
            if (val < min || val > max) {
                VivemonkecraftServerMod.LOGGER.warn(
                        "[ViveMonkeCraft-Server] '{}' must be {}-{}, got {}, using {}",
                        key, min, max, val, fallback);
                return fallback;
            }
            return val;
        } catch (NumberFormatException e) {
            VivemonkecraftServerMod.LOGGER.warn(
                    "[ViveMonkeCraft-Server] Invalid value for '{}': '{}', using {}", key, v.trim(), fallback);
            return fallback;
        }
    }
}
