package ru.ap4uuk.coreprotect.command;

import ru.ap4uuk.coreprotect.util.ParameterException;
import ru.ap4uuk.coreprotect.util.TimeUtil;

public final class CoParamParser {

    private CoParamParser() {}

    /**
     * Парсит строку вида:
     * "t:1h30m r:10 u:Nick"
     */
    public static RollbackParams parse(String input) throws ParameterException {
        RollbackParams params = new RollbackParams();

        String[] tokens = input.trim().split("\\s+");
        if (tokens.length == 0) {
            throw new ParameterException("message.coreprotect.params.missing");
        }

        for (String token : tokens) {
            int idx = token.indexOf(':');
            if (idx <= 0 || idx == token.length() - 1) {
                throw new ParameterException("message.coreprotect.params.invalid_pair", token);
            }

            String key = token.substring(0, idx).toLowerCase();
            String value = token.substring(idx + 1);

            switch (key) {
                case "t", "time" -> {
                    int seconds = TimeUtil.parseDurationSeconds(value);
                    params.seconds = seconds;
                }
                case "r", "radius" -> params.radius = parseInt(value, "message.coreprotect.params.radius_invalid");
                case "u", "user" -> {
                    params.playerName = value;
                }
                case "id" -> params.sessionId = parseInt(value, "message.coreprotect.params.id_invalid");
                default -> throw new ParameterException("message.coreprotect.params.unknown_key", key);
            }
        }

        return params;
    }

    public static PurgeParams parsePurge(String input) throws ParameterException {
        PurgeParams params = new PurgeParams();

        String[] tokens = input.trim().split("\\s+");
        if (tokens.length == 0) {
            throw new ParameterException("message.coreprotect.params.missing");
        }

        for (String token : tokens) {
            if (token.equalsIgnoreCase("#optimize")) {
                params.optimize = true;
                continue;
            }

            int idx = token.indexOf(':');
            if (idx <= 0 || idx == token.length() - 1) {
                throw new ParameterException("message.coreprotect.params.invalid_pair", token);
            }

            String key = token.substring(0, idx).toLowerCase();
            String value = token.substring(idx + 1);

            switch (key) {
                case "t", "time" -> params.seconds = TimeUtil.parseDurationSeconds(value);
                case "r", "region", "world" -> params.dimension = CommandVariableResolver.parseDimension(value);
                case "i", "include" -> {
                    for (String block : value.split(",")) {
                        if (!block.isBlank()) {
                            params.includeBlocks.add(block.trim());
                        }
                    }
                }
                default -> throw new ParameterException("message.coreprotect.params.unknown_key", key);
            }
        }

        if (params.seconds == null) {
            throw new ParameterException("message.coreprotect.params.time_missing");
        }

        return params;
    }

    private static Integer parseInt(String value, String translationKey) throws ParameterException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ParameterException(translationKey, value);
        }
    }
}
