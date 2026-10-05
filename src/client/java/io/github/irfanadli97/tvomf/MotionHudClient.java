package io.github.irfanadli97.tvomf;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MotionHudClient implements ClientModInitializer {
	public static final String MOD_ID = "tvomf";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Ticks in a world before the hooks are judged; see {@link HookStatus#missingFeatures}. */
	private static final int REPORT_AFTER_TICKS = 100;
	private static int ticksInWorld;
	private static boolean reportedMissing;

	@Override
	public void onInitializeClient() {
		MotionHudConfig.load();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> MotionHudCommands.register(dispatcher));
		ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> HudElements.wrapAll());
		ClientTickEvents.END_CLIENT_TICK.register(MotionHudClient::reportMissingFeatures);
		LOGGER.info("There's a Visor On My Face: initialized");
	}

	/**
	 * On a game version the mod was not built for, some hooks may not have taken (see
	 * {@link HookStatus}). Says so once, in chat, the first time the player is in a world.
	 */
	private static void reportMissingFeatures(Minecraft minecraft) {
		if (reportedMissing || minecraft.player == null || ++ticksInWorld < REPORT_AFTER_TICKS) {
			return;
		}

		reportedMissing = true;

		for (String feature : HookStatus.missingFeatures()) {
			minecraft.player.sendSystemMessage(Component.literal("[" + MOD_ID + "] Not available on this game version: " + feature)
					.withStyle(ChatFormatting.YELLOW));
		}
	}
}
