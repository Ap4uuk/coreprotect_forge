package ru.ap4uuk.coreprotect.container;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.ap4uuk.coreprotect.model.BlockAction;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;
import ru.ap4uuk.coreprotect.util.ContainerSnapshotUtil;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ContainerTrackManager {

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private ContainerTrackManager() {}

    public static void start(ServerPlayer player, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        Container container = ContainerSnapshotUtil.resolveContainer(level, pos, be);
        if (container == null) return;

        String before = ContainerSnapshotUtil.serializeContainer(container);
        SESSIONS.put(player.getUUID(), new Session(level.dimension().location().toString(), pos, before));
    }

    public static void finish(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;

        Level level = player.serverLevel();

        // если игрок уже в другом измерении — просто игнор
        if (!level.dimension().location().toString().equals(session.dimensionId)) return;

        BlockEntity be = level.getBlockEntity(session.pos);
        Container container = ContainerSnapshotUtil.resolveContainer(level, session.pos, be);
        if (container == null) return;

        String after = ContainerSnapshotUtil.serializeContainer(container);

        if (equalsTrim(session.before, after)) {
            return; // не изменилось
        }

        DatabaseManager db = DatabaseManager.get();
        if (db == null) return;

        BlockAction action = new BlockAction(
                BlockAction.Type.CONTAINER,
                player.getUUID(),
                player.getGameProfile().getName(),
                level.dimension(),
                session.pos,
                session.before,
                after,
                Instant.now()
        );

        db.logBlockAction(action);
    }

    private static boolean equalsTrim(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.trim().equals(b.trim());
    }

    private record Session(String dimensionId, BlockPos pos, String before) {}
}
