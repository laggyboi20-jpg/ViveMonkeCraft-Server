package laggyboi.vivemonkecraftserver.mixin;

import laggyboi.vivemonkecraftserver.RealMonkeTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Dedicated-server half of the Real Monke suffocation fix: a Real Monke player's
// collision box is 0.5 tall (fits 1-block tunnels) but their eye stays at full
// height, so vanilla suffocation (isInWall → IN_WALL damage, applied server-side)
// would hurt them in a tunnel. Report isInWall = false for tracked players.
@Mixin(Entity.class)
public class ServerSuffocationMixin {

    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true, require = 0)
    private void vmcs$noMonkeSuffocation(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer sp
                && RealMonkeTracker.isOn(sp.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
