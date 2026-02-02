package sierra.thing.playernamestyler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sierra.thing.playernamestyler.commands.NicknameCommand;
import sierra.thing.playernamestyler.data.NicknameManager;
import sierra.thing.playernamestyler.data.PlayerNameStylerConfig;
import sierra.thing.playernamestyler.events.NicknameEvents;
import sierra.thing.playernamestyler.integrations.NeoForgeCompat;

@Mod(value="playernamestyler")
public class PlayerNameStyler {
    public static final String MOD_ID = "playernamestyler";
    public static final Logger LOGGER = LogManager.getLogger();
    public static NicknameManager nicknameManager;

    public PlayerNameStyler(ModContainer container, IEventBus modEventBus) {
        LOGGER.info("Loading PlayerName Styler...");
        NeoForgeCompat.ensureLegacyAddReloadListenerEventPresent();
        NeoForgeCompat.registerAddReloadListenerBridgeIfPresent();
        container.registerConfig(ModConfig.Type.COMMON, (IConfigSpec)PlayerNameStylerConfig.SPEC);
        modEventBus.addListener(this::onCommonSetup);
        nicknameManager = new NicknameManager();
        NeoForge.EVENT_BUS.register((Object)new NicknameEvents());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        NicknameCommand.register();
        nicknameManager.loadNicknames();
        LOGGER.info("PlayerName Styler setup complete");
    }
}
