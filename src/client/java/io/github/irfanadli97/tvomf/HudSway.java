package io.github.irfanadli97.tvomf;

import java.util.function.Consumer;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * The sway offset of the whole HUD, updated once per frame.
 *
 * <p>The model is the original mod's: camera rotation pushes the HUD the opposite way and the
 * offset decays exponentially. The integration differs so the result does not depend on frame
 * rate or frame pacing, and the limit is approached smoothly instead of being a hard stop.
 */
public final class HudSway {
	private static final double MS_PER_TICK = 50.0;
	/** Blocks of vertical camera movement in one frame beyond which it is treated as a teleport. */
	private static final double MAX_HEIGHT_STEP = 8.0;
	/** Offsets below this many GUI pixels are treated as fully settled. */
	private static final double REST_THRESHOLD = 0.02;

	private static double offsetX;
	private static double offsetY;
	private static float lastYaw;
	private static float lastPitch;
	private static double lastHeight;
	private static boolean hasLastAngles;

	/** The game's bob value at full walking speed. */
	private static final float FULL_BOB = 0.1f;
	private static final float BOB_EASE_MS = 150.0f;

	private static float bobAmount;
	private static float bobX;
	private static float bobY;

	private static float appliedX;
	private static float appliedY;
	private static float testX = Float.NaN;
	private static float testY = Float.NaN;
	private static @Nullable Consumer<double[]> trace;

	private HudSway() {
	}

	/** The offset applied to the HUD this frame, in GUI pixels. */
	public static float x() {
		return appliedX;
	}

	public static float y() {
		return appliedY;
	}

	/** For the visual tests: pins the sway to a fixed offset. NaN returns to normal behaviour. */
	public static void setTestOffset(float x, float y) {
		testX = x;
		testY = y;
	}

	public static void update(DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		MotionHudConfig config = MotionHudConfig.get();
		PipShift.startFrame();

		if (!Float.isNaN(testX)) {
			appliedX = testX;
			appliedY = testY;
			return;
		}

		if (minecraft.player == null) {
			hasLastAngles = false;
			offsetX = 0;
			offsetY = 0;
			appliedX = 0;
			appliedY = 0;
			return;
		}

		updateBob(minecraft.player, deltaTracker, config);

		if (!config.swayEnabled) {
			hasLastAngles = false;
			offsetX = 0;
			offsetY = 0;
			appliedX = bobX;
			appliedY = bobY;
			return;
		}

		Camera camera = minecraft.gameRenderer.mainCamera();
		float yaw = camera.yRot();
		float pitch = camera.xRot();
		double height = camera.position().y;

		if (!hasLastAngles) {
			lastYaw = yaw;
			lastPitch = pitch;
			lastHeight = height;
			hasLastAngles = true;
			return;
		}

		float yawDelta = Mth.wrapDegrees(yaw - lastYaw);
		float pitchDelta = pitch - lastPitch;
		double heightDelta = height - lastHeight;
		lastYaw = yaw;
		lastPitch = pitch;
		lastHeight = height;

		double pushX = -yawDelta * config.sensitivity;
		double pushY = -pitchDelta * config.sensitivity;

		// Not in the original mod: the HUD lags behind vertical movement, so it dips when the
		// camera rises (jumping) and lifts when it drops (falling). A jump of this size in one
		// frame is a teleport or respawn, not movement.
		if (Math.abs(heightDelta) < MAX_HEIGHT_STEP) {
			pushY += heightDelta * config.motionSensitivity;
		}

		// The frame time must come from the game's own timer: the camera moved by exactly this
		// much game time since the last frame, and a separately sampled clock drifts against it
		// from frame to frame, which shows up as jitter when the camera moves fast. While the
		// game is paused that timer stands still, so fall back to real time to keep easing back.
		double elapsedTicks = deltaTracker.getGameTimeDeltaTicks();

		if (elapsedTicks <= 0) {
			elapsedTicks = deltaTracker.getRealtimeDeltaTicks();
		}

		// The original adds the whole push and then decays it, which makes the size of the sway
		// depend on how the motion happens to be cut into frames. Treating the push as spread
		// evenly over the frame gives the same offset for the same motion at any frame rate.
		double rate = -Math.log(Mth.clamp(config.damping, 1.0e-4f, 0.9999f));
		double decay = Math.exp(-rate * elapsedTicks);
		double gain = spreadGain(rate * elapsedTicks) * originalStrengthAt60Fps(rate);
		offsetX = offsetX * decay + pushX * gain;
		offsetY = offsetY * decay + pushY * gain;

		// Bounded so a long fall cannot wind up more than it can ease out of quickly.
		double windup = config.maxOffset * 3.0;
		offsetX = Mth.clamp(offsetX, -windup, windup);
		offsetY = Mth.clamp(offsetY, -windup, windup);

		appliedX = (float) softLimit(offsetX, config.maxOffset) + bobX;
		appliedY = (float) softLimit(offsetY, config.maxOffset) + bobY;

		if (trace != null) {
			trace.accept(new double[] {elapsedTicks * MS_PER_TICK, yaw, pitch, height, appliedX, appliedY});
		}
	}

	/**
	 * Not in the original mod: the HUD bobs with the same step cycle that vanilla uses for view
	 * bobbing and the held item, so it stays in time with them. The game's bob value fades in and
	 * out by itself as the player starts and stops walking, and is zero in the air.
	 */
	private static void updateBob(LocalPlayer player, DeltaTracker deltaTracker, MotionHudConfig config) {
		float target = !config.bobEnabled ? 0 : player.isSprinting() ? config.sprintBobStrength : config.bobStrength;
		float elapsedMs = deltaTracker.getRealtimeDeltaTicks() * (float) MS_PER_TICK;
		bobAmount += (target - bobAmount) * (1.0f - (float) Math.exp(-Math.max(0, elapsedMs) / BOB_EASE_MS));

		if (Math.abs(bobAmount) < REST_THRESHOLD && target == 0) {
			bobAmount = 0;
			bobX = 0;
			bobY = 0;
			return;
		}

		ClientAvatarState avatar = player.avatarState();
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float phase = avatar.getBackwardsInterpolatedWalkDistance(partialTick) * Mth.PI;
		float size = avatar.getInterpolatedBob(partialTick) / FULL_BOB * bobAmount;

		bobX = Mth.sin(phase) * 0.5f * size;
		// Less its average, so the HUD bobs around its resting place instead of below it.
		bobY = (Math.abs(Mth.cos(phase)) - 2.0f / Mth.PI) * size;
	}

	/** (1 - e^-u) / u: the share of a push spread evenly over a frame that is left at its end. */
	private static double spreadGain(double u) {
		return u < 1.0e-6 ? 1.0 : (1.0 - Math.exp(-u)) / u;
	}

	/**
	 * How strong the original's sway is at 60 frames per second, relative to the frame-rate
	 * independent form used here, for a given decay rate per tick. Scaling by it keeps settings
	 * tuned against the original feeling the same at that frame rate.
	 */
	private static double originalStrengthAt60Fps(double rate) {
		double u = rate * (1000.0 / 60.0) / MS_PER_TICK;
		return u < 1.0e-6 ? 1.0 : u * Math.exp(-u) / (1.0 - Math.exp(-u));
	}

	/**
	 * Follows {@code value} exactly up to half of {@code max} and then bends smoothly towards
	 * {@code max}, instead of stopping dead at the limit. Values too small to see become zero so
	 * the HUD sits on whole pixels again once it has settled.
	 */
	private static double softLimit(double value, double max) {
		if (max <= 0 || Math.abs(value) < REST_THRESHOLD) {
			return 0;
		}

		double knee = max * 0.5;
		double magnitude = Math.abs(value);

		if (magnitude > knee) {
			magnitude = knee + (max - knee) * Math.tanh((magnitude - knee) / (max - knee));
		}

		return Math.copySign(magnitude, value);
	}

	/** For the visual tests: receives {elapsed ms, yaw, pitch, camera height, offset x, offset y} every frame. */
	public static void setTrace(@Nullable Consumer<double[]> sink) {
		trace = sink;
	}

	/** Runs {@code draw} with this frame's sway applied to everything it draws. */
	public static void applyTo(GuiGraphicsExtractor graphics, Runnable draw) {
		if (appliedX == 0 && appliedY == 0) {
			draw.run();
			return;
		}

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		float x = appliedX;
		float y = appliedY;
		PipShift.add(x, y);

		try {
			pose.translate(x, y);
			draw.run();
		} finally {
			pose.popMatrix();
			PipShift.add(-x, -y);
		}
	}

	/** Rounds a GUI-pixel distance to a whole number of physical pixels, which keeps text sharp. */
	public static float snapToScreenPixel(double guiPixels) {
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		return (float) (Math.rint(guiPixels * guiScale) / guiScale);
	}
}
