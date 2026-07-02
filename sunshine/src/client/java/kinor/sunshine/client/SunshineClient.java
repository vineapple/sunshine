package kinor.sunshine.client;

import kinor.sunshine.Sunshine;
import kinor.sunshine.client.command.SunshineCommands;
import kinor.sunshine.client.config.SunshineConfig;
import kinor.sunshine.client.stacking.ItemStackingManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Client entrypoint for Sunshine.
 *
 * <p>Everything wired up here is purely cosmetic / client-side:
 * <ul>
 *   <li>A per-tick grouping pass that decides which item entities to hide from rendering.</li>
 *   <li>A client-side {@code /sunshine} command for live config changes.</li>
 *   <li>An optional HUD overlay that shows performance statistics.</li>
 * </ul>
 * Nothing here interacts with the server, mutates entity state, or affects
 * gameplay in any way.
 */
// HudRenderCallback was deprecated in fabric-api starting with 1.21.6 and is removed in 26.1.
// It is still fully functional in 1.21.11.
@SuppressWarnings("deprecation")
public class SunshineClient implements ClientModInitializer {

    /**
     * The single, process-global stacking manager.
     *
     * <p>Written once per client tick by {@link ClientTickEvents#END_CLIENT_TICK} and
     * read every rendered frame by
     * {@link kinor.sunshine.client.mixin.EntityRenderDispatcherMixin}. The
     * volatile fields inside the manager make this access pattern safe without
     * locking.
     */
    public static final ItemStackingManager STACKING_MANAGER = new ItemStackingManager();

    private static SunshineConfig config;

    @Override
    public void onInitializeClient() {
        config = SunshineConfig.load();
        Sunshine.LOGGER.info("Sunshine loaded. Optimisation {}.",
                config.enabled ? "enabled" : "disabled");

        // Re-cluster item entities once per game tick (20 Hz), independent of FPS.
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> STACKING_MANAGER.tick(client, config));

        // /sunshine … (client-side only; never reaches the server)
        ClientCommandRegistrationCallback.EVENT.register(SunshineCommands::register);

        // Optional HUD overlay drawn every frame when showStatistics = true.
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (config.showStatistics) {
                renderStatsOverlay(graphics);
            }
        });

        Sunshine.LOGGER.info("Sunshine client initialised successfully.");
    }

    /** Returns the live config instance. Never {@code null} after initialisation. */
    public static SunshineConfig config() {
        return config;
    }

    /** Reloads config from disk. Called by {@code /sunshine reload}. */
    public static void reloadConfig() {
        config = SunshineConfig.load();
        Sunshine.LOGGER.info("Sunshine config reloaded. Optimisation {}.",
                config.enabled ? "enabled" : "disabled");
    }

    // -------------------------------------------------------------------------
    //  Statistics overlay
    // -------------------------------------------------------------------------

    /** X origin for the panel (pixels from the left edge). */
    private static final int OL_X = 4;
    /** Y origin for the panel (pixels from the top edge). */
    private static final int OL_Y = 4;
    /** Approx panel width in pixels (generous – longest line is ~155 px at scale 1). */
    private static final int OL_W = 172;

    // ARGB colour constants used by the overlay.
    private static final int C_BG     = 0x88000000; // 53 % opaque black background
    private static final int C_HEADER = 0xFFFFDD44; // gold  – title
    private static final int C_LABEL  = 0xFFAAAAAA; // gray  – field names
    private static final int C_VALUE  = 0xFFFFFFFF; // white – plain numbers
    private static final int C_GOOD   = 0xFF55FF55; // green – positive indicator
    private static final int C_WARN   = 0xFFFFFF55; // yellow
    private static final int C_BAD    = 0xFFFF5555; // red

    /**
     * Draws an eight-line statistics panel in the top-left corner of the screen.
     *
     * <p>Uses Minecraft's legacy {@code \u00a7} formatting codes inside the strings
     * passed to {@link GuiGraphics#drawString(Font, String, int, int, int)} so that
     * individual values can be coloured differently from their labels without
     * allocating extra {@code Component} objects per frame.
     *
     * <p>This method is only called when {@link SunshineConfig#showStatistics} is
     * {@code true}, so the cost is zero in the common case.
     */
    private static void renderStatsOverlay(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return; // Respect F1 "hide HUD" mode.
        }

        Font font  = mc.font;
        int lineH  = font.lineHeight + 2; // 9 px glyph + 2 px gap = 11 px per row
        int x      = OL_X;
        int y      = OL_Y;
        int rows   = 8;

        // Semi-transparent background panel
        g.fill(x - 2, y - 2, x + OL_W, y + lineH * rows + 2, C_BG);

        // Row 0 — title
        g.drawString(font, "[Sunshine]", x, y, C_HEADER);
        y += lineH;

        // Row 1 — master-switch status
        String statusVal = config.enabled
                ? "\u00a7aEnabled\u00a7r"
                : "\u00a7cDisabled\u00a7r";
        g.drawString(font, lbl("Status  ") + statusVal, x, y, C_LABEL);
        y += lineH;

        ItemStackingManager.Stats s = STACKING_MANAGER.stats();

        // Row 2 — entities tracked this tick
        g.drawString(font, lbl("Tracked ") + num(s.totalTracked()), x, y, C_LABEL);
        y += lineH;

        // Row 3 — groups and how many are being optimised
        String groupDetail = s.groupsOptimized() > 0
                ? " \u00a7a(" + s.groupsOptimized() + " opt)\u00a7r"
                : "";
        g.drawString(font, lbl("Groups  ") + num(s.totalGroups()) + groupDetail, x, y, C_LABEL);
        y += lineH;

        // Row 4 — entities actually rendered
        g.drawString(font, lbl("Rendered") + num(s.visibleCount()), x, y, C_LABEL);
        y += lineH;

        // Row 5 — entities hidden (highlight in green when > 0 to show the mod is doing work)
        int sup = s.suppressedCount();
        String supStr = sup > 0
                ? "\u00a7a" + sup + "\u00a7r"
                : "\u00a7f" + sup;
        g.drawString(font, lbl("Hidden  ") + supStr, x, y, C_LABEL);
        y += lineH;

        // Row 6 — scan duration (green < 0.5 ms, yellow < 1.5 ms, red >= 1.5 ms)
        double ms       = s.lastScanNanos() / 1_000_000.0;
        String msColour = ms < 0.5 ? "\u00a7a" : ms < 1.5 ? "\u00a7e" : "\u00a7c";
        g.drawString(font,
                lbl("Scan    ") + msColour + String.format("%.3f ms\u00a7r", ms),
                x, y, C_LABEL);
        y += lineH;

        // Row 7 — current config summary
        g.drawString(font,
                lbl("Config  ")
                        + "\u00a77cap:\u00a7f" + config.maxVisiblePerGroup
                        + " \u00a77r:\u00a7f" + String.format("%.1f", config.groupingRadius),
                x, y, C_LABEL);
    }

    /**
     * Formats a field name as medium-gray, followed by a colon and a reset code so
     * the value colour is set by the caller.
     */
    private static String lbl(String name) {
        return "\u00a77" + name + ": \u00a7r";
    }

    /** Formats an integer as white. */
    private static String num(int n) {
        return "\u00a7f" + n;
    }
}
