package ru.ap4uuk.coreprotect.command;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import ru.ap4uuk.coreprotect.util.ParameterException;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Позволяет использовать плейсхолдеры в строках параметров команд.
 * Например: "t:10m r:5 u:{player}" или "r:{x} t:30s".
 */
public final class CommandVariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([a-z_]+)}", Pattern.CASE_INSENSITIVE);

    private CommandVariableResolver() {
    }

    public static String resolve(ServerPlayer player, String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        Level level = player.level();
        var pos = player.blockPosition();
        ResourceLocation dimensionId = level.dimension().location();

        Map<String, Supplier<String>> providers = Map.of(
                "x", () -> String.valueOf(pos.getX()),
                "y", () -> String.valueOf(pos.getY()),
                "z", () -> String.valueOf(pos.getZ()),
                "world", () -> dimensionId.toString(),
                "dim", () -> dimensionId.toString(),
                "player", () -> player.getGameProfile().getName(),
                "user", () -> player.getGameProfile().getName()
        );

        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            Supplier<String> provider = providers.get(key);
            String replacement = provider != null ? provider.get() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public static ResourceKey<Level> parseDimension(String raw) throws ParameterException {
        String cleaned = raw.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }

        ResourceLocation id = ResourceLocation.tryParse(cleaned);
        if (id == null) {
            throw new ParameterException("message.coreprotect.params.dimension_invalid", raw);
        }

        return ResourceKey.create(Registries.DIMENSION, id);
    }
}
