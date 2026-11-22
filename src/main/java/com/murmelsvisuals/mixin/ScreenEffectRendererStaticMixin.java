package com.murmelsvisuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.murmelsvisuals.config.ConfigManager;
import com.murmelsvisuals.config.ModConfig;
import com.murmelsvisuals.util.HookStatus;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Low Fire (variant for a STATIC {@code renderFire}, as in 26.2 and earlier).
 *
 * <p>Instead of push/pop on the game's pose stack, the pose-stack ARGUMENT is replaced by a fresh stack that only
 * contains our scale + translate. The fire overlay is drawn in screen space with an identity pose, so nothing is
 * lost, and there is nothing to pop (so a half-applied mixin can never unbalance the stack). The handler only
 * depends on the method NAME and on having exactly one {@code PoseStack} parameter - not on the other parameter
 * types, which changed in 26.x.</p>
 *
 * <p>Geometry (vanilla): quads span y = -0.5..0.5 translated by -0.3, so the bottom edge sits at y = -0.8.
 * Scaling by {@code s} and translating by {@code -0.8 * (1 - s)} keeps that edge fixed. {@code height} (0.10..1.00)
 * maps to {@code s = 0.45 + 0.55 * height} (estimate for FOV 70; tune in game).</p>
 *
 * <p>Exactly one of the two Low Fire mixin classes applies, depending on whether the 26.3 method is static; the
 * other one is skipped with a harmless warning in the log.</p>
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererStaticMixin {
	@ModifyVariable(method = "renderFire", at = @At("HEAD"), argsOnly = true, require = 0)
	private static PoseStack murmels$fire(PoseStack original) {
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
