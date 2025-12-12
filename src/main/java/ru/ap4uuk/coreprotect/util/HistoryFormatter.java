package ru.ap4uuk.coreprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class HistoryFormatter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private HistoryFormatter() {
    }

    public static Component formatHistoryLine(long epochSeconds,
                                              String playerName,
                                              String actionType,
                                              String oldBlock,
                                              String newBlock) {
        String ts = TIME_FORMATTER.format(Instant.ofEpochSecond(epochSeconds));

        Component time = Component.literal(ts).withStyle(ChatFormatting.GRAY);
        Component player = Component.literal(playerName).withStyle(ChatFormatting.AQUA);
        Component action = TextUtil.actionName(actionType);
        Component oldBlockComponent = TextUtil.blockName(oldBlock);
        Component newBlockComponent = TextUtil.blockName(newBlock);

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
}
