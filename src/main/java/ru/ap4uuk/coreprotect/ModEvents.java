package ru.ap4uuk.coreprotect;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
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
import ru.ap4uuk.coreprotect.util.BlockLogging;
import ru.ap4uuk.coreprotect.util.ContainerSnapshotUtil;
import ru.ap4uuk.coreprotect.util.HistoryFormatter;
import ru.ap4uuk.coreprotect.util.TextUtil;
import ru.ap4uuk.coreprotect.util.WorldEditIntegration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static ru.ap4uuk.coreprotect.Coreprotect.LOGGER;
import static ru.ap4uuk.coreprotect.Coreprotect.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    // Снапшоты контейнеров: берём BEFORE на клике, AFTER на закрытии меню
    private static final Map<UUID, ContainerSnapshot> CONTAINER_SNAPSHOTS = new ConcurrentHashMap<>();

    private static final Map<PistonKey, List<PistonMove>> PISTON_MOVES = new ConcurrentHashMap<>();
    private static final AtomicBoolean SERVER_INITIALIZED = new AtomicBoolean(false);

    // ---------- Жизненный цикл сервера ----------

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        initializeServer();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        InspectManager.clear();
        DatabaseManager.shutdown();
        LOGGER.info("[Coreprotect] База данных остановлена.");
        SERVER_INITIALIZED.set(false);
        CONTAINER_SNAPSHOTS.clear();
        PISTON_MOVES.clear();
    }

    private static void initializeServer() {
        if (!SERVER_INITIALIZED.compareAndSet(false, true)) {
            LOGGER.info("[Coreprotect] Инициализация уже выполнена, пропускаем повторный вызов onServerStarting.");
            return;
        }

        String storageTypeRaw = CoreprotectConfig.COMMON.storageType.get();
        String storageType = storageTypeRaw == null ? "SQLITE" : storageTypeRaw.toUpperCase(Locale.ROOT);

        if ("SQLITE".equals(storageType)) {
            String pathStr = CoreprotectConfig.COMMON.sqlitePath.get();
            Path dbPath = Paths.get(pathStr);
            DatabaseManager.initSQLite(dbPath);
            LOGGER.info("[Coreprotect] База данных (SQLite) инициализирована: {}", dbPath.toAbsolutePath());
        } else {
            LOGGER.error("[Coreprotect] Тип хранилища '{}' не поддерживается. Используйте SQLITE.", storageTypeRaw);
        }

        WorldEditIntegration.tryRegister();
    }

    // ---------- События блоков ----------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (ActionContext.isRollbackInProgress()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        var action = new BlockAction(
                BlockAction.Type.BREAK,
                player.getUUID(),
                player.getGameProfile().getName(),
                level.dimension(),
                pos,
                state,
                null,
                Instant.now()
        );

        var db = DatabaseManager.get();
        if (db != null) db.logBlockAction(action);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (ActionContext.isRollbackInProgress()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        BlockState placedState = event.getPlacedBlock();
        BlockState oldState = event.getBlockSnapshot().getReplacedBlock();

        var action = new BlockAction(
                BlockAction.Type.PLACE,
                player.getUUID(),
                player.getGameProfile().getName(),
                level.dimension(),
                pos,
                oldState,
                placedState,
                Instant.now()
        );

        var db = DatabaseManager.get();
        if (db != null) db.logBlockAction(action);
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (ActionContext.isRollbackInProgress()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Level level = (Level) event.getLevel();
        if (!BlockLogging.isServer(level)) return;

        event.getReplacedBlockSnapshots().forEach(snapshot -> {
            BlockPos pos = snapshot.getPos();
            BlockLogging.log(level, pos, snapshot.getReplacedBlock(), snapshot.getCurrentBlock(), BlockAction.Type.GENERATE, player);
        });
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (ActionContext.isRollbackInProgress()) return;

        Level level = (Level) event.getLevel();
        if (!BlockLogging.isServer(level)) return;

        BlockLogging.log(level, event.getPos(), event.getOriginalState(), event.getNewState(), BlockAction.Type.FLUID, null);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (ActionContext.isRollbackInProgress()) return;

        Level level = event.getLevel();
        if (!BlockLogging.isServer(level)) return;

        ServerPlayer player = event.getExplosion().getIndirectSourceEntity() instanceof ServerPlayer p ? p : null;
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState oldState = level.getBlockState(pos);
            BlockLogging.log(level, pos, oldState, null, BlockAction.Type.EXPLODE, player);
        }
    }

    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (ActionContext.isRollbackInProgress()) return;

        Level level = (Level) event.getLevel();
        if (!BlockLogging.isServer(level)) return;

        var structure = event.getStructureHelper();
        List<PistonMove> moves = structure.getToPush().stream()
                .map(pos -> {
                    BlockState sourceState = level.getBlockState(pos);
                    BlockPos dest = event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                            ? pos.relative(event.getDirection())
                            : pos.relative(event.getDirection().getOpposite());
                    BlockState destState = level.getBlockState(dest);
                    return new PistonMove(pos.immutable(), sourceState, dest.immutable(), destState);
                })
                .collect(Collectors.toList());

        structure.getToDestroy().forEach(pos -> moves.add(
                new PistonMove(pos.immutable(), level.getBlockState(pos), pos.immutable(), level.getBlockState(pos))
        ));

        PISTON_MOVES.put(new PistonKey(level.dimension(), event.getPos().immutable()), moves);
    }

    @SubscribeEvent
    public static void onPistonPost(PistonEvent.Post event) {
        if (ActionContext.isRollbackInProgress()) return;

        Level level = (Level) event.getLevel();
        if (!BlockLogging.isServer(level)) return;

        PistonKey key = new PistonKey(level.dimension(), event.getPos().immutable());
        List<PistonMove> moves = PISTON_MOVES.remove(key);
        if (moves == null) return;

        for (PistonMove move : moves) {
            BlockLogging.log(level, move.sourcePos(), move.oldState(), level.getBlockState(move.sourcePos()), BlockAction.Type.PISTON, null);
            BlockLogging.log(level, move.destPos(), move.destOldState(), level.getBlockState(move.destPos()), BlockAction.Type.PISTON, null);
        }
    }

    // ---------- Инспекция ----------

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!InspectTool.isInspectTool(held)) return;

        // не даём ломать блок инспект-блоком
        event.setCanceled(true);

        if (event.getLevel().isClientSide()) return;

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        inspectPos(player, level, pos);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack held = player.getItemInHand(event.getHand());

        // Если в руке инспект-блок — работаем через него (ПКМ смотрит "позицию установки")
        if (InspectTool.isInspectTool(held)) {
            event.setCanceled(true);

            if (event.getLevel().isClientSide()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }

            Level level = (Level) event.getLevel();

            BlockPos base = event.getPos();
            if (event.getFace() != null) {
                base = base.relative(event.getFace());
            }

            inspectPos(player, level, base);
            player.getInventory().setChanged();
            return;
        }

        // --- ВАЖНО: трекинг контейнеров (до открытия) ---
        // Сохраняем снапшот BEFORE именно здесь, потому что PlayerContainerEvent.Open не даёт нормально получить BlockEntity/pos.
        if (!event.getLevel().isClientSide()) {
            Level level = (Level) event.getLevel();
            BlockPos pos = event.getPos();
            BlockEntity be = level.getBlockEntity(pos);
            net.minecraft.world.Container container = ContainerSnapshotUtil.resolveContainer(level, pos, be);
            if (container != null) {
                String before = ContainerSnapshotUtil.serializeContainer(container);
                CONTAINER_SNAPSHOTS.put(player.getUUID(), new ContainerSnapshot(level.dimension(), pos.immutable(), before));
            }
        }

        // иначе — старый режим /co inspect (по флагу InspectManager)
        if (!InspectManager.isInspecting(player)) return;

        if (event.getLevel().isClientSide()) return;

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        event.setCanceled(true);
        inspectPos(player, level, pos);
    }

    // Закрыли меню → сравнить снапшоты и залогировать изменения
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ContainerSnapshot before = CONTAINER_SNAPSHOTS.remove(player.getUUID());
        if (before == null) return;

        Level level = player.serverLevel();
        if (!Objects.equals(level.dimension(), before.dimension)) return;

        BlockEntity be = level.getBlockEntity(before.pos);
        net.minecraft.world.Container container = ContainerSnapshotUtil.resolveContainer(level, before.pos, be);
        if (container == null) return;

        String after = ContainerSnapshotUtil.serializeContainer(container);
        if (Objects.equals(before.serialized, after)) return;

        if (!BlockLogging.isServer(level)) return;

        BlockLogging.log(level, before.pos, before.serialized, after, BlockAction.Type.CONTAINER, player);
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
            player.sendSystemMessage(HistoryFormatter.formatHistoryLine(h));
        }

        if (hasNext) {
            player.sendSystemMessage(TextUtil.translate("message.coreprotect.inspect.next_hint"));
        }
    }

    // ---------- Защита инспект-блока ----------

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity entity = event.getEntity(); // Forge 1.20.1
        ItemStack stack = entity.getItem();

        if (InspectTool.isInspectTool(stack)) {
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
        if (!(entity instanceof ServerPlayer)) return;

        event.getDrops().removeIf(itemEntity -> InspectTool.isInspectTool(itemEntity.getItem()));
    }

    // ---------- Records ----------

    private record ContainerSnapshot(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos, String serialized) {}

    private record PistonKey(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos) {}

    private record PistonMove(BlockPos sourcePos, BlockState oldState, BlockPos destPos, BlockState destOldState) {}
}
