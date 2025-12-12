package ru.ap4uuk.coreprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.ap4uuk.coreprotect.Coreprotect;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ContainerSnapshotUtil {

    private static final String SLOT_KEY = "Slot";
    private static final String STACK_KEY = "Item";

    private ContainerSnapshotUtil() {
    }

    public static String serializeSlots(java.util.List<net.minecraft.world.inventory.Slot> slots) {
        ListTag list = new ListTag();
        for (net.minecraft.world.inventory.Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            list.add(serializeEntry(slot.getContainerSlot(), stack));
        }
        return list.toString();
    }

    public static String serializeContainer(Container container) {
        ListTag list = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            list.add(serializeEntry(i, stack));
        }
        return list.toString();
    }

    private static CompoundTag serializeEntry(int slotIndex, ItemStack stack) {
        CompoundTag entry = new CompoundTag();
        entry.putInt(SLOT_KEY, slotIndex);
        entry.put(STACK_KEY, stack.save(new CompoundTag()));
        return entry;
    }

    public static Map<Integer, ItemStack> deserializeSnapshot(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return Map.of();
        }
        try {
            Tag parsed = TagParser.parseTag(serialized);
            if (!(parsed instanceof ListTag listTag)) {
                return Map.of();
            }

            Map<Integer, ItemStack> items = new HashMap<>();
            for (Tag element : listTag) {
                if (!(element instanceof CompoundTag entry)) {
                    continue;
                }
                if (!entry.contains(SLOT_KEY) || !entry.contains(STACK_KEY)) {
                    continue;
                }
                int slot = entry.getInt(SLOT_KEY);
                CompoundTag stackTag = entry.getCompound(STACK_KEY);
                ItemStack stack = ItemStack.of(stackTag);
                if (!stack.isEmpty()) {
                    items.put(slot, stack);
                }
            }

            return items;
        } catch (Exception e) {
            Coreprotect.LOGGER.warn("[Coreprotect] Не удалось десериализовать содержимое контейнера '{}': {}", serialized, e.getMessage());
            return Map.of();
        }
    }

    public static boolean isSerializedSnapshot(String serialized) {
        try {
            Tag parsed = TagParser.parseTag(serialized);
            return parsed instanceof ListTag;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Component describeSnapshot(String serialized) {
        Map<Integer, ItemStack> items = deserializeSnapshot(serialized);
        if (items.isEmpty()) {
            return Component.literal("[]").withStyle(ChatFormatting.AQUA);
        }

        String text = items.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(e -> formatEntry(e.getKey(), e.getValue()))
                .collect(Collectors.joining("; ", "[", "]"));

        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    private static String formatEntry(int slot, ItemStack stack) {
        CompoundTag tag = stack.save(new CompoundTag());
        return slot + ":" + tag;
    }

    public static boolean applySnapshot(Level level, BlockPos pos, String serialized) {
        if (serialized == null) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            return false;
        }
        boolean applied = applySnapshot(container, serialized);
        if (applied) {
            blockEntity.setChanged();
        }
        return applied;
    }

    public static boolean applySnapshot(Container container, String serialized) {
        Map<Integer, ItemStack> items = deserializeSnapshot(serialized);
        if (items.isEmpty()) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                container.setItem(i, ItemStack.EMPTY);
            }
            return true;
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = Objects.requireNonNullElse(items.get(i), ItemStack.EMPTY);
            container.setItem(i, stack);
        }
        return true;
    }
}

