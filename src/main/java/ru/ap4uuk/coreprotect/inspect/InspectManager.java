package ru.ap4uuk.coreprotect.inspect;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InspectManager {

    private static final Set<UUID> INSPECTING = ConcurrentHashMap.newKeySet();

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
    }

    public static void clear() {
        INSPECTING.clear();
    }
}
