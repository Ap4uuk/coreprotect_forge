package ru.ap4uuk.coreprotect.inspect;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;

public final class InspectTool {
    private static final String TAG_KEY = "CoreprotectInspectTool";

    private InspectTool() {}

    public static ItemStack createToolStack() {
        ItemStack stack = new ItemStack(Items.BEDROCK);
        stack.setHoverName(Component.literal("Coreprotect Inspect Block")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_KEY, true);
        return stack;
    }

    public static boolean isInspectTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() != Items.BEDROCK) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_KEY);
    }
}
