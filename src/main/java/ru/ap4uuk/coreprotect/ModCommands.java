package ru.ap4uuk.coreprotect;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.ap4uuk.coreprotect.command.CoParamParser;
import ru.ap4uuk.coreprotect.inspect.InspectManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;
import ru.ap4uuk.coreprotect.storage.DatabaseManager.DbRollbackAction;
import ru.ap4uuk.coreprotect.util.ActionContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import ru.ap4uuk.coreprotect.storage.DatabaseManager.DbForwardAction;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import ru.ap4uuk.coreprotect.inspect.InspectTool;
import net.minecraft.ChatFormatting;

import ru.ap4uuk.coreprotect.command.RollbackParams;
import java.time.Instant;
import java.util.List;



import static ru.ap4uuk.coreprotect.Coreprotect.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> CO_PARAM_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining(); // всё, что введено в этом аргументе
        // Берём только последний "токен" после пробела
        int lastSpace = remaining.lastIndexOf(' ');
        String token = lastSpace == -1 ? remaining : remaining.substring(lastSpace + 1);
        String tokenLower = token.toLowerCase();

        // Начинаем с базовых шаблонов параметров
        List<String> suggestions = new java.util.ArrayList<>();

        // Если только начали писать или пусто — даём все ключи
        if (token.isEmpty()) {
            suggestions.add("t:10m");
            suggestions.add("t:1h");
            suggestions.add("r:5");
            suggestions.add("r:10");
            suggestions.add("u:");
            suggestions.add("id:");
        } else {
            // time
            if ("t:".startsWith(tokenLower) || tokenLower.startsWith("t:")) {
                suggestions.add("t:30s");
                suggestions.add("t:5m");
                suggestions.add("t:10m");
                suggestions.add("t:1h");
            }

            // radius
            if ("r:".startsWith(tokenLower) || tokenLower.startsWith("r:")) {
                suggestions.add("r:5");
                suggestions.add("r:10");
                suggestions.add("r:20");
                suggestions.add("r:50");
            }

            // user / игрок
            if ("u:".startsWith(tokenLower) || tokenLower.startsWith("u:")) {
                // u:<ник_игрока> — подставляем онлайн-игроков
                try {
                    var source = ctx.getSource();
                    var server = source.getServer();
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        suggestions.add("u:" + p.getGameProfile().getName());
                    }
                } catch (Exception ignored) {
                }
            }

            // id (для restore)
            if ("id:".startsWith(tokenLower) || tokenLower.startsWith("id:")) {
                // Просто шаблоны, можно будет потом сделать реальные id из БД
                suggestions.add("id:1");
                suggestions.add("id:2");
                suggestions.add("id:3");
            }

            // Если токен вообще не начинается ни с какого ключа, можно подбросить все "t:/r:/u:/id:"
            if (suggestions.isEmpty()) {
                suggestions.add("t:10m");
                suggestions.add("r:10");
                suggestions.add("u:");
                suggestions.add("id:");
            }
        }

        // Применяем фильтр по последнему токену
        List<String> filtered = suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(tokenLower))
                .toList();

        // И скармливаем Brigadier’у
        return SharedSuggestionProvider.suggest(filtered, builder);
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("co")
                        // /co inspect
                        .then(Commands.literal("inspect")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    boolean on = InspectManager.toggleInspect(player);

                                    Component msg = Component.literal(
                                            "[Coreprotect] Режим инспекции " + (on ? "включён." : "выключен.")
                                    );
                                    player.sendSystemMessage(msg);

                                    return 1;
                                })
                        )
                        // /co rollback <params>
                        .then(Commands.literal("rollback")
                                .then(Commands.argument("params", StringArgumentType.greedyString())
                                        .suggests(CO_PARAM_SUGGESTIONS) // <-- вот тут подсказки
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String paramsStr = StringArgumentType.getString(ctx, "params");
                                            return executeRollback(player, paramsStr);
                                        })
                                )
                        )
                        // /co restore <params>
                        .then(Commands.literal("restore")
                                .then(Commands.argument("params", StringArgumentType.greedyString())
                                        .suggests(CO_PARAM_SUGGESTIONS) // <-- и тут
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String paramsStr = StringArgumentType.getString(ctx, "params");
                                            return executeRestore(player, paramsStr);
                                        })
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("co")
                        // /co inspect (старый режим)
                        .then(Commands.literal("inspect") /* ... как было ... */)
                        // /co rollback ...
                        .then(Commands.literal("rollback") /* ... */)
                        // /co restore ...
                        .then(Commands.literal("restore") /* ... */)
                        // /co tb — выдать / забрать инспекционный блок
                        .then(Commands.literal("tb")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    toggleToolBlock(player);
                                    return 1;
                                })
                        )
        );
    }

    private static void toggleToolBlock(ServerPlayer player) {
        Inventory inv = player.getInventory();

        // ищем инспект-блок у игрока
        int foundSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (InspectTool.isInspectTool(s)) {
                foundSlot = i;
                break;
            }
        }

        if (foundSlot >= 0) {
            // забираем
            inv.removeItem(foundSlot, inv.getItem(foundSlot).getCount());
            player.sendSystemMessage(Component.literal("[Coreprotect] Инспекционный блок убран.")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            // выдаём
            ItemStack tool = InspectTool.createToolStack();
            boolean added = inv.add(tool);
            if (!added) {
                // инвентарь забит — бросаем под ноги, но всё равно недропабельный через наши эвенты
                player.drop(tool, false);
            }
            player.sendSystemMessage(Component.literal("[Coreprotect] Выдан инспекционный блок.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static int executeRollback(ServerPlayer player, String paramsStr) {
        DatabaseManager db = DatabaseManager.get();
        if (db == null) {
            player.sendSystemMessage(Component.literal("[Coreprotect] БД недоступна."));
            return 0;
        }

        RollbackParams params;
        try {
            params = CoParamParser.parse(paramsStr);
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("[Coreprotect] Ошибка параметров: " + e.getMessage()));
            return 0;
        }

        // id:... в rollback игнорируем, он нужен только для restore
        int radius = params.radius != null ? params.radius : 10;
        int seconds = params.seconds != null ? params.seconds : 300;
        String playerFilter = params.playerName;

        Level level = player.serverLevel();
        BlockPos center = player.blockPosition();

        long now = Instant.now().getEpochSecond();
        long since = now - seconds;

        String sessionParamsText = "r=" + radius + ",t=" + seconds +
                (playerFilter != null ? (",u=" + playerFilter) : "");

        int sessionId = db.createRollbackSession(
                player.getGameProfile().getName(),
                sessionParamsText
        );
        if (sessionId <= 0) {
            player.sendSystemMessage(Component.literal("[Coreprotect] Не удалось создать rollback-сессию."));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "[Coreprotect] Rollback (session " + sessionId + "): r=" + radius +
                        ", t=" + seconds + "s" +
                        (playerFilter != null ? (", u=" + playerFilter) : "") +
                        " ..."
        ));

        List<DbRollbackAction> actions = db.getActionsForRollback(
                level.dimension(), center, radius, since, playerFilter
        );

        if (actions.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Coreprotect] Нечего откатывать в заданной зоне/интервале."));
            return 1;
        }

        int applied = 0;

        ActionContext.setRollbackInProgress(true);
        try {
            for (DbRollbackAction a : actions) {
                BlockPos pos = new BlockPos(a.x, a.y, a.z);

                // состояние ДО rollback
                var before = level.getBlockState(pos);
                // целевое состояние (то, что было "old_block")
                var targetState = db.deserializeBlockState(a.oldBlock);

                if (before == targetState) {
                    continue; // можно пропускать, если уже в нужном состоянии
                }

                level.setBlock(pos, targetState, 3);
                applied++;

                // записываем в сессию: before -> after
                db.insertRollbackEntry(sessionId, level.dimension(), pos, before, targetState);
            }
        } finally {
            ActionContext.setRollbackInProgress(false);
        }

        player.sendSystemMessage(Component.literal(
                "[Coreprotect] Rollback завершён. Сессия #" + sessionId +
                        ", применено изменений: " + applied
        ));

        return applied;
    }


    private static int executeRestore(ServerPlayer player, String paramsStr) {
        DatabaseManager db = DatabaseManager.get();
        if (db == null) {
            player.sendSystemMessage(Component.literal("[Coreprotect] БД недоступна."));
            return 0;
        }

        RollbackParams params;
        try {
            params = CoParamParser.parse(paramsStr);
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("[Coreprotect] Ошибка параметров: " + e.getMessage()));
            return 0;
        }

        // Если указан id:... — восстанавливаем конкретную сессию
        if (params.sessionId != null) {
            return executeRestoreSession(player, params.sessionId);
        }

        // Иначе — старое поведение (replay по времени/радиусу)
        int radius = params.radius != null ? params.radius : 10;
        int seconds = params.seconds != null ? params.seconds : 300;
        String playerFilter = params.playerName;

        Level level = player.serverLevel();
        BlockPos center = player.blockPosition();

        long now = Instant.now().getEpochSecond();
        long since = now - seconds;

        player.sendSystemMessage(Component.literal(
                "[Coreprotect] Restore: r=" + radius +
                        ", t=" + seconds + "s" +
                        (playerFilter != null ? (", u=" + playerFilter) : "") +
                        " ..."
        ));

        List<DbForwardAction> actions = db.getActionsForRestore(
                level.dimension(), center, radius, since, playerFilter
        );

        if (actions.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Coreprotect] Нечего восстанавливать в заданной зоне/интервале."));
            return 1;
        }

        int applied = 0;

        ActionContext.setRollbackInProgress(true);
        try {
            for (DbForwardAction a : actions) {
                BlockPos pos = new BlockPos(a.x, a.y, a.z);
                var targetState = db.deserializeBlockState(a.newBlock);
                level.setBlock(pos, targetState, 3);
                applied++;
            }
        } finally {
            ActionContext.setRollbackInProgress(false);
        }

        player.sendSystemMessage(Component.literal(
                "[Coreprotect] Restore завершён. Применено изменений: " + applied
        ));

        return applied;
    }
    private static int executeRestoreSession(ServerPlayer player, int sessionId) {
        DatabaseManager db = DatabaseManager.get();
        if (db == null) {
            player.sendSystemMessage(Component.literal("[Coreprotect] БД недоступна."));
            return 0;
        }

        List<DatabaseManager.RollbackSessionEntry> entries = db.getRollbackEntries(sessionId);
        if (entries.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Coreprotect] Сессия #" + sessionId + " не найдена или пуста."));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "[Coreprotect] Restore сессии #" + sessionId + "..."
        ));

        int applied = 0;

        ActionContext.setRollbackInProgress(true);
        try {
            for (DatabaseManager.RollbackSessionEntry e : entries) {
                // Пока предполагаем, что сессия в том же измерении, где и выполняем команду
                Level level = player.serverLevel();
                BlockPos pos = new BlockPos(e.x, e.y, e.z);

                // Для restore нам нужно вернуть блок к состоянию ДО rollback’а
                var beforeState = db.deserializeBlockState(e.beforeBlock);
                level.setBlock(pos, beforeState, 3);
                applied++;
            }
        } finally {
            ActionContext.setRollbackInProgress(false);
        }

        db.markSessionRestored(sessionId);

        player.sendSystemMessage(Component.literal(
                "[Coreprotect] Restore сессии #" + sessionId + " завершён. Изменений: " + applied
        ));

        return applied;
    }

}
