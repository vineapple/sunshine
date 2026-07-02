package kinor.sunshine.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import kinor.sunshine.Sunshine;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persisted client-side settings for Sunshine.
 *
 * <p>Every field here only changes how aggressively duplicate {@code ItemEntity}
 * renders are hidden; none of them are read by, or sent to, the server, and
 * none of them affect gameplay, item counts, or physics in any way.
 *
 * <p>Settings can be changed in-game with {@code /sunshine ...} (see
 * {@link kinor.sunshine.client.command.SunshineCommands}), or by editing
 * {@code config/sunshine.json} directly while the game is closed.
 */
public final class SunshineConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = Sunshine.MOD_ID + ".json";

	public static final int MIN_VISIBLE = 1;
	public static final int MAX_VISIBLE = 64;
	public static final int DEFAULT_VISIBLE = 3;

	public static final double MIN_RADIUS = 0.25D;
	public static final double MAX_RADIUS = 16.0D;
	public static final double DEFAULT_RADIUS = 1.5D;

	/** Master switch. When false, Sunshine renders every item entity as vanilla would. */
	public boolean enabled = true;

	/** Maximum number of identical, nearby item entities actually rendered per group. */
	public int maxVisiblePerGroup = DEFAULT_VISIBLE;

	/** Side length, in blocks, of the cell used to group nearby identical items together. */
	public double groupingRadius = DEFAULT_RADIUS;

	/** Whether the on-screen statistics overlay is shown. */
	public boolean showStatistics = false;

	public static SunshineConfig load() {
		Path path = configPath();
		if (Files.exists(path)) {
			try (BufferedReader reader = Files.newBufferedReader(path)) {
				SunshineConfig loaded = GSON.fromJson(reader, SunshineConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					return loaded;
				}
			} catch (IOException | JsonParseException e) {
				Sunshine.LOGGER.warn("Failed to read {}, falling back to defaults.", FILE_NAME, e);
			}
		}

		SunshineConfig defaults = new SunshineConfig();
		defaults.save();
		return defaults;
	}

	public void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			Sunshine.LOGGER.warn("Failed to write {}.", FILE_NAME, e);
		}
	}

	/** Clamps every field into its valid range, e.g. after a hand-edited config file. */
	public void sanitize() {
		this.maxVisiblePerGroup = Math.clamp(this.maxVisiblePerGroup, MIN_VISIBLE, MAX_VISIBLE);
		this.groupingRadius = Math.clamp(this.groupingRadius, MIN_RADIUS, MAX_RADIUS);
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
