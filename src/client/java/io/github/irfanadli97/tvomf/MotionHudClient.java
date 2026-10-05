package io.github.irfanadli97.tvomf;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MotionHudClient implements ClientModInitializer {
	public static final String MOD_ID = "tvomf";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		MotionHudConfig.load();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> MotionHudCommands.register(dispatcher));
		ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> HudElements.wrapAll());
		LOGGER.info("There's a Visor On My Face: initialized");
	}
}
