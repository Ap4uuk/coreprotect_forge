package ru.ap4uuk.coreprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public final class PaginationUtil {

    private PaginationUtil() {
    }

    public static MutableComponent buildPager(IntFunction<String> commandBuilder, int currentPage, int totalPages) {
        MutableComponent root = Component.literal("");

        root.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
        root.append(navButton("«", currentPage > 1 ? currentPage - 1 : null, commandBuilder));
        root.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));

        for (Component pageButton : pageButtons(commandBuilder, currentPage, totalPages)) {
            root.append(pageButton);
            root.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
        }

        root.append(navButton("»", currentPage < totalPages ? currentPage + 1 : null, commandBuilder));
        root.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));

        return root;
    }

    private static List<Component> pageButtons(IntFunction<String> commandBuilder, int currentPage, int totalPages) {
        List<Component> buttons = new ArrayList<>();

        int start = Math.max(1, currentPage - 2);
        int end = Math.min(totalPages, currentPage + 2);

        addPageButton(buttons, commandBuilder, 1, currentPage);
        if (start > 2) {
            buttons.add(Component.literal("...").withStyle(ChatFormatting.DARK_GRAY));
        }

        for (int page = start; page <= end; page++) {
            if (page == 1 || page == totalPages) continue;
            addPageButton(buttons, commandBuilder, page, currentPage);
        }

        if (end < totalPages - 1) {
            buttons.add(Component.literal("...").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (totalPages > 1) {
            addPageButton(buttons, commandBuilder, totalPages, currentPage);
        }

        return buttons;
    }

    private static void addPageButton(List<Component> buttons,
                                      IntFunction<String> commandBuilder,
                                      int page,
                                      int currentPage) {
        buttons.add(makePageButton(commandBuilder, page, currentPage == page));
    }

    private static Component makePageButton(IntFunction<String> commandBuilder, int page, boolean current) {
        ChatFormatting color = current ? ChatFormatting.GOLD : ChatFormatting.AQUA;
        MutableComponent button = Component.literal(String.valueOf(page)).withStyle(color);

        if (!current) {
            button = button.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandBuilder.apply(page)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("message.coreprotect.pagination.click", page)))
            );
        }

        return button;
    }

    private static Component navButton(String label, Integer targetPage, IntFunction<String> commandBuilder) {
        MutableComponent button = Component.literal(label).withStyle(ChatFormatting.GRAY);
        if (targetPage == null) {
            return button.withStyle(ChatFormatting.DARK_GRAY);
        }

        return button.withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandBuilder.apply(targetPage)))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.coreprotect.pagination.click", targetPage)))
        );
    }
}
