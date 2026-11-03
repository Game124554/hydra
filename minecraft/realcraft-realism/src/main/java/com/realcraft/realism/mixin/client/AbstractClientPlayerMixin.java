package com.realcraft.realism.mixin.client;

import com.realcraft.realism.zombie.ZombifiedState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zombified players look like zombies: zombie skin on the classic (wide-arm) model, hands included. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void realcraft$zombieSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        if (ZombifiedState.isZombifiedOnClient(((AbstractClientPlayer) (Object) this).getId())) {
            cir.setReturnValue(ZombifiedState.ZOMBIE_SKIN);
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void realcraft$zombieModel(CallbackInfoReturnable<String> cir) {
        if (ZombifiedState.isZombifiedOnClient(((AbstractClientPlayer) (Object) this).getId())) {
            cir.setReturnValue("default");
        }
    }
}
