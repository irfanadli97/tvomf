package io.github.irfanadli97.tvomf.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.DoubleFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.jspecify.annotations.Nullable;

/**
 * Records the game's own frames to a file of raw RGB0 pixels, for the clips on the project page.
 * The camera is steered from here too, once per rendered frame, so a turn is as smooth as one
 * made with a mouse.
 */
public final class FrameCapture {
	private record Frame(int[] pixels, int repeats) {
	}

	private static final Frame END = new Frame(new int[0], 0);

	private static volatile boolean active;
	private static volatile boolean finished = true;
	private static volatile double elapsedSeconds;

	private static @Nullable DoubleFunction<float[]> camera;
	private static double seconds;
	private static int fps;
	private static long startNanos;
	private static int framesTaken;
	private static int pending;
	private static int width;
	private static int height;
	private static @Nullable Path metaFile;
	private static @Nullable BlockingQueue<Frame> queue;

	private FrameCapture() {
	}

	/**
	 * Starts recording with the next rendered frame. Call on the client thread.
	 *
	 * @param camera gives {yaw, pitch} for a time in seconds since the start
	 */
	public static void start(Path rawFile, Path meta, double length, int framesPerSecond, DoubleFunction<float[]> cameraPath) {
		BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
		Thread writer = new Thread(() -> write(rawFile, frames), "tvomf-frame-writer");
		writer.setDaemon(true);
		writer.start();

		camera = cameraPath;
		seconds = length;
		fps = framesPerSecond;
		startNanos = 0;
		framesTaken = 0;
		pending = 0;
		metaFile = meta;
		queue = frames;
		elapsedSeconds = 0;
		finished = false;
		active = true;
	}

	public static boolean finished() {
		return finished;
	}

	/** Seconds since the recording started, for timing key presses against it. */
	public static double elapsedSeconds() {
		return elapsedSeconds;
	}

	public static void beforeFrame() {
		if (!active) {
			return;
		}

		long now = System.nanoTime();

		if (startNanos == 0) {
			startNanos = now;
		}

		elapsedSeconds = (now - startNanos) / 1.0e9;
		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null && camera != null) {
			float[] angles = camera.apply(Math.min(elapsedSeconds, seconds));
			// Both the current and the previous value, so the camera is exactly here this frame
			// instead of part-way from wherever it was last tick.
			player.setYRot(angles[0]);
			player.yRotO = angles[0];
			player.setXRot(angles[1]);
			player.xRotO = angles[1];
		}
	}

	public static void afterFrame(GameRenderer gameRenderer) {
		if (!active || startNanos == 0) {
			return;
		}

		BlockingQueue<Frame> frames = queue;
		int wanted = (int) Math.floor(elapsedSeconds * fps) + 1;
		int total = (int) Math.round(seconds * fps);

		if (framesTaken >= total) {
			active = false;
			finishWhenDrained();
			return;
		}

		wanted = Math.min(wanted, total);

		if (wanted <= framesTaken || frames == null) {
			return;
		}

		// A slow frame stands in for the ones that should have been taken while it rendered, so
		// the clip keeps real time.
		int repeats = wanted - framesTaken;
		framesTaken = wanted;
		pending++;

		Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), image -> {
			try (image) {
				width = image.getWidth();
				height = image.getHeight();
				frames.add(new Frame(image.getPixelsABGR(), repeats));
			} finally {
				pending--;
				finishWhenDrained();
			}
		});
	}

	private static void finishWhenDrained() {
		BlockingQueue<Frame> frames = queue;

		if (active || pending > 0 || frames == null) {
			return;
		}

		queue = null;

		try {
			if (metaFile != null) {
				Files.write(metaFile, List.of(width + " " + height + " " + fps + " " + framesTaken));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		frames.add(END);
	}

	private static void write(Path rawFile, BlockingQueue<Frame> frames) {
		try (FileChannel channel = FileChannel.open(rawFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			ByteBuffer buffer = null;

			while (true) {
				Frame frame = frames.take();

				if (frame == END) {
					break;
				}

				if (buffer == null || buffer.capacity() != frame.pixels().length * 4) {
					buffer = ByteBuffer.allocateDirect(frame.pixels().length * 4).order(ByteOrder.LITTLE_ENDIAN);
				}

				buffer.clear();
				// ABGR ints written low byte first come out as R, G, B, A bytes.
				buffer.asIntBuffer().put(frame.pixels());

				for (int i = 0; i < frame.repeats(); i++) {
					buffer.position(0).limit(buffer.capacity());

					while (buffer.hasRemaining()) {
						channel.write(buffer);
					}
				}
			}
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			finished = true;
		}
	}
}
