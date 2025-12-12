package ru.ap4uuk.coreprotect;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.ap4uuk.coreprotect.config.CoreprotectConfig;
import ru.ap4uuk.coreprotect.inspect.InspectManager;
import ru.ap4uuk.coreprotect.inspect.InspectTool;
import ru.ap4uuk.coreprotect.model.BlockAction;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;
import ru.ap4uuk.coreprotect.storage.DatabaseManager.DbBlockAction;
import ru.ap4uuk.coreprotect.util.ActionContext;
import ru.ap4uuk.coreprotect.util.TextUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static ru.ap4uuk.coreprotect.Coreprotect.LOGGER;
import static ru.ap4uuk.coreprotect.Coreprotect.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    // ---------- Жизненный цикл сервера ----------

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        String storageType = CoreprotectConfig.COMMON.storageType.get().toUpperCase(Locale.ROOT);

        if ("SQLITE".equals(storageType)) {
            String pathStr = CoreprotectConfig.COMMON.sqlitePath.get();
            Path dbPath = Paths.get(pathStr);
            DatabaseManager.initSQLite(dbPath);
            LOGGER.info("[Coreprotect] База данных (SQLite) инициализирована: {}", dbPath.toAbsolutePath());
        } else {
            LOGGER.error("[Coreprotect] Тип хранилища '{}' не поддерживается. Используйте SQLITE.", storageType);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        InspectManager.clear();
        DatabaseManager.shutdown();
        LOGGER.info("[Coreprotect] База данных остановлена.");
    }

    // ---------- События блоков ----------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (ActionContext.isRollbackInProgress()) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        var state = event.getState();

        LOGGER.debug("[Coreprotect] BREAK: player={}, dim={}, pos=({}, {}, {})",
                player.getGameProfile().getName(),
                level.dimension().location(),
                pos.getX(), pos.getY(), pos.getZ()
        );

        var action = new BlockAction(
                BlockAction.Type.BREAK,
                player.getUUID(),
                player.getGameProfile().getName(),
                level.dimension(),
                pos,
                state,      // oldState — блок, который был
                null,       // newState — после break нет блока
                Instant.now()
        );

        var db = DatabaseManager.get();
        if (db != null) {
            db.logBlockAction(action);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (ActionContext.isRollbackInProgress()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        var placedState = event.getPlacedBlock();
        var oldState = event.getBlockSnapshot().getReplacedBlock();

        LOGGER.debug("[Coreprotect] PLACE: player={}, dim={}, pos=({}, {}, {})",
                player.getGameProfile().getName(),
                level.dimension().location(),
                pos.getX(), pos.getY(), pos.getZ()
        );

        var action = new BlockAction(
                BlockAction.Type.PLACE,
                player.getUUID(),
                player.getGameProfile().getName(),
                level.dimension(),
                pos,
                oldState,       // что было до
                placedState,    // что поставили
                Instant.now()
        );

        var db = DatabaseManager.get();
        if (db != null) {
            db.logBlockAction(action);
        }
    }

    // ---------- Инспекция ----------

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!InspectTool.isInspectTool(held)) {
            return;
        }

        // не даём ломать блок инспект-блоком
        event.setCanceled(true);

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        inspectPos(player, level, pos);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getItemInHand(event.getHand());

        // Если в руке инспект-блок — работаем через него
        if (InspectTool.isInspectTool(held)) {
            event.setCanceled(true);

            Level level = (Level) event.getLevel();

            // Блок, который "должен стоять" — это позиция, куда бы поставился блок при этом ПКМ
            BlockPos base = event.getPos();
            if (event.getFace() != null) {
                base = base.relative(event.getFace());
            }
            BlockPos targetPos = base;

            inspectPos(player, level, targetPos);
            return;
        }

        // иначе — старый режим /co inspect (по флагу InspectManager)
        if (!InspectManager.isInspecting(player)) {
            return;
        }

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        event.setCanceled(true);

        inspectPos(player, level, pos);
    }


    private static void inspectPos(ServerPlayer player, Level level, BlockPos pos) {
        var db = DatabaseManager.get();
        if (db == null) {
            player.sendSystemMessage(TextUtil.translate("message.coreprotect.db_unavailable"));
            return;
        }

        int pageSize = CoreprotectConfig.COMMON.inspectHistoryLimit.get();
        boolean advancePage = player.isShiftKeyDown();
        InspectManager.InspectSession session = InspectManager.getSession(player, level.dimension(), pos, advancePage);
        int offset = session.page() * pageSize;

        List<DbBlockAction> history = db.getBlockHistory(level.dimension(), pos, pageSize + 1, offset);
        boolean hasNext = history.size() > pageSize;
        if (hasNext) {
            history = history.subList(0, pageSize);
        }

        if (history.isEmpty()) {
            if (session.page() > 0) {
                InspectManager.resetPagination(player);
                player.sendSystemMessage(TextUtil.translate("message.coreprotect.inspect.no_more_pages"));
            } else {
                player.sendSystemMessage(TextUtil.translate("message.coreprotect.inspect.no_records"));
            }
            return;
        }

        Component header = TextUtil.translate(
                "message.coreprotect.inspect.header",
                Component.literal(level.dimension().location().toString()).withStyle(ChatFormatting.AQUA),
                Component.literal(pos.getX() + "," + pos.getY() + "," + pos.getZ()).withStyle(ChatFormatting.WHITE)
        );
        player.sendSystemMessage(header);

        player.sendSystemMessage(TextUtil.translate(
                "message.coreprotect.inspect.page",
                Component.literal(String.valueOf(session.page() + 1)).withStyle(ChatFormatting.GOLD)
        ));

        for (DbBlockAction h : history) {
            player.sendSystemMessage(formatHistoryLine(h));
        }

        if (hasNext) {
            player.sendSystemMessage(TextUtil.translate("message.coreprotect.inspect.next_hint"));
        }
    }

    private static Component formatHistoryLine(DbBlockAction history) {
        String ts = TIME_FORMATTER.format(Instant.ofEpochSecond(history.timeEpoch));

        Component time = Component.literal(ts).withStyle(ChatFormatting.GRAY);
        Component playerName = Component.literal(history.playerName).withStyle(ChatFormatting.AQUA);
        Component action = TextUtil.actionName(history.actionType);
        Component oldBlock = TextUtil.blockName(history.oldBlock);
        Component newBlock = TextUtil.blockName(history.newBlock);

        return TextUtil.prefixed(Component.translatable(
                "message.coreprotect.inspect.line",
                time,
                playerName,
                action,
                oldBlock,
                newBlock
        ));
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity entity = event.getEntity();          // вместо getEntityItem()
        ItemStack stack = entity.getItem();

        if (InspectTool.isInspectTool(stack)) {
            // отменяем дроп и возвращаем в инвентарь
            event.setCanceled(true);

            if (event.getPlayer() instanceof ServerPlayer player) {
                player.getInventory().add(stack);
                player.sendSystemMessage(TextUtil.translate("message.coreprotect.inspect.tool_drop_denied"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;

        // убираем инспект-блок из дропа
        event.getDrops().removeIf(itemEntity -> InspectTool.isInspectTool(itemEntity.getItem()));
        // сам предмет при этом исчезает; после респавна игрок может снова взять /co tb
    }

}
