package com.fortnitelocker.mixin;

import com.fortnitelocker.LockerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerSkinMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void fortnitelocker$swapSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        Identifier tex = LockerState.getEquippedTexture();
        if (tex == null) return;
        SkinTextures orig = cir.getReturnValue();
        // Keep the player's own cape/elytra, swap only the body skin.
        cir.setReturnValue(new SkinTextures(
            tex, null, orig.capeTexture(), orig.elytraTexture(), orig.model(), true));
    }
}
