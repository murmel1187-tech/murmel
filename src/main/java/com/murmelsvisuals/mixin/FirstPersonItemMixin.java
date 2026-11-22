package com.murmelsvisuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.murmelsvisuals.config.ConfigManager;
import com.murmelsvisuals.config.ModConfig;
import com.murmelsvisuals.util.HookStatus;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Side Shield. Before the first-person item is submitted, a shield is moved toward the screen edge of the hand that
 * holds it, lowered, tilted outward and scaled; the pose is popped again on return.
 *
 * <p>26.3 renamed {@code ItemInHandRenderer} to {@code FirstPersonHandsAndItemsRenderer}; the old name is kept as a
 * second target for 26.2 and earlier (the missing one is skipped with a warning). The method is
 * {@code submitArmWithItem} ({@code renderArmWithItem} before 26.2).</p>
 */
@Mixin(targets = {
		"net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer",
		"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class FirstPersonItemMixin {
	@Unique
	private boolean murmels$pushed;

	@Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"), require = 0)
	private void murmels$shieldHead(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
									float swing, ItemStack stack, float equip, PoseStack pose,
									SubmitNodeCollector collector, int light, CallbackInfo ci) {
		murmels$pushed = false;
		ModConfig c = ConfigManager.get();
		if (!c.sideShieldEnabled || !stack.is(Items.SHIELD)) return;
		if (c.shieldCenterWhileBlocking && player.isUsingItem() && player.getUsedItemHand() == hand) return;

		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.pushPose();
		pose.translate(side * c.shieldSide * 0.6f, -c.shieldDown * 0.6f, 0f);
		pose.rotate(Axis.ZP.rotationDegrees(side * c.shieldTilt)); // 26.3: PoseStack.rotate(Quaternionfc) replaced mulPose
		pose.scale(c.shieldScale, c.shieldScale, c.shieldScale);
		murmels$pushed = true;
		HookStatus.SIDE_SHIELD.hit();
	}

	@Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("RETURN"), require = 0)
	private void murmels$shieldReturn(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
									  float swing, ItemStack stack, float equip, PoseStack pose,
									  SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (murmels$pushed) {
			pose.popPose();
			murmels$pushed = false;
		}
	}
}
