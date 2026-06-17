package laggyboi.vivemonkecraftserver;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server request: apply Real Monke (height-only hitbox shrink) to the
 * sender — the client mod's gorilla-size feature.
 *
 * The packet carries only on/off intent: the server tracks opted-in players
 * (RealMonkeTracker) and ServerPlayerHitboxMixin caps their collision box
 * HEIGHT at 0.5 blocks. Width, model and reach are untouched, and the cap
 * is fixed server-side so the packet can't be abused for arbitrary sizes.
 *
 * Wire format: one boolean. MUST match the client mod's mirror class.
 */
public record RealMonkeC2SPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RealMonkeC2SPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("vivemonkecraft", "real_monke"));

    public static final StreamCodec<FriendlyByteBuf, RealMonkeC2SPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.enabled()),
                    buf -> new RealMonkeC2SPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
