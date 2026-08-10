package nm63ae2;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class NoMore63AE2 implements ModInitializer {

    @Override
    public void onInitialize() {
        var configFile = FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID + ".toml");
        NoMore63AE2Config.load(configFile);
        Constants.LOG.info("[{}] {} loaded. maxItemTypesPerCell = {}",
                Constants.MOD_ID, Constants.DISPLAY_NAME, NoMore63AE2Config.getMaxItemTypesPerCell());
    }
}
