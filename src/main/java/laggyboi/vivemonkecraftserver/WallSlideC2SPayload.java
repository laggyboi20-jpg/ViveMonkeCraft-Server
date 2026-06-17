package laggyboi.vivemonkecraftserver;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "I am gripping right now (climbing or sliding)" keepalive.
 *
 * Fall damage is server-authoritative, so a client-only install can't suppress it
 * here. The client mod sends this while gripping; {@link FallSuppressTracker} zeroes
 * the sender's fallDistance each tick they're gripping — slide all the way down = no
 * damage, let go partway = damage only from the release point.
 *
 * {@code gripping=true} arrives every gripping tick (keepalive); {@code false}
 * arrives once on release. The tracker also auto-expires the flag after a short
 * grace window so a dropped release packet can't grant permanent fall immunity.
 *
 * Wire format: one boolean. MUST match the client mod's mirror class.
 */
public record WallSlideC2SPayload(boolean gripping) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WallSlideC2SPayload> ID =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("vivemonkecraft", "wall_slide"));

    public static final StreamCodec<FriendlyByteBuf, WallSlideC2SPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.gripping()),
                    buf -> new WallSlideC2SPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
