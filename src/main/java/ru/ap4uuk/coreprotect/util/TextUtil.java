package ru.ap4uuk.coreprotect.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.Tag;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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

    public static Component blockName(String serialized) {
        BlockState state = resolveBlockState(serialized);
        MutableComponent name = state.getBlock().getName().copy().withStyle(ChatFormatting.AQUA);

        String properties = formatProperties(state);
        if (properties.isEmpty()) {
            return name;
        }

        return name.append(Component.literal(" [" + properties + "]").withStyle(ChatFormatting.GRAY));
    }

    private static String formatProperties(BlockState state) {
        return state.getValues().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .map(TextUtil::formatProperty)
                .collect(Collectors.joining(", "));
    }

    private static String formatProperty(Map.Entry<Property<?>, Comparable<?>> entry) {
        Property<?> property = entry.getKey();
        Comparable<?> value = entry.getValue();

        @SuppressWarnings({"rawtypes", "unchecked"})
        String valueName = ((Property) property).getName(value);

        return property.getName() + "=" + valueName;
    }

    public static BlockState resolveBlockState(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }

        try {
            Tag tag = TagParser.parseTag(serialized);
            DataResult<BlockState> parsed = BlockState.CODEC.parse(NbtOps.INSTANCE, tag);
            if (parsed.result().isPresent()) {
                return parsed.result().get();
            }
        } catch (CommandSyntaxException ignored) {
            // не SNBT — попробуем устаревшие форматы ниже
        }

        try {
            var reader = new StringReader(serialized);
            var parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, false);
            return parsed.blockState();
        } catch (CommandSyntaxException ignored) {
            // устаревший формат
        }

        ResourceLocation location = ResourceLocation.tryParse(serialized);
        if (location != null) {
            return BuiltInRegistries.BLOCK.getOptional(location)
                    .orElse(Blocks.AIR)
                    .defaultBlockState();
        }

        return Blocks.AIR.defaultBlockState();
    }
}
