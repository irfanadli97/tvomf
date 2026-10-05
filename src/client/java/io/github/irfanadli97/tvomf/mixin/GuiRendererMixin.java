package io.github.irfanadli97.tvomf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import io.github.irfanadli97.tvomf.HudCurve;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void tvomf$beginFrame(CallbackInfo ci) {
		HudCurve.beginFrame();
	}

	// Clipping areas are applied separately from the vertices, so they have to follow the bend
	// too, or bent content slides out from under the area meant to clip it.
	@ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true)
	private ScreenRectangle tvomf$curveScissor(ScreenRectangle area) {
		return HudCurve.bendScissor(area);
	}

	// Every GUI element, including text glyphs and item icons, is turned into vertices here.
	@WrapOperation(
			method = "addElementToMesh",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;buildVertices(Lcom/mojang/blaze3d/vertex/VertexConsumer;)V")
	)
	private void tvomf$curve(GuiElementRenderState element, VertexConsumer consumer, Operation<Void> original) {
		if (HudCurve.appliesTo(element.bounds())) {
			HudCurve.build(element, consumer, curved -> original.call(element, curved));
		} else {
			original.call(element, consumer);
		}
	}
}
