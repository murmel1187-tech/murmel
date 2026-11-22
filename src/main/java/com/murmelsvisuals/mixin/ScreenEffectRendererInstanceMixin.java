package com.murmelsvisuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.murmelsvisuals.config.ConfigManager;
import com.murmelsvisuals.config.ModConfig;
import com.murmelsvisuals.util.HookStatus;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Low Fire (variant for an INSTANCE {@code renderFire}; 26.3 made ScreenEffectRenderer take the GameRenderer). See the static variant for details. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererInstanceMixin {
	@ModifyVariable(method = "renderFire", at = @At("HEAD"), argsOnly = true, require = 0)
	private PoseStack murmels$fire(PoseStack original) {
		ModConfig c = ConfigManager.get();
		if (!c.lowFireEnabled) return original;
		float s = 0.45f + 0.55f * c.lowFireHeight;
		PoseStack fresh = new PoseStack();
		fresh.translate(0f, -0.8f * (1f - s), 0f);
		fresh.scale(c.lowFireWidth, s, 1f);
		HookStatus.LOW_FIRE.hit();
		return fresh;
	}
}
