package ru.ap4uuk.coreprotect;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import ru.ap4uuk.coreprotect.config.CoreprotectConfig;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Mod(Coreprotect.MODID)
public class Coreprotect {

    public static final String MODID = "coreprotect";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Coreprotect() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем COMMON-конфиг
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CoreprotectConfig.COMMON_SPEC);

        LOGGER.info("[Coreprotect] Мод инициализирован (modid={}).", MODID);
    }
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Coreprotect.LOGGER.info("[Coreprotect] onServerStarting вызван.");

        String storageTypeRaw = CoreprotectConfig.COMMON.storageType.get();
        String storageType = storageTypeRaw == null ? "SQLITE" : storageTypeRaw.toUpperCase(Locale.ROOT);

        if ("SQLITE".equals(storageType)) {
            String pathStr = CoreprotectConfig.COMMON.sqlitePath.get();
            Path dbPath = Paths.get(pathStr);
            DatabaseManager.initSQLite(dbPath);
            Coreprotect.LOGGER.info("[Coreprotect] База данных (SQLite) инициализирована: {}", dbPath.toAbsolutePath());
        } else {
            Coreprotect.LOGGER.error("[Coreprotect] Тип хранилища '{}' не поддерживается. Используйте SQLITE.", storageTypeRaw);
        }
    }

}
