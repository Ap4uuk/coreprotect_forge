package ru.ap4uuk.coreprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class TextUtil {

    private static final MutableComponent PREFIX = Component.literal("[")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("CoreProtect").withStyle(ChatFormatting.DARK_AQUA))
            .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));

    private TextUtil() {
    }

    public static MutableComponent prefixed(Component message) {
        return Component.empty().append(PREFIX).append(message);
    }

    public static MutableComponent translate(String key, Object... args) {
        return prefixed(Component.translatable(key, args).withStyle(ChatFormatting.GRAY));
    }

    public static MutableComponent actionName(String actionType) {
        String key = "message.coreprotect.action." + actionType.toLowerCase(Locale.ROOT);
        return Component.translatable(key).withStyle(ChatFormatting.YELLOW);
    }

    public static Component blockName(String blockId) {
        String value = blockId == null ? "air" : blockId;
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
    }
}
