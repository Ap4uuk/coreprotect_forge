package ru.ap4uuk.coreprotect.command;

import ru.ap4uuk.coreprotect.util.TimeUtil;

public final class CoParamParser {

    private CoParamParser() {}

    /**
     * Парсит строку вида:
     * "t:1h30m r:10 u:Nick"
     */
    public static RollbackParams parse(String input) throws IllegalArgumentException {
        RollbackParams params = new RollbackParams();

        String[] tokens = input.trim().split("\\s+");
        if (tokens.length == 0) {
            throw new IllegalArgumentException("Параметры не заданы.");
        }

        for (String token : tokens) {
            int idx = token.indexOf(':');
            if (idx <= 0 || idx == token.length() - 1) {
                throw new IllegalArgumentException("Неверный параметр: " + token);
            }

            String key = token.substring(0, idx).toLowerCase();
            String value = token.substring(idx + 1);

            switch (key) {
                case "t", "time" -> {
                    int seconds = TimeUtil.parseDurationSeconds(value);
                    params.seconds = seconds;
                }
                case "r", "radius" -> {
                    // как было...
                }
                case "u", "user" -> {
                    params.playerName = value;
                }
                case "id" -> {
                    try {
                        params.sessionId = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Неверный id: " + value);
                    }
                }
                default -> throw new IllegalArgumentException("Неизвестный параметр: " + key);
            }
        }

        return params;
    }
}
