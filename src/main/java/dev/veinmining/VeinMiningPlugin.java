package dev.veinmining;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;

public final class VeinMiningPlugin extends JavaPlugin {
    public VeinMiningPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        VeinMiningConfig config = VeinMiningConfig.load(getDataDirectory(), getLogger());
        VeinFinder finder = new VeinFinder(config);
        getEntityStoreRegistry().registerSystem(new VeinBreakSystem(finder, config, getLogger()));
        getLogger().atInfo().log("Vein mining up to %d extra blocks for %s%s",
            config.maxBlocks, config.blocks, config.requireCrouch ? " (while crouching)" : "");
    }
}
