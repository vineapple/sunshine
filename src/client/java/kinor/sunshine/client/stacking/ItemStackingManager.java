package kinor.sunshine.client.stacking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import kinor.sunshine.client.config.SunshineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only manager that periodically groups visually identical, nearby
 * {@link ItemEntity} instances and decides which ones in each group should
 * actually be drawn.
 *
 * <p><b>This class never touches gameplay.</b> It does not move, remove,
 * despawn, or otherwise mutate any entity. Entities that lose their spot in
 * the "visible" set are left completely alone -- they keep ticking,
 * colliding, bobbing, and can still be walked over and picked up exactly as
 * if Sunshine were not installed. The only thing affected is whether
 * {@code kinor.sunshine.client.mixin.EntityRenderDispatcherMixin} lets the renderer draw them.
 *
 * <p>Grouping happens once per client tick (20/s, independent of FPS) rather
 * than once per rendered frame, since recomputing every frame would be wasted
 * work for a decision that does not need to change that often.
 */
public final class ItemStackingManager {

	/** Entity ids that should currently be skipped by the renderer. Read by the mixin every frame. */
	private volatile IntSet suppressed = IntSets.EMPTY_SET;

	/** Whether the optimization produced any decisions this tick (false while disabled or world-less). */
	private volatile boolean active = false;

	/** The "keep" set chosen last tick for groups over the cap, used to avoid needless flicker. */
	private IntSet lastVisible = IntSets.EMPTY_SET;

	private volatile Stats stats = Stats.EMPTY;

	/**
	 * @return true if this exact entity should currently be hidden from rendering.
	 */
	public boolean isSuppressed(ItemEntity entity) {
		return active && suppressed.contains(entity.getId());
	}

	public Stats stats() {
		return stats;
	}

	/**
	 * Recomputes which item entities should be visible. Intended to be called once per client tick.
	 */
	public void tick(Minecraft client, SunshineConfig config) {
		if (!config.enabled || client.level == null) {
			active = false;
			suppressed = IntSets.EMPTY_SET;
			lastVisible = IntSets.EMPTY_SET;
			stats = Stats.EMPTY;
			return;
		}

		long startNanos = System.nanoTime();

		double cellSize = Math.max(SunshineConfig.MIN_RADIUS, config.groupingRadius);
		int cap = Math.max(1, config.maxVisiblePerGroup);

		Map<ClusterKey, List<ItemEntity>> clusters = new HashMap<>();
		int totalTracked = 0;

		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ItemEntity itemEntity) || itemEntity.isRemoved()) {
				continue;
			}
			ItemStack stack = itemEntity.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			totalTracked++;
			ClusterKey key = ClusterKey.of(stack, itemEntity, cellSize);
			clusters.computeIfAbsent(key, unused -> new ArrayList<>()).add(itemEntity);
		}

		IntSet newSuppressed = new IntOpenHashSet();
		IntSet newVisible = new IntOpenHashSet();
		int groupsOptimized = 0;
		int visibleCount = 0;

		for (List<ItemEntity> group : clusters.values()) {
			int size = group.size();
			if (size <= cap) {
				// Small enough already; nothing to hide.
				visibleCount += size;
				continue;
			}

			groupsOptimized++;
			// Sort for fully deterministic tie-breaking (oldest entity id first).
			group.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

			List<ItemEntity> keep = new ArrayList<>(cap);
			List<ItemEntity> others = new ArrayList<>(size - cap);
			for (ItemEntity entity : group) {
				if (keep.size() < cap && this.lastVisible.contains(entity.getId())) {
					// Prefer keeping whatever was already visible last tick, so the
					// rendered entity doesn't visibly "jump" between piles each tick.
					keep.add(entity);
				} else {
					others.add(entity);
				}
			}
			int stillNeeded = cap - keep.size();
			for (int i = 0; i < others.size() && stillNeeded > 0; i++, stillNeeded--) {
				keep.add(others.get(i));
			}

			IntSet keepIds = new IntOpenHashSet(keep.size());
			for (ItemEntity entity : keep) {
				keepIds.add(entity.getId());
				newVisible.add(entity.getId());
			}
			for (ItemEntity entity : group) {
				if (!keepIds.contains(entity.getId())) {
					newSuppressed.add(entity.getId());
				}
			}
			visibleCount += keep.size();
		}

		this.lastVisible = newVisible;
		this.suppressed = newSuppressed;
		this.active = true;

		long elapsedNanos = System.nanoTime() - startNanos;
		this.stats = new Stats(totalTracked, clusters.size(), groupsOptimized, visibleCount,
				newSuppressed.size(), elapsedNanos);
	}

	/**
	 * Groups entities that share both an {@link ItemIdentity} and a grid cell. Using a grid
	 * instead of pairwise distance checks keeps grouping a single O(n) pass regardless of how
	 * many entities are clustered together, which matters when hundreds of stacks pile up at
	 * once (e.g. breaking a large area of blocks with Fortune/Looting).
	 *
	 * <p>This is an approximation: two entities in the same cell can be up to {@code cellSize *
	 * sqrt(3)} apart rather than strictly within {@code cellSize}, and entities on opposite sides
	 * of a cell boundary won't be grouped even if they are touching. Both are acceptable
	 * trade-offs for a purely visual optimization, and the cell size is exactly the user-facing
	 * "grouping radius" setting, so it stays tunable.
	 */
	private record ClusterKey(ItemIdentity identity, int cellX, int cellY, int cellZ) {
		static ClusterKey of(ItemStack stack, ItemEntity entity, double cellSize) {
			int cellX = (int) Math.floor(entity.getX() / cellSize);
			int cellY = (int) Math.floor(entity.getY() / cellSize);
			int cellZ = (int) Math.floor(entity.getZ() / cellSize);
			return new ClusterKey(ItemIdentity.of(stack), cellX, cellY, cellZ);
		}
	}

	/**
	 * Snapshot of what the last grouping pass did, for the optional statistics overlay.
	 *
	 * @param totalTracked     item entities considered this tick
	 * @param totalGroups      distinct (item, location) groups found
	 * @param groupsOptimized  groups that exceeded the visible cap and had entities hidden
	 * @param visibleCount     item entities actually rendered
	 * @param suppressedCount  item entities hidden from rendering
	 * @param lastScanNanos    time spent building groups and choosing visibility this tick
	 */
	public record Stats(int totalTracked, int totalGroups, int groupsOptimized, int visibleCount,
						 int suppressedCount, long lastScanNanos) {
		public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0L);
	}
}
