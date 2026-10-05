package io.github.irfanadli97.tvomf.mixin;

import io.github.irfanadli97.tvomf.PipShift;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
	// The model's texture is placed on the GUI somewhere inside this method.
	@Inject(method = "prepare", at = @At("HEAD"))
	private void tvomf$beginPlacing(PictureInPictureRenderState state, GuiRenderState guiRenderState,
			FeatureRenderDispatcher featureRenderDispatcher, int guiScale, CallbackInfo ci) {
		PipShift.beginPlacing(state);
	}

	// If the method is left by an exception instead, PipShift.startFrame clears up next frame.
	@Inject(method = "prepare", at = @At("RETURN"))
	private void tvomf$endPlacing(PictureInPictureRenderState state, GuiRenderState guiRenderState,
			FeatureRenderDispatcher featureRenderDispatcher, int guiScale, CallbackInfo ci) {
		PipShift.endPlacing();
	}
}
