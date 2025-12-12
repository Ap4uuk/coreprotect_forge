package ru.ap4uuk.coreprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.ap4uuk.coreprotect.model.BlockAction;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class HistoryFormatter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private HistoryFormatter() {}

    public static Component formatHistoryLine(long epochSeconds,
                                              String playerName,
                                              String actionType,
                                              String oldBlock,
                                              String newBlock) {
        String ts = TIME_FORMATTER.format(Instant.ofEpochSecond(epochSeconds));

        Component time = Component.literal(ts).withStyle(ChatFormatting.GRAY);
        Component player = Component.literal(playerName == null ? "?" : playerName).withStyle(ChatFormatting.AQUA);
        Component action = TextUtil.actionName(actionType);

        boolean isContainer = BlockAction.Type.CONTAINER.name().equalsIgnoreCase(actionType);

        // Для CONTAINER считаем дифф один раз
        ContainerSnapshotUtil.ContainerChange change = null;
        if (isContainer) {
            change = ContainerSnapshotUtil.describeChange(oldBlock, newBlock);
        }

        Component oldBlockComponent = describeState(actionType, oldBlock, change, true);
        Component newBlockComponent = describeState(actionType, newBlock, change, false);

        return TextUtil.prefixed(Component.translatable(
                "message.coreprotect.inspect.line",
                time,
                player,
                action,
                oldBlockComponent,
                newBlockComponent
        ));
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

    private static Component describeState(String actionType,
                                           String serialized,
                                           ContainerSnapshotUtil.ContainerChange containerChange,
                                           boolean isOldState) {
        boolean isContainer = BlockAction.Type.CONTAINER.name().equalsIgnoreCase(actionType);

        if (isContainer && containerChange != null) {
            // old = removed, new = added
            if (isOldState) {
                MutableComponent out = Component.literal("- ").withStyle(ChatFormatting.RED);
                out.append(containerChange.removed());
                return out;
            } else {
                MutableComponent out = Component.literal("+ ").withStyle(ChatFormatting.GREEN);
                out.append(containerChange.added());
                return out;
            }
        }

        // обычные блоки
        return TextUtil.blockName(serialized);
    }
}
