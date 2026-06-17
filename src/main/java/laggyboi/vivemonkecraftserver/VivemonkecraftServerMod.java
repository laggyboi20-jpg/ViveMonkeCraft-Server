package laggyboi.vivemonkecraftserver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side companion mod for ViveMonke(Quest)Craft.
 *
 * On every player join, this mod sends a {@link ServerConfigPayload}
 * to that player.  The client mod reads the packet and applies the
 * server's movement limits (or disables itself entirely if
 * {@code modEnabled} is {@code false}).
 *
 * The config lives at:
 *   <server-root>/config/vivemonkecraft-server.properties
 */
public class VivemonkecraftServerMod implements ModInitializer {

    public static final Logger LOGGER =
            LoggerFactory.getLogger("vivemonkecraft-server");

    @Override
    public void onInitialize() {
        LOGGER.info("ViveMonke(Quest)Craft Server Config — initialising...");

        // 1. Read server config from disk (creates default file if absent)
        ServerModConfig.load();

        // 2. Register the S2C payload type.
        //    Must be done once during init so Fabric knows how to encode it.
        PayloadTypeRegistry.playS2C().register(
                ServerConfigPayload.ID,
                ServerConfigPayload.STREAM_CODEC
        );

        // 3. Register server-side movement enforcement (works even without the client mod).
        MovementEnforcer.register();

        // 3a. No-fall-damage wall slide: fall damage is computed here from the server's
        //     own fallDistance, so a client-only install can't cancel it. The client
        //     sends a keepalive every tick it's gripping (climbing/sliding); we zero
        //     that player's fallDistance while the keepalives arrive — slide all the
        //     way = no damage, let go partway = damage only from the release point.
        PayloadTypeRegistry.playC2S().register(
                WallSlideC2SPayload.ID,
                WallSlideC2SPayload.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                WallSlideC2SPayload.ID,
                (payload, context) -> {
                    if (payload.gripping()) FallSuppressTracker.keepAlive(context.player().getUUID());
                    else                    FallSuppressTracker.release(context.player().getUUID());
                }
        );
        FallSuppressTracker.register();

        // 3a-2. Magma touch: client tells us a hand is gripping a magma block; apply
        //       hot-floor damage to the sender (server-authoritative, like fall damage).
        //       Invulnerability frames throttle the cadence; fire resistance negates it.
        PayloadTypeRegistry.playC2S().register(
                MagmaTouchC2SPayload.ID,
                MagmaTouchC2SPayload.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                MagmaTouchC2SPayload.ID,
                (payload, context) -> {
                    var p = context.player();
                    p.hurt(p.damageSources().hotFloor(), 1.0f);
                }
        );

        // 3b. Real Monke: the client asks us to shrink its collision box HEIGHT
        //     (only the height — the SCALE attribute was rejected because it
        //     shrinks width, model and reach too). We track who opted in and
        //     ServerPlayerHitboxMixin applies the height cap on the server's
        //     box, so movement validation agrees with the client's shrunk box
        //     and 1-block tunnels work. Fabric runs this handler on the server
        //     thread, so touching the player here is safe.
        PayloadTypeRegistry.playC2S().register(
                RealMonkeC2SPayload.ID,
                RealMonkeC2SPayload.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                RealMonkeC2SPayload.ID,
                (payload, context) -> {
                    RealMonkeTracker.set(context.player().getUUID(), payload.enabled());
                    context.player().refreshDimensions();
                }
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RealMonkeTracker.remove(handler.player.getUUID());
            FallSuppressTracker.remove(handler.player.getUUID());
            // Tell everyone the leaver's monke model is gone.
            if (MonkeModelTracker.isOn(handler.player.getUUID())) {
                MonkeModelTracker.remove(handler.player.getUUID());
                MonkeModelS2CPayload off =
                        new MonkeModelS2CPayload(handler.player.getUUID(), false);
                for (var p : net.fabricmc.fabric.api.networking.v1.PlayerLookup.all(server)) {
                    ServerPlayNetworking.send(p, off);
                }
            }
        });

        // 3c. Monke model (legless look) sync: a client announces its toggle and
        //     we broadcast it to every player, so all mod users see each other
        //     without legs. Joiners get the full current set replayed (below).
        PayloadTypeRegistry.playC2S().register(
                MonkeModelC2SPayload.ID,
                MonkeModelC2SPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                MonkeModelS2CPayload.ID,
                MonkeModelS2CPayload.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                MonkeModelC2SPayload.ID,
                (payload, context) -> {
                    MonkeModelTracker.set(context.player().getUUID(), payload.enabled());
                    MonkeModelS2CPayload sync = new MonkeModelS2CPayload(
                            context.player().getUUID(), payload.enabled());
                    for (var p : net.fabricmc.fabric.api.networking.v1.PlayerLookup.all(
                            context.player().getServer())) {
                        ServerPlayNetworking.send(p, sync);
                    }
                }
        );

        // 4. Send our config to every player the moment they finish joining.
        //    Operators (permission level 2+) bypass the modEnabled ban so admins
        //    can still use the mod on their own server.
        //    This runs on the server thread, so ServerPlayNetworking.send() is safe here.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Cleanup: an earlier Real Monke build used the SCALE attribute, which
            // persists in player NBT. Reset any leftover shrink from those builds.
            AttributeInstance scale = handler.player.getAttribute(Attributes.SCALE);
            if (scale != null && scale.getBaseValue() != 1.0) {
                scale.setBaseValue(1.0);
            }

            // Replay the current monke-model set to the joiner so already-legless
            // players render correctly from the first frame.
            for (java.util.UUID monke : MonkeModelTracker.all()) {
                ServerPlayNetworking.send(handler.player,
                        new MonkeModelS2CPayload(monke, true));
            }

            // Players at or above opBypassLevel get modEnabled=true and no speed cap,
            // regardless of what the config says. (Ops also bypass the client-side
            // setting caps via their permission level, so allowances don't matter
            // for them — they're sent the same allowances as everyone anyway.)
            boolean isOp = handler.player.hasPermissions(ServerModConfig.opBypassLevel);

            ServerPlayNetworking.send(
                    handler.player,
                    new ServerConfigPayload(
                            isOp || ServerModConfig.modEnabled,
                            isOp ? 0.0 : ServerModConfig.maxJumpSpeed,
                            ServerModConfig.allowPushStrength,
                            ServerModConfig.allowJumpMultiplier,
                            ServerModConfig.allowMaxJumpSpeed,
                            ServerModConfig.allowHandReach,
                            ServerModConfig.allowArmLength,
                            ServerModConfig.allowStepHeight,
                            ServerModConfig.allowHandRadius,
                            ServerModConfig.allowGravityMin,
                            ServerModConfig.allowAirFrictionMin
                    )
            );
        });

        LOGGER.info("ViveMonke(Quest)Craft Server Config — ready.");
    }
}
