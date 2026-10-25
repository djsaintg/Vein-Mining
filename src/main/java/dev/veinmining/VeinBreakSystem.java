package dev.veinmining;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ActiveSlotInventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * When a player breaks an ore block, breaks the rest of its vein too.
 *
 * <p>The vein is broken on the next world tick rather than inside the event, so that if anything
 * else (a land-claim plugin, say) cancels the original break, nothing else is mined either. Each
 * vein block then goes through the game's own block-damage path, as if the player had mined it
 * with one hit: the held tool must be good enough for it, it drops what it normally drops, it costs
 * normal durability, and it fires its own break events that other plugins can cancel.
 */
public final class VeinBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    /** Enough damage to break any block in one hit (block health is 0-1, scaled by tool power). */
    private static final float ONE_HIT = 1_000_000f;
    /** Set while we break a vein, so the breaks we cause don't start veins of their own. */
    private static final ThreadLocal<Boolean> BREAKING_VEIN = ThreadLocal.withInitial(() -> false);

    private final VeinFinder finder;
    private final boolean requireCrouch;
    private final HytaleLogger logger;

    public VeinBreakSystem(VeinFinder finder, VeinMiningConfig config, HytaleLogger logger) {
        super(BreakBlockEvent.class);
        this.finder = finder;
        this.requireCrouch = config.requireCrouch;
        this.logger = logger;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        if (BREAKING_VEIN.get() || event.isCancelled()) {
            return;
        }
        BlockType blockType = event.getBlockType();
        String gatherType = finder.gatherType(blockType);
        if (gatherType == null) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        // Creative breaks blocks instantly anyway, and builders don't want whole veins vanishing.
        if (player == null || player.getGameMode() == GameMode.Creative) {
            return;
        }
        if (requireCrouch && !isCrouching(ref, commandBuffer)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i origin = new Vector3i(event.getTargetBlock());
        int originBlock = VeinFinder.blockAt(world, origin);
        world.execute(() -> breakVein(world, ref, origin, originBlock, gatherType));
    }

    private void breakVein(World world, Ref<EntityStore> ref, Vector3i origin, int originBlock, String gatherType) {
        if (!ref.isValid()) {
            return;
        }
        // Still there means the original break was cancelled after we saw it.
        if (VeinFinder.blockAt(world, origin) == originBlock) {
            return;
        }
        List<Vector3i> vein = finder.find(world, origin, gatherType);
        if (vein.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        ItemStack tool = heldTool(ref, store);
        if (tool == null) {
            return;
        }
        BREAKING_VEIN.set(true);
        try {
            BlockHarvestUtils.performBlockDamage(
                ref, ref, vein, origin, tool, null, null, false, ONE_HIT, 0, false, true,
                store, world.getChunkStore().getStore());
        } catch (RuntimeException e) {
            logger.atWarning().withCause(e).log("Vein mining at %s failed", origin);
        } finally {
            BREAKING_VEIN.set(false);
        }
    }

    /**
     * The tool in the player's active hotbar or utility slot. It has to be the exact stack from
     * the inventory, or the game can't find it again to take durability off it.
     */
    @Nullable
    private static ItemStack heldTool(Ref<EntityStore> ref, Store<EntityStore> store) {
        ItemStack hotbar = activeTool(store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()));
        return hotbar != null ? hotbar : activeTool(store.getComponent(ref, InventoryComponent.Utility.getComponentType()));
    }

    @Nullable
    private static ItemStack activeTool(@Nullable ActiveSlotInventoryComponent inventory) {
        ItemStack stack = inventory != null ? inventory.getActiveItem() : null;
        Item item = stack != null ? stack.getItem() : null;
        return item != null && item.getTool() != null ? stack : null;
    }

    private static boolean isCrouching(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        MovementStatesComponent movement = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType());
        return movement != null && movement.getMovementStates().crouching;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
