package kinor.sunshine.client.stacking;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Identifies what makes two {@link ItemStack}s "the same item" for rendering
 * purposes: the underlying {@link Item} together with whatever data
 * components differ from that item's defaults (enchantments, custom name,
 * durability, etc). Stack size is intentionally excluded, since two piles of
 * the same enchanted item with different counts should still be visually
 * collapsed together.
 *
 * <p>This mirrors what {@code ItemStack#isSameItemSameComponents} checks, but
 * as an immutable, hashable value so it can be used as a map key while
 * grouping entities.
 */
final class ItemIdentity {

	private final Item item;
	private final DataComponentPatch patch;
	private final int hashCode;

	private ItemIdentity(Item item, DataComponentPatch patch) {
		this.item = item;
		this.patch = patch;
		this.hashCode = Objects.hash(item, patch);
	}

	static ItemIdentity of(ItemStack stack) {
		return new ItemIdentity(stack.getItem(), stack.getComponentsPatch());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ItemIdentity other)) {
			return false;
		}
		// Items are registry singletons, so reference equality is correct and fast.
		return this.item == other.item && this.patch.equals(other.patch);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}
}
