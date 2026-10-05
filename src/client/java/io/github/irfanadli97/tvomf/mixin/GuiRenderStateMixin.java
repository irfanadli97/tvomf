package io.github.irfanadli97.tvomf.mixin;

import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
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

	@ModifyVariable(method = "submitBlitToCurrentLayer", at = @At("HEAD"), argsOnly = true)
	private BlitRenderState tvomf$shiftModel(BlitRenderState blit) {
		return PipShift.shift(blit);
	}
}
