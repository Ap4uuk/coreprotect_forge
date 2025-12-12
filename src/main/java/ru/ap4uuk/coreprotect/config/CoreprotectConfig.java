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

        // Общие настройки подключения к внешним БД
        public final ForgeConfigSpec.ConfigValue<String> connectionUrl;
        public final ForgeConfigSpec.ConfigValue<String> host;
        public final ForgeConfigSpec.IntValue port;
        public final ForgeConfigSpec.ConfigValue<String> databaseName;
        public final ForgeConfigSpec.ConfigValue<String> username;
        public final ForgeConfigSpec.ConfigValue<String> password;
        public final ForgeConfigSpec.BooleanValue useSsl;
        public final ForgeConfigSpec.BooleanValue verifyServerCertificate;
        public final ForgeConfigSpec.IntValue connectionPoolSize;

        // Сколько записей показывать при инспекции блока
        public final ForgeConfigSpec.IntValue inspectHistoryLimit;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("storage");

            storageType = builder
                    .comment("Тип хранилища: SQLITE, MARIADB, POSTGRESQL")
                    .define("storageType", "SQLITE");

            sqlitePath = builder
                    .comment("Путь к SQLite файлу относительно корня сервера")
                    .define("sqlitePath", "coreprotect/coreprotect.sqlite");

            connectionUrl = builder
                    .comment("Полный JDBC URL. Если указан, host/port/databaseName игнорируются")
                    .define("connectionUrl", "");

            host = builder
                    .comment("Хост базы данных для MARIADB/POSTGRESQL")
                    .define("host", "localhost");

            port = builder
                    .comment("Порт базы данных")
                    .defineInRange("port", 0, 0, 65535);

            databaseName = builder
                    .comment("Имя базы данных")
                    .define("databaseName", "coreprotect");

            username = builder
                    .comment("Пользователь базы данных")
                    .define("username", "");

            password = builder
                    .comment("Пароль базы данных")
                    .define("password", "");

            useSsl = builder
                    .comment("Включить SSL при подключении к внешним БД")
                    .define("useSsl", false);

            verifyServerCertificate = builder
                    .comment("Проверять сертификат сервера при SSL (MARIADB)")
                    .define("verifyServerCertificate", true);

            connectionPoolSize = builder
                    .comment("Размер пула подключений для внешних БД")
                    .defineInRange("connectionPoolSize", 4, 1, 64);

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
