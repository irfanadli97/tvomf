package io.github.irfanadli97.tvomf.mixin;

import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import io.github.irfanadli97.tvomf.PipShift;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
	@Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
	private void tvomf$recordShift(PictureInPictureRenderState state, CallbackInfo ci) {
		PipShift.record(state);
	}

	@ModifyVariable(method = "addBlitToCurrentLayer", at = @At("HEAD"), argsOnly = true)
	private BlitRenderState tvomf$shiftModel(BlitRenderState blit) {
		return PipShift.shift(blit);
	}
}
