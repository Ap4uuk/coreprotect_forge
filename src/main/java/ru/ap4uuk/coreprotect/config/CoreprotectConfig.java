package ru.ap4uuk.coreprotect.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class CoreprotectConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    public static class Common {
        // Тип хранилища: пока поддерживаем только SQLITE
        public final ForgeConfigSpec.ConfigValue<String> storageType;

        // Путь к SQLite-файлу
        public final ForgeConfigSpec.ConfigValue<String> sqlitePath;

        // Сколько записей показывать при инспекции блока
        public final ForgeConfigSpec.IntValue inspectHistoryLimit;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("storage");

            storageType = builder
                    .comment("Тип хранилища: SQLITE (другие типы пока не реализованы)")
                    .define("storageType", "SQLITE");

            sqlitePath = builder
                    .comment("Путь к SQLite файлу относительно корня сервера")
                    .define("sqlitePath", "coreprotect/coreprotect.sqlite");

            builder.pop();

            builder.push("inspect");

            inspectHistoryLimit = builder
                    .comment("Сколько последних записей выводить при /co inspect клике по блоку")
                    .defineInRange("inspectHistoryLimit", 5, 1, 50);

            builder.pop();
        }
    }

    private CoreprotectConfig() {}
}
