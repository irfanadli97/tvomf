package io.github.irfanadli97.tvomf.test.mixin;

import io.github.irfanadli97.tvomf.test.FrameCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: gives {@link FrameCapture} a call before and after every rendered frame. */
@Mixin(GameRenderer.class)
public abstract class GameRendererCaptureMixin {
	@Inject(method = "extract", at = @At("HEAD"))
	private void tvomftest$beforeFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		FrameCapture.beforeFrame();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void tvomftest$afterFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		FrameCapture.afterFrame((GameRenderer) (Object) this);
	}
}
