package ru.ap4uuk.coreprotect.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ru.ap4uuk.coreprotect.model.BlockAction;
import ru.ap4uuk.coreprotect.storage.DatabaseManager;

import java.time.Instant;
import java.util.UUID;

public final class BlockLogging {

    private static final UUID ENVIRONMENT_UUID = UUID.nameUUIDFromBytes("coreprotect:environment".getBytes());
    private static final String ENVIRONMENT_NAME = "#environment";

    private BlockLogging() {
    }

    public static void log(Level level,
                           BlockPos pos,
                           BlockState oldState,
                           BlockState newState,
                           BlockAction.Type type,
                           ServerPlayer player) {
        ResourceKey<Level> dimension = level.dimension();
        UUID actorId = player != null ? player.getUUID() : ENVIRONMENT_UUID;
        String actorName = player != null ? player.getGameProfile().getName() : ENVIRONMENT_NAME;

        DatabaseManager db = DatabaseManager.get();
        if (db == null) {
            return;
        }

        db.logBlockAction(new BlockAction(type, actorId, actorName, dimension, pos, oldState, newState, Instant.now()));
    }

    public static void log(Level level,
                           BlockPos pos,
                           String oldState,
                           String newState,
                           BlockAction.Type type,
                           ServerPlayer player) {
        ResourceKey<Level> dimension = level.dimension();
        UUID actorId = player != null ? player.getUUID() : ENVIRONMENT_UUID;
        String actorName = player != null ? player.getGameProfile().getName() : ENVIRONMENT_NAME;

        DatabaseManager db = DatabaseManager.get();
        if (db == null) {
            return;
        }

        db.logBlockAction(new BlockAction(type, actorId, actorName, dimension, pos, oldState, newState, Instant.now()));
    }

    public static boolean isServer(Level level) {
        return level instanceof ServerLevel;
    }
}
