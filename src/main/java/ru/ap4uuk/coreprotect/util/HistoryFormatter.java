package ru.ap4uuk.coreprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.ap4uuk.coreprotect.model.BlockAction;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class HistoryFormatter {

    private HistoryFormatter() {}

    public static Component formatHistoryLine(long epochSeconds,
                                              String playerName,
                                              String actionType,
                                              String oldBlock,
                                              String newBlock) {
        boolean isContainer = BlockAction.Type.CONTAINER.name().equalsIgnoreCase(actionType);
        ContainerSnapshotUtil.ContainerChange containerChange = isContainer
                ? ContainerSnapshotUtil.describeChange(oldBlock, newBlock)
                : null;

        Component time = formatRelativeTime(epochSeconds);
        Component player = Component.literal(playerName == null ? "?" : playerName).withStyle(ChatFormatting.AQUA);
        Component action = TextUtil.actionName(actionType);

        MutableComponent line = TextUtil.prefixed(Component.empty());

        line.append(time);
        line.append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
        line.append(changePrefix(actionType, oldBlock, newBlock, containerChange));
        line.append(player);
        line.append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
        line.append(action);

        Component changeDescription = describeChange(actionType, oldBlock, newBlock, containerChange);
        if (!changeDescription.getString().isBlank()) {
            line.append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
            line.append(changeDescription);
        }

        return line;
    }

    public static Component formatHistoryLine(DatabaseManager.DbBlockAction history) {
        return formatHistoryLine(
                history.timeEpoch,
                history.playerName,
                history.actionType,
                history.oldBlock,
                history.newBlock
        );
    }

    private static Component formatRelativeTime(long epochSeconds) {
        long secondsAgo = Math.max(0, Instant.now().getEpochSecond() - epochSeconds);
        Duration duration = Duration.ofSeconds(secondsAgo);

        long days = duration.toDays();
        duration = duration.minusDays(days);
        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);
        long seconds = duration.getSeconds();

        String[] units = isRussian() ? new String[]{"д", "ч", "м", "с"} : new String[]{"d", "h", "m", "s"};
        StringBuilder sb = new StringBuilder();
        int parts = 0;

        parts = appendUnit(sb, days, units[0], parts);
        parts = appendUnit(sb, hours, units[1], parts);
        parts = appendUnit(sb, minutes, units[2], parts);

        if (sb.length() == 0) {
            appendUnit(sb, seconds, units[3], parts);
        } else if (parts == 1 && seconds > 0) {
            appendUnit(sb, seconds, units[3], parts);
        }

        if (sb.length() == 0) {
            sb.append("0").append(units[3]);
        }

        sb.append(isRussian() ? " назад" : " ago");
        return Component.literal(sb.toString()).withStyle(ChatFormatting.DARK_PURPLE);
    }

    private static int appendUnit(StringBuilder sb, long value, String unit, int parts) {
        if (value <= 0 || parts >= 2) {
            return parts;
        }

        if (sb.length() > 0) sb.append(' ');
        sb.append(value).append(unit);
        return parts + 1;
    }

    private static boolean isRussian() {
        return Locale.getDefault().getLanguage().equalsIgnoreCase("ru");
    }

    private static Component changePrefix(String actionType,
                                          String oldBlock,
                                          String newBlock,
                                          ContainerSnapshotUtil.ContainerChange containerChange) {
        boolean addition = hasAddition(actionType, newBlock, containerChange);
        boolean removal = hasRemoval(actionType, oldBlock, containerChange);

        if (addition && !removal) {
            return Component.literal("+ ").withStyle(ChatFormatting.GREEN);
        }
        if (removal && !addition) {
            return Component.literal("- ").withStyle(ChatFormatting.RED);
        }

        return Component.literal("• ").withStyle(ChatFormatting.YELLOW);
    }

    private static Component describeChange(String actionType,
                                            String oldBlock,
                                            String newBlock,
                                            ContainerSnapshotUtil.ContainerChange containerChange) {
        boolean isContainer = BlockAction.Type.CONTAINER.name().equalsIgnoreCase(actionType);

        if (isContainer && containerChange != null) {
            boolean hasRemoved = hasMeaningfulChange(containerChange.removed());
            boolean hasAdded = hasMeaningfulChange(containerChange.added());

            MutableComponent out = Component.empty();
            if (hasRemoved) {
                out.append(Component.literal("- ").withStyle(ChatFormatting.RED));
                out.append(containerChange.removed().copy());
            }

            if (hasAdded) {
                if (hasRemoved) {
                    out.append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
                }
                out.append(Component.literal("+ ").withStyle(ChatFormatting.GREEN));
                out.append(containerChange.added().copy());
            }

            return out;
        }

        boolean hasOld = !isAir(oldBlock);
        boolean hasNew = !isAir(newBlock);

        if (hasOld && hasNew && !oldBlock.equals(newBlock)) {
            MutableComponent out = Component.empty();
            out.append(TextUtil.blockName(oldBlock).copy().withStyle(ChatFormatting.RED));
            out.append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY));
            out.append(TextUtil.blockName(newBlock).copy().withStyle(ChatFormatting.GREEN));
            return out;
        }

        if (hasNew) {
            return TextUtil.blockName(newBlock).copy();
        }

        if (hasOld) {
            return TextUtil.blockName(oldBlock).copy();
        }

        return Component.empty();
    }

    private static boolean hasMeaningfulChange(Component component) {
        if (component == null) return false;

        String text = component.getString().trim();
        return !text.isEmpty() && !"[]".equals(text);
    }

    private static boolean hasAddition(String actionType,
                                       String newBlock,
                                       ContainerSnapshotUtil.ContainerChange containerChange) {
        if (BlockAction.Type.CONTAINER.name().equalsIgnoreCase(actionType)) {
            return containerChange != null && hasMeaningfulChange(containerChange.added());
        }

        return !isAir(newBlock);
    }

    private static boolean hasRemoval(String actionType,
                                      String oldBlock,
                                      ContainerSnapshotUtil.ContainerChange containerChange) {
        if (BlockAction.Type.CONTAINER.name().equalsIgnoreCase(actionType)) {
            return containerChange != null && hasMeaningfulChange(containerChange.removed());
        }

        return !isAir(oldBlock);
    }

    private static boolean isAir(String serialized) {
        return TextUtil.resolveBlockState(serialized).isAir();
    }
}
