package ru.ap4uuk.coreprotect.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
        Block block = resolveBlock(blockId);
        Component name = block.getName();
        return name.copy().withStyle(ChatFormatting.AQUA);
    }

    private static Block resolveBlock(String serialized) {
        String blockId = extractBlockId(serialized);
        ResourceLocation location = ResourceLocation.tryParse(blockId);
        if (location != null) {
            return BuiltInRegistries.BLOCK.getOptional(location).orElse(Blocks.AIR);
        }
        return Blocks.AIR;
    }

    private static String extractBlockId(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return "minecraft:air";
        }

        try {
            CompoundTag tag = TagParser.parseTag(serialized);
            if (tag.contains("Name")) {
                return tag.getString("Name");
            }
        } catch (CommandSyntaxException ignored) {
            // не SNBT — попробуем устаревшие форматы ниже
        }

        int propertyIndex = serialized.indexOf('[');
        if (propertyIndex > 0) {
            return serialized.substring(0, propertyIndex);
        }

        return serialized;
    }
}
