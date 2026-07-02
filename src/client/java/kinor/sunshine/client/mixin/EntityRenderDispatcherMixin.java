package kinor.sunshine.client.mixin;

import kinor.sunshine.client.SunshineClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses rendering of redundant, stacked {@link ItemEntity} instances.
 *
 * <p>{@code EntityRenderDispatcher#shouldRender} is the single gate every
 * entity passes through before the renderer will draw it -- it already
 * decides things like frustum culling and per-renderer visibility. Forcing it
 * to {@code false} for entities Sunshine has hidden means we are using
 * exactly the same mechanism vanilla already uses to skip rendering entities
 * that don't need it, rather than inventing a new code path. Ticking,
 * collision, AI, and networking all happen completely independently of this
 * method, so none of that is affected.
 *
 * <p>This only ever turns a {@code true} result into {@code false}, and only
 * for items chosen by {@link kinor.sunshine.client.stacking.ItemStackingManager}.
 * That makes it safe to run alongside other rendering/culling mods: we never
 * force an entity to render that another mod decided to hide, we only ever
 * add an additional reason to skip one.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private void sunshine$hideStackedItems(Entity entity, Frustum frustum, double camX, double camY,
			double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			// Already not rendering for some other reason (culled, invisible, etc); nothing to do.
			return;
		}
		if (entity instanceof ItemEntity itemEntity
				&& SunshineClient.STACKING_MANAGER.isSuppressed(itemEntity)) {
			cir.setReturnValue(false);
		}
	}
}
