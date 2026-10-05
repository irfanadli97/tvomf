package io.github.irfanadli97.tvomf;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-side {@code /gd656motionhud} command. The {@code config list|edit|reset} branch matches
 * the original mod; the rest is new in this port.
 */
public final class MotionHudCommands {
	private static final List<String> SWAY_OPTIONS = List.of("maxOffset", "damping", "sensitivity", "motionSensitivity", "curveStrength", "sprintCurveStrength",
			"sprintCurveTransition", "sprintZoom", "bobStrength", "sprintBobStrength");
	private static final SuggestionProvider<FabricClientCommandSource> SWAY_OPTION_SUGGESTIONS =
			(context, builder) -> SharedSuggestionProvider.suggest(SWAY_OPTIONS, builder);
	private static final SuggestionProvider<FabricClientCommandSource> ELEMENT_SUGGESTIONS =
			(context, builder) -> SharedSuggestionProvider.suggestResource(HudElements.movable(), builder);

	private MotionHudCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal(MotionHudClient.MOD_ID)
				.then(literal("config")
						.then(literal("list").executes(MotionHudCommands::listConfig))
						.then(literal("edit")
								.then(argument("option", StringArgumentType.word()).suggests(SWAY_OPTION_SUGGESTIONS)
										.then(argument("value", FloatArgumentType.floatArg())
												.executes(MotionHudCommands::editConfig))))
						.then(literal("reset").executes(MotionHudCommands::resetConfig))
						.then(literal("sway")
								.then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
									MotionHudConfig config = MotionHudConfig.get();
									config.swayEnabled = BoolArgumentType.getBool(context, "enabled");
									config.save();
									return success(context, "Set swayEnabled to " + config.swayEnabled);
								})))
						.then(literal("crosshair")
								.then(argument("sways", BoolArgumentType.bool()).executes(context -> {
									MotionHudConfig config = MotionHudConfig.get();
									config.swayCrosshair = BoolArgumentType.getBool(context, "sways");
									config.save();
									return success(context, "Set swayCrosshair to " + config.swayCrosshair);
								})))
						.then(literal("curve")
								.then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
									MotionHudConfig config = MotionHudConfig.get();
									config.curveEnabled = BoolArgumentType.getBool(context, "enabled");
									config.save();
									return success(context, "Set curveEnabled to " + config.curveEnabled);
								})))
						.then(literal("shape")
								.then(literal(MotionHudConfig.SHAPE_SPHERE).executes(context -> setShape(context, MotionHudConfig.SHAPE_SPHERE)))
								.then(literal(MotionHudConfig.SHAPE_CYLINDER).executes(context -> setShape(context, MotionHudConfig.SHAPE_CYLINDER))))
						.then(literal("bob")
								.then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
									MotionHudConfig config = MotionHudConfig.get();
									config.bobEnabled = BoolArgumentType.getBool(context, "enabled");
									config.save();
									return success(context, "Set bobEnabled to " + config.bobEnabled);
								})))
						.then(literal("sprintcurve")
								.then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
									MotionHudConfig config = MotionHudConfig.get();
									config.sprintCurveEnabled = BoolArgumentType.getBool(context, "enabled");
									config.save();
									return success(context, "Set sprintCurveEnabled to " + config.sprintCurveEnabled);
								})))
						.then(literal("reload").executes(context -> {
							MotionHudConfig.load();
							return success(context, "Reloaded config file");
						})))
				.then(literal("element")
						.then(literal("list").executes(MotionHudCommands::listElements))
						.then(literal("move")
								.then(elementArgument()
										.then(argument("x", FloatArgumentType.floatArg())
												.then(argument("y", FloatArgumentType.floatArg())
														.executes(context -> moveElement(context, false))))))
						.then(literal("percent")
								.then(elementArgument()
										.then(argument("x", FloatArgumentType.floatArg(-100, 100))
												.then(argument("y", FloatArgumentType.floatArg(-100, 100))
														.executes(context -> moveElement(context, true))))))
						.then(literal("reset")
								.then(elementArgument().executes(MotionHudCommands::resetElement)))
						.then(literal("resetall").executes(context -> {
							MotionHudConfig config = MotionHudConfig.get();
							config.clearOffsets();
							config.save();
							return success(context, "Reset all HUD element offsets");
						}))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, Identifier> elementArgument() {
		return argument("element", IdentifierArgument.id()).suggests(ELEMENT_SUGGESTIONS);
	}

	private static int setShape(CommandContext<FabricClientCommandSource> context, String shape) {
		MotionHudConfig config = MotionHudConfig.get();
		config.curveShape = shape;
		config.save();
		return success(context, "Set curveShape to " + shape);
	}

	private static int listConfig(CommandContext<FabricClientCommandSource> context) {
		MotionHudConfig config = MotionHudConfig.get();
		success(context, "=== HUD Motion Config List ===");
		success(context, "maxOffset: " + config.maxOffset);
		success(context, "damping: " + config.damping);
		success(context, "sensitivity: " + config.sensitivity);
		success(context, "motionSensitivity: " + config.motionSensitivity);
		success(context, "swayEnabled: " + config.swayEnabled);
		success(context, "swayCrosshair: " + config.swayCrosshair);
		success(context, "curveEnabled: " + config.curveEnabled);
		success(context, "curveStrength: " + config.curveStrength);
		success(context, "curveShape: " + config.curveShape);
		success(context, "sprintCurveEnabled: " + config.sprintCurveEnabled);
		success(context, "sprintCurveStrength: " + config.sprintCurveStrength);
		success(context, "sprintCurveTransition: " + config.sprintCurveTransition);
		success(context, "sprintZoom: " + config.sprintZoom);
		success(context, "bobEnabled: " + config.bobEnabled);
		success(context, "bobStrength: " + config.bobStrength);
		success(context, "sprintBobStrength: " + config.sprintBobStrength);
		return success(context, "======================");
	}

	private static int editConfig(CommandContext<FabricClientCommandSource> context) {
		MotionHudConfig config = MotionHudConfig.get();
		String option = StringArgumentType.getString(context, "option");
		float value = FloatArgumentType.getFloat(context, "value");

		// Ranges are the ones the original mod enforces.
		switch (option.toLowerCase(Locale.ROOT)) {
			case "maxoffset" -> {
				if (value < 0.0f || value > 50.0f) return error(context, "Error: maxOffset must be between 0.0-50.0");
				config.maxOffset = value;
			}
			case "damping" -> {
				if (value < 0.1f || value > 1.0f) return error(context, "Error: damping must be between 0.1-1.0");
				config.damping = value;
			}
			case "sensitivity" -> {
				if (value < 0.01f || value > 1.0f) return error(context, "Error: sensitivity must be between 0.01-1.0");
				config.sensitivity = value;
			}
			case "curvestrength" -> {
				if (value < 0.0f || value > 100.0f) return error(context, "Error: curveStrength must be between 0.0-100.0");
				config.curveStrength = value;
			}
			case "sprintcurvestrength" -> {
				if (value < 0.0f || value > 100.0f) return error(context, "Error: sprintCurveStrength must be between 0.0-100.0");
				config.sprintCurveStrength = value;
			}
			case "sprintcurvetransition" -> {
				if (value < 0.0f || value > 10.0f) return error(context, "Error: sprintCurveTransition must be between 0.0-10.0 seconds");
				config.sprintCurveTransition = value;
			}
			case "sprintzoom" -> {
				if (value < 0.0f || value > HudCurve.MAX_ZOOM) return error(context, "Error: sprintZoom must be between 0.0-20.0 percent");
				config.sprintZoom = value;
			}
			case "bobstrength" -> {
				if (value < 0.0f || value > 20.0f) return error(context, "Error: bobStrength must be between 0.0-20.0");
				config.bobStrength = value;
			}
			case "sprintbobstrength" -> {
				if (value < 0.0f || value > 20.0f) return error(context, "Error: sprintBobStrength must be between 0.0-20.0");
				config.sprintBobStrength = value;
			}
			case "motionsensitivity" -> {
				if (value < 0.0f || value > 100.0f) return error(context, "Error: motionSensitivity must be between 0.0-100.0");
				config.motionSensitivity = value;
			}
			default -> {
				error(context, "Error: Unknown config option '" + option + "'");
				return error(context, "Available options: " + String.join(", ", SWAY_OPTIONS));
			}
		}

		config.save();
		return success(context, "Set " + option + " to " + value);
	}

	private static int resetConfig(CommandContext<FabricClientCommandSource> context) {
		MotionHudConfig config = MotionHudConfig.get();
		config.resetSway();
		config.save();
		success(context, "Reset all config options to default values");
		success(context, "maxOffset: " + config.maxOffset);
		success(context, "damping: " + config.damping);
		success(context, "sensitivity: " + config.sensitivity);
		success(context, "motionSensitivity: " + config.motionSensitivity);
		success(context, "curveStrength: " + config.curveStrength);
		return success(context, "sprintCurveStrength: " + config.sprintCurveStrength);
	}

	private static int listElements(CommandContext<FabricClientCommandSource> context) {
		MotionHudConfig config = MotionHudConfig.get();
		success(context, "=== HUD Elements ===");

		for (Identifier id : HudElements.movable()) {
			MotionHudConfig.ElementOffset offset = config.offsetFor(id);

			if (offset == null) {
				context.getSource().sendFeedback(Component.literal(id.toString()).withStyle(ChatFormatting.GRAY));
			} else {
				context.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
						"%s: %.1f, %.1f px + %.1f%%, %.1f%%", id, offset.x, offset.y, offset.xPercent, offset.yPercent)));
			}
		}

		return 1;
	}

	private static int moveElement(CommandContext<FabricClientCommandSource> context, boolean percent) {
		Identifier id = context.getArgument("element", Identifier.class);

		if (!HudElements.isMovable(id)) {
			return error(context, "Error: Unknown HUD element '" + id + "'. See /" + MotionHudClient.MOD_ID + " element list");
		}

		float x = FloatArgumentType.getFloat(context, "x");
		float y = FloatArgumentType.getFloat(context, "y");
		MotionHudConfig config = MotionHudConfig.get();
		MotionHudConfig.ElementOffset offset = config.getOrCreateOffset(id);

		if (percent) {
			offset.xPercent = x;
			offset.yPercent = y;
		} else {
			offset.x = x;
			offset.y = y;
		}

		config.offsetsChanged();
		config.save();
		return success(context, String.format(Locale.ROOT, "Moved %s to %.1f, %.1f px + %.1f%%, %.1f%% from its normal position",
				id, offset.x, offset.y, offset.xPercent, offset.yPercent));
	}

	private static int resetElement(CommandContext<FabricClientCommandSource> context) {
		Identifier id = context.getArgument("element", Identifier.class);
		MotionHudConfig config = MotionHudConfig.get();

		if (!config.removeOffset(id)) {
			return error(context, "Error: " + id + " has no offset set");
		}

		config.save();
		return success(context, "Reset " + id + " to its normal position");
	}

	private static int success(CommandContext<FabricClientCommandSource> context, String message) {
		context.getSource().sendFeedback(prefixed(message, ChatFormatting.GREEN));
		return 1;
	}

	private static int error(CommandContext<FabricClientCommandSource> context, String message) {
		context.getSource().sendError(prefixed(message, ChatFormatting.RED));
		return 0;
	}

	private static Component prefixed(String message, ChatFormatting color) {
		return Component.literal("[" + MotionHudClient.MOD_ID + "] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(message).withStyle(color));
	}
}
