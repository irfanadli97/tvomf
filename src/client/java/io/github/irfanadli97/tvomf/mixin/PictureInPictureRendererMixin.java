package io.github.irfanadli97.tvomf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import io.github.irfanadli97.tvomf.PipShift;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
	// The model's texture is placed on the GUI somewhere inside this call.
	@WrapMethod(method = "prepare")
	private void tvomf$placeShifted(PictureInPictureRenderState state, GuiRenderState guiRenderState,
			FeatureRenderDispatcher featureRenderDispatcher, int guiScale, Operation<Void> original) {
		PipShift.beginPlacing(state);

		try {
			original.call(state, guiRenderState, featureRenderDispatcher, guiScale);
		} finally {
			PipShift.endPlacing();
		}
	}
}
