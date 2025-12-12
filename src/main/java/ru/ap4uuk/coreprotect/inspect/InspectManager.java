package ru.ap4uuk.coreprotect.inspect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InspectManager {

    private static final Set<UUID> INSPECTING = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, InspectSession> INSPECT_SESSIONS = new ConcurrentHashMap<>();

    private InspectManager() {}

    public static boolean toggleInspect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (INSPECTING.contains(uuid)) {
            INSPECTING.remove(uuid);
            return false; // теперь выключен
        } else {
            INSPECTING.add(uuid);
            return true; // теперь включен
        }
    }

    public static boolean isInspecting(ServerPlayer player) {
        return INSPECTING.contains(player.getUUID());
    }

    public static void disable(ServerPlayer player) {
        INSPECTING.remove(player.getUUID());
        INSPECT_SESSIONS.remove(player.getUUID());
    }

    public static void clear() {
        INSPECTING.clear();
        INSPECT_SESSIONS.clear();
    }

    public static InspectSession getSession(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, boolean advancePage) {
        UUID uuid = player.getUUID();
        InspectSession current = INSPECT_SESSIONS.get(uuid);

        if (current != null && current.sameTarget(dimension, pos)) {
            int nextPage = advancePage ? current.page() + 1 : 0;
            current = new InspectSession(dimension, pos, nextPage);
        } else {
            current = new InspectSession(dimension, pos, 0);
        }

        INSPECT_SESSIONS.put(uuid, current);
        return current;
    }

    public static void resetPagination(ServerPlayer player) {
        INSPECT_SESSIONS.remove(player.getUUID());
    }

    public static InspectSession setPage(ServerPlayer player, int page) {
        if (page < 0) return null;

        UUID uuid = player.getUUID();
        InspectSession current = INSPECT_SESSIONS.get(uuid);
        if (current == null) {
            return null;
        }

        InspectSession updated = new InspectSession(current.dimension, current.pos, page);
        INSPECT_SESSIONS.put(uuid, updated);
        return updated;
    }

    public static InspectSession getCurrentSession(ServerPlayer player) {
        return INSPECT_SESSIONS.get(player.getUUID());
    }

    public record InspectSession(ResourceKey<Level> dimension, BlockPos pos, int page) {
        public boolean sameTarget(ResourceKey<Level> otherDimension, BlockPos otherPos) {
            return dimension.equals(otherDimension) && pos.equals(otherPos);
        }
    }
}
