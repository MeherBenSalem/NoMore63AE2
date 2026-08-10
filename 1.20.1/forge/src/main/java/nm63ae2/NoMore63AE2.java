package nm63ae2;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(Constants.MOD_ID)
public class NoMore63AE2 {

    public NoMore63AE2() {
        var configFile = FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID + ".toml");
        NoMore63AE2Config.load(configFile);
        Constants.LOG.info("[{}] {} loaded. maxItemTypesPerCell = {}",
                Constants.MOD_ID, Constants.DISPLAY_NAME, NoMore63AE2Config.getMaxItemTypesPerCell());
    }
}
