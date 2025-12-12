package ru.ap4uuk.coreprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import ru.ap4uuk.coreprotect.Coreprotect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ContainerSnapshotUtil {

    private static final String SLOT_KEY = "Slot";
    private static final String STACK_KEY = "Item";

    // ключ для обёртки списка в compound (для парсера, который ожидает "{...}")
    private static final String WRAP_KEY = "cp_list";

    private ContainerSnapshotUtil() {}

    public static Container resolveContainer(Level level, BlockPos pos, BlockEntity be) {
        if (!(be instanceof Container container)) {
            return null;
        }

        if (!(be instanceof ChestBlockEntity chest)) {
            return container;
        }

        BlockState state = chest.getBlockState();
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return container;
        }

        Direction connectedDir = ChestBlock.getConnectedDirection(state);
        BlockPos otherPos = pos.relative(connectedDir);
        BlockEntity otherBe = level.getBlockEntity(otherPos);
        if (!(otherBe instanceof ChestBlockEntity otherChest)) {
            return container;
        }

        BlockState otherState = otherChest.getBlockState();
        if (!(otherState.getBlock() instanceof ChestBlock) || otherState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return container;
        }

        Container first = chest;
        Container second = otherChest;

        if (state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
            first = otherChest;
            second = chest;
        }

        return new CompoundContainer(first, second);
    }

    public static String serializeSlots(List<net.minecraft.world.inventory.Slot> slots) {
        ListTag list = new ListTag();
        for (net.minecraft.world.inventory.Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            list.add(serializeEntry(slot.getContainerSlot(), stack));
        }
        return list.toString(); // SNBT list: [...]
    }

    public static String serializeContainer(Container container) {
        ListTag list = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            list.add(serializeEntry(i, stack));
        }
        return list.toString(); // SNBT list: [...]
    }

    private static CompoundTag serializeEntry(int slotIndex, ItemStack stack) {
        CompoundTag entry = new CompoundTag();
        entry.putInt(SLOT_KEY, slotIndex);
        entry.put(STACK_KEY, stack.save(new CompoundTag()));
        return entry;
    }

    /**
     * Парсит снапшот контейнера, который хранится как SNBT:
     * - "[]" или "[{...}, {...}]"
     *
     * Важно: TagParser.parseTag ожидает "{...}" (CompoundTag).
     * Поэтому списки мы оборачиваем в compound: "{cp_list:[...]}" и достаем cp_list.
     */
    private static ListTag parseSnapshotList(String serialized) throws Exception {
        serialized = normalizeSerialized(serialized);

        // пустая строка
        if (serialized.isEmpty()) {
            return new ListTag();
        }

        // список SNBT
        if (serialized.startsWith("[")) {
            // оборачиваем в compound, чтобы parseTag не ругался "Expected '{'"
            CompoundTag root = TagParser.parseTag("{" + WRAP_KEY + ":" + serialized + "}");
            // 10 = Tag.TAG_COMPOUND (элементы списка у нас CompoundTag)
            return root.getList(WRAP_KEY, 10);
        }

        // если вдруг кто-то уже хранит как compound {cp_list:[...]}
        if (serialized.startsWith("{")) {
            CompoundTag root = TagParser.parseTag(serialized);

            if (root.contains(WRAP_KEY, 9)) { // 9 = list
                return root.getList(WRAP_KEY, 10);
            }

            // не наш формат
            return new ListTag();
        }

        // неизвестный формат
        return new ListTag();
    }

    private static String normalizeSerialized(String serialized) {
        if (serialized == null) return "";

        serialized = serialized.trim();

        // иногда из SQL прилетает в кавычках
        if ((serialized.startsWith("\"") && serialized.endsWith("\""))
                || (serialized.startsWith("'") && serialized.endsWith("'"))) {
            serialized = serialized.substring(1, serialized.length() - 1).trim();
        }

        return serialized;
    }

    public static Map<Integer, ItemStack> deserializeSnapshot(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return Map.of();
        }

        try {
            ListTag listTag = parseSnapshotList(serialized);
            if (listTag.isEmpty()) {
                return Map.of();
            }

            Map<Integer, ItemStack> items = new HashMap<>();
            for (Tag element : listTag) {
                if (!(element instanceof CompoundTag entry)) continue;
                if (!entry.contains(SLOT_KEY) || !entry.contains(STACK_KEY)) continue;

                int slot = entry.getInt(SLOT_KEY);
                CompoundTag stackTag = entry.getCompound(STACK_KEY);
                ItemStack stack = ItemStack.of(stackTag);

                if (!stack.isEmpty()) {
                    items.put(slot, stack);
                }
            }

            return items;
        } catch (Exception e) {
            Coreprotect.LOGGER.warn(
                    "[Coreprotect] Не удалось десериализовать содержимое контейнера '{}': {}",
                    normalizeSerialized(serialized), e.getMessage()
            );
            return Map.of();
        }
    }

    public static boolean isSerializedSnapshot(String serialized) {
        if (serialized == null || serialized.isBlank()) return false;

        serialized = normalizeSerialized(serialized);

        // быстрый путь
        if (serialized.startsWith("[")) return true;

        try {
            // если кто-то хранит в wrapped формате "{cp_list:[...]}"
            if (serialized.startsWith("{")) {
                CompoundTag root = TagParser.parseTag(serialized);
                return root.contains(WRAP_KEY, 9); // 9 = list
            }
        } catch (Exception ignored) {}

        return false;
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

    public static ContainerChange describeChange(String beforeSerialized, String afterSerialized) {
        if (!isSerializedSnapshot(beforeSerialized) || !isSerializedSnapshot(afterSerialized)) {
            return new ContainerChange(describeSnapshot(beforeSerialized), describeSnapshot(afterSerialized));
        }

        Map<Integer, ItemStack> before = deserializeSnapshot(beforeSerialized);
        Map<Integer, ItemStack> after = deserializeSnapshot(afterSerialized);

        Map<ItemKey, Integer> diffs = new HashMap<>();
        for (int slot : unionSlots(before, after)) {
            ItemStack oldStack = before.getOrDefault(slot, ItemStack.EMPTY);
            ItemStack newStack = after.getOrDefault(slot, ItemStack.EMPTY);

            if (ItemStack.isSameItemSameTags(oldStack, newStack)) {
                int delta = newStack.getCount() - oldStack.getCount();
                if (delta != 0) {
                    accumulate(diffs, oldStack, delta);
                }
                continue;
            }

            if (!oldStack.isEmpty()) {
                accumulate(diffs, oldStack, -oldStack.getCount());
            }
            if (!newStack.isEmpty()) {
                accumulate(diffs, newStack, newStack.getCount());
            }
        }

        Component removed = formatChanges(diffs, false);
        Component added = formatChanges(diffs, true);
        return new ContainerChange(removed, added);
    }

    private static List<Integer> unionSlots(Map<Integer, ItemStack> before, Map<Integer, ItemStack> after) {
        var slots = new java.util.LinkedHashSet<Integer>();
        slots.addAll(before.keySet());
        slots.addAll(after.keySet());
        List<Integer> ordered = new ArrayList<>(slots);
        ordered.sort(Integer::compareTo);
        return ordered;
    }

    private static void accumulate(Map<ItemKey, Integer> diffs, ItemStack stack, int delta) {
        if (stack.isEmpty() || delta == 0) return;

        ItemKey key = ItemKey.from(stack);
        diffs.merge(key, delta, Integer::sum);
        if (diffs.get(key) == 0) {
            diffs.remove(key);
        }
    }

    private static Component formatChanges(Map<ItemKey, Integer> diffs, boolean added) {
        List<Component> entries = diffs.entrySet().stream()
                .filter(e -> added ? e.getValue() > 0 : e.getValue() < 0)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> formatEntry(e.getKey(), Math.abs(e.getValue())))
                .toList();

        if (entries.isEmpty()) {
            return Component.literal("[]").withStyle(ChatFormatting.GRAY);
        }

        MutableComponent out = Component.literal("[").withStyle(ChatFormatting.GRAY);
        out.append(joinComponents(entries));
        out.append(Component.literal("]").withStyle(ChatFormatting.GRAY));
        return out;
    }

    private static Component joinComponents(List<Component> components) {
        if (components.isEmpty()) return Component.empty();

        MutableComponent out = Component.empty();
        boolean first = true;

        for (Component c : components) {
            if (!first) {
                out.append(Component.literal("; ").withStyle(ChatFormatting.GRAY));
            }
            out.append(c);
            first = false;
        }

        return out;
    }

    private static Component formatEntry(ItemKey key, int count) {
        MutableComponent out = Component.literal(count + "x ").withStyle(ChatFormatting.AQUA);
        MutableComponent itemComponent = Component.literal(key.displayName()).withStyle(ChatFormatting.WHITE);

        if (!key.tag().isEmpty()) {
            itemComponent.append(Component.literal(" " + key.tag()).withStyle(ChatFormatting.DARK_GRAY));
        }

        out.append(itemComponent);
        return out;
    }

    private static String formatEntry(int slot, ItemStack stack) {
        CompoundTag tag = stack.save(new CompoundTag());
        return slot + ":" + tag;
    }

    public static boolean applySnapshot(Level level, BlockPos pos, String serialized) {
        if (serialized == null) return false;

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

        // пустой снапшот -> очистить контейнер
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

    public record ContainerChange(Component removed, Component added) {}

    private record ItemKey(ResourceLocation itemId, CompoundTag tag) implements Comparable<ItemKey> {

        private static ItemKey from(ItemStack stack) {
            CompoundTag tag = stack.save(new CompoundTag());
            tag.remove("Count");
            return new ItemKey(BuiltInRegistries.ITEM.getKey(stack.getItem()), tag);
        }

        private String displayName() {
            return itemId().toString();
        }

        @Override
        public int compareTo(ItemKey other) {
            int cmp = this.itemId.compareTo(other.itemId);
            if (cmp != 0) return cmp;
            return this.tag.toString().compareTo(other.tag.toString());
        }
    }
}
