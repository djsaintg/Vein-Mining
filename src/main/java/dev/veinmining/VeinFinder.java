package dev.veinmining;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.joml.Vector3i;

/**
 * Finds the blocks of an ore vein: every ore block connected to a starting point that shares its
 * gather type (so copper in stone and copper in sandstone are one vein, but iron next to it is
 * not). Only loaded chunk sections are looked at; nothing is loaded or generated.
 */
public final class VeinFinder {
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 319;

    private final List<Pattern> patterns;
    private final int maxBlocks;
    private final int[][] offsets;
    /** Gather type of each block-type index, or empty if that block is not ore. */
    private final Map<Integer, Optional<String>> gatherTypes = new ConcurrentHashMap<>();

    public VeinFinder(VeinMiningConfig config) {
        this.patterns = config.blocks.stream().map(VeinFinder::toRegex).toList();
        this.maxBlocks = config.maxBlocks;
        this.offsets = neighbourOffsets(config.includeDiagonals);
    }

    /** The gather type shared by a vein of this block (e.g. {@code OreCopper}), or null if it isn't ore. */
    public String gatherType(BlockType type) {
        if (type == null) {
            return null;
        }
        int index = BlockType.getAssetMap().getIndex(type.getId());
        return index == Integer.MIN_VALUE ? computeGatherType(type) : gatherType(index);
    }

    private String gatherType(int blockIndex) {
        return gatherTypes.computeIfAbsent(blockIndex, i -> {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            return Optional.ofNullable(type == null ? null : computeGatherType(type));
        }).orElse(null);
    }

    private String computeGatherType(BlockType type) {
        // State variants are keyed "*<id>_State_..."
        String id = type.getId();
        String name = id.startsWith("*") ? id.substring(1) : id;
        if (patterns.stream().noneMatch(p -> p.matcher(name).matches())) {
            return null;
        }
        BlockGathering gathering = type.getGathering();
        BlockBreakingDropType breaking = gathering != null ? gathering.getBreaking() : null;
        return breaking != null ? breaking.getGatherType() : null;
    }

    /**
     * The vein around {@code origin}, nearest blocks first, not including {@code origin} itself and
     * at most {@code maxBlocks} long.
     */
    public List<Vector3i> find(World world, Vector3i origin, String gatherType) {
        List<Vector3i> vein = new ArrayList<>();
        if (maxBlocks == 0) {
            return vein;
        }
        Set<Vector3i> seen = new HashSet<>();
        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            Vector3i current = queue.poll();
            for (int[] o : offsets) {
                Vector3i next = new Vector3i(current.x + o[0], current.y + o[1], current.z + o[2]);
                if (!seen.add(next)) {
                    continue;
                }
                int block = blockAt(world, next);
                if (block == Integer.MIN_VALUE || !gatherType.equals(gatherType(block))) {
                    continue;
                }
                vein.add(next);
                if (vein.size() >= maxBlocks) {
                    return vein;
                }
                queue.add(next);
            }
        }
        return vein;
    }

    /** The block-type index at {@code pos}, or {@link Integer#MIN_VALUE} if that section isn't loaded. */
    public static int blockAt(World world, Vector3i pos) {
        if (pos.y < MIN_Y || pos.y > MAX_Y) {
            return Integer.MIN_VALUE;
        }
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> ref = chunks.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (ref == null || !ref.isValid()) {
            return Integer.MIN_VALUE;
        }
        BlockSection section = chunks.getStore().getComponent(ref, BlockSection.getComponentType());
        return section == null ? Integer.MIN_VALUE : section.get(pos.x, pos.y, pos.z);
    }

    private static int[][] neighbourOffsets(boolean diagonals) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (axes == 1 || (diagonals && axes > 1)) {
                        offsets.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return offsets.toArray(int[][]::new);
    }

    private static Pattern toRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        // Block states are separate types named "<id>_State_...".
        regex.append("(_State_.*)?");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}
