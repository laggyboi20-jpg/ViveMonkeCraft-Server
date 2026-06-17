package laggyboi.vivemonkecraftserver;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "a hand is gripping a magma block this tick — hurt me."
 *
 * Magma damage is server-authoritative, so a client-only install can't apply it here.
 * The client mod sends this each tick a hand touches a magma block; we apply hot-floor
 * damage to the sender. Vanilla invulnerability frames throttle the cadence and fire
 * resistance negates it — same as standing on magma.
 *
 * No payload data. MUST match the client mod's mirror class.
 */
public record MagmaTouchC2SPayload() implements CustomPacketPayload {

    public static final MagmaTouchC2SPayload INSTANCE = new MagmaTouchC2SPayload();

    public static final CustomPacketPayload.Type<MagmaTouchC2SPayload> ID =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("vivemonkecraft", "magma_touch"));

    public static final StreamCodec<FriendlyByteBuf, MagmaTouchC2SPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
