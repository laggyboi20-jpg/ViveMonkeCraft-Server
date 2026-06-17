package laggyboi.vivemonkecraftserver.mixin;

import laggyboi.vivemonkecraftserver.RealMonkeTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// =====================================================================
// REAL MONKE — server-side HEIGHT-ONLY hitbox shrink
// =====================================================================
//
// Movement collision is validated by the SERVER with the server's idea of the
// player's bounding box. A client-only shrink therefore can't get a player
// through a 1-block tunnel: the client walks in, the server still has a
// 1.8-tall box, sees the player inside blocks, and rubber-bands them out.
//
// The SCALE attribute fixed that but shrank EVERYTHING (width, model, reach).
// This mixin shrinks ONLY the collision box height — for players who opted in
// via the Real Monke packet — so the server's box matches the client's and
// tunnel movement validates, while width, model and camera stay normal.
// =====================================================================

// TARGET: Player#getDefaultDimensions — NOT Entity#getDimensions! Since 1.20.5,
// LivingEntity overrides getDimensions() as getDefaultDimensions(pose).scale(getScale())
// without calling super, so an Entity.getDimensions injection never runs for
// players. getDefaultDimensions is the method players actually execute.
// priority 2000 (default 1000): Vivecraft's server component also manages player
// poses/sizing — applying later means OUR setReturnValue runs last and wins.
@Mixin(value = Player.class, priority = 2000)
public class ServerPlayerHitboxMixin {

    // require = 0 -> if Mojang renames this in a future version, we just skip
    // the shrink instead of crashing.
    @Inject(
        method = "getDefaultDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void vmcs$shrinkHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object) this instanceof ServerPlayer sp)) return;
        if (!RealMonkeTracker.isOn(sp.getUUID())) return;

        // Height-only, COLLISION-ONLY: cap the box at 0.5 blocks (half the ~2 m
        // player) so a 1-block tunnel has clearance, but keep the ORIGINAL eye
        // height + attachments so the rendered model isn't dragged down (matches
        // the client's PlayerHitboxMixin so movement validation agrees).
        EntityDimensions original = cir.getReturnValue();
        if (original.height() > 0.5f) {
            EntityDimensions shrunk = original.scale(1.0f, 0.5f / original.height());
            cir.setReturnValue(new EntityDimensions(
                    shrunk.width(), shrunk.height(),
                    original.eyeHeight(), original.attachments(), shrunk.fixed()));
        }
    }
}
