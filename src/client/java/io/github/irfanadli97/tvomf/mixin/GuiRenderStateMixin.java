package io.github.irfanadli97.tvomf.mixin;

import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import io.github.irfanadli97.tvomf.HudCurve;
import io.github.irfanadli97.tvomf.PipShift;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
	@Inject(method = "submitPicturesInPictureState", at = @At("HEAD"))
	private void tvomf$recordShift(PictureInPictureRenderState state, CallbackInfo ci) {
		PipShift.record(state);
	}

	// The debug screen and the full-screen overlays are recorded inside a flat section.
	@Inject(method = "submitGuiElement", at = @At("HEAD"))
	private void tvomf$keepFlat(GuiElementRenderState element, CallbackInfo ci) {
		HudCurve.recordFlat(element);
	}

	@Inject(method = "submitText", at = @At("HEAD"))
	private void tvomf$keepTextFlat(GuiTextRenderState text, CallbackInfo ci) {
		HudCurve.recordFlat(text.pose);
	}

	@ModifyVariable(method = "submitBlitToCurrentLayer", at = @At("HEAD"), argsOnly = true)
	private BlitRenderState tvomf$shiftModel(BlitRenderState blit) {
		return PipShift.shift(blit);
	}
}
