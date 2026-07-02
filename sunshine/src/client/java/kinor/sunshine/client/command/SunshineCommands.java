package kinor.sunshine.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import kinor.sunshine.client.SunshineClient;
import kinor.sunshine.client.config.SunshineConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Registers {@code /sunshine}, a purely client-side command for adjusting
 * Sunshine's settings live. Nothing here talks to the server.
 *
 * <pre>
 * /sunshine                         – print current settings
 * /sunshine enable | disable        – master toggle
 * /sunshine stats                   – toggle the HUD statistics overlay
 * /sunshine maxvisible &lt;1–64&gt;       – cap per cluster
 * /sunshine radius &lt;0.25–16.0&gt;     – grouping cell size in blocks
 * /sunshine reload                  – re-read config/sunshine.json from disk
 * </pre>
 */
public final class SunshineCommands {

    private SunshineCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandBuildContext registryAccess) {
        dispatcher.register(literal("sunshine")
                .executes(SunshineCommands::showStatus)
                .then(literal("enable").executes(ctx -> setEnabled(ctx, true)))
                .then(literal("disable").executes(ctx -> setEnabled(ctx, false)))
                .then(literal("stats").executes(SunshineCommands::toggleStats))
                .then(literal("reload").executes(SunshineCommands::reload))
                .then(literal("maxvisible")
                        .then(argument("count", IntegerArgumentType.integer(
                                SunshineConfig.MIN_VISIBLE, SunshineConfig.MAX_VISIBLE))
                                .executes(SunshineCommands::setMaxVisible)))
                .then(literal("radius")
                        .then(argument("blocks", DoubleArgumentType.doubleArg(
                                SunshineConfig.MIN_RADIUS, SunshineConfig.MAX_RADIUS))
                                .executes(SunshineCommands::setRadius))));
    }

    // -------------------------------------------------------------------------

    private static int showStatus(CommandContext<FabricClientCommandSource> ctx) {
        SunshineConfig cfg = SunshineClient.config();
        ctx.getSource().sendFeedback(Component.literal(String.format(
                "Sunshine: %s | max-visible: %d | radius: %.2f | stats: %s",
                cfg.enabled ? "enabled" : "disabled",
                cfg.maxVisiblePerGroup,
                cfg.groupingRadius,
                cfg.showStatistics ? "shown" : "hidden")));
        return 1;
    }

    private static int setEnabled(CommandContext<FabricClientCommandSource> ctx, boolean enabled) {
        SunshineConfig cfg = SunshineClient.config();
        cfg.enabled = enabled;
        cfg.save();
        ctx.getSource().sendFeedback(
                Component.literal("Sunshine " + (enabled ? "enabled" : "disabled") + "."));
        return 1;
    }

    private static int toggleStats(CommandContext<FabricClientCommandSource> ctx) {
        SunshineConfig cfg = SunshineClient.config();
        cfg.showStatistics = !cfg.showStatistics;
        cfg.save();
        ctx.getSource().sendFeedback(Component.literal(
                "Sunshine statistics overlay " + (cfg.showStatistics ? "shown" : "hidden") + "."));
        return 1;
    }

    private static int setMaxVisible(CommandContext<FabricClientCommandSource> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        SunshineConfig cfg = SunshineClient.config();
        cfg.maxVisiblePerGroup = count;
        cfg.save();
        ctx.getSource().sendFeedback(Component.literal(
                "Sunshine max visible per group set to " + count + "."));
        return 1;
    }

    private static int setRadius(CommandContext<FabricClientCommandSource> ctx) {
        double radius = DoubleArgumentType.getDouble(ctx, "blocks");
        SunshineConfig cfg = SunshineClient.config();
        cfg.groupingRadius = radius;
        cfg.save();
        ctx.getSource().sendFeedback(Component.literal(
                "Sunshine grouping radius set to " + radius + " blocks."));
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> ctx) {
        SunshineClient.reloadConfig();
        ctx.getSource().sendFeedback(
                Component.literal("Sunshine configuration reloaded from disk."));
        return 1;
    }
}
