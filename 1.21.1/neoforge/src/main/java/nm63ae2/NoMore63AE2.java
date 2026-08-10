package nm63ae2;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(Constants.MOD_ID)
public class NoMore63AE2 {

    public NoMore63AE2(IEventBus eventBus) {
        var configFile = FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID + ".toml");
        NoMore63AE2Config.load(configFile);
        Constants.LOG.info("[{}] {} loaded. maxItemTypesPerCell = {}",
                Constants.MOD_ID, Constants.DISPLAY_NAME, NoMore63AE2Config.getMaxItemTypesPerCell());
    }
}
