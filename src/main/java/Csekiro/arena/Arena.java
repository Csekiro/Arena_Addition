package Csekiro.arena;

import Csekiro.arena.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Arena implements ModInitializer {
    public static final String MOD_ID = "arena";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Arena] onInitialize start");
        ModItems.initialize();
        LOGGER.info("[Arena] onInitialize done");
    }
}