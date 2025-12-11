package ru.ap4uuk.coreprotect.util;

/**
 * Контекст для служебных операций (rollback и т.п.), чтобы не логировать свои же изменения.
 */
public final class ActionContext {

    private static final ThreadLocal<Boolean> ROLLBACKING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ActionContext() {}

    public static boolean isRollbackInProgress() {
        return ROLLBACKING.get();
    }

    public static void setRollbackInProgress(boolean value) {
        ROLLBACKING.set(value);
    }
}
