package ru.ap4uuk.coreprotect;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import ru.ap4uuk.coreprotect.config.CoreprotectConfig;

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
}
