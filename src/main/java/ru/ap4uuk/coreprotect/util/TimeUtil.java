package ru.ap4uuk.coreprotect.util;

public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Парсит строку вида "90s", "10m", "2h", "1h30m", "2d3h15m" в секунды.
     * Допустимые суффиксы: s, m, h, d.
     */
    public static int parseDurationSeconds(String input) throws ParameterException {
        input = input.trim().toLowerCase();
        if (input.isEmpty()) {
            throw new ParameterException("message.coreprotect.params.time_empty");
        }

        int totalSeconds = 0;
        int currentNumber = 0;
        boolean hasUnit = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (Character.isDigit(c)) {
                currentNumber = currentNumber * 10 + (c - '0');
            } else {
                if (currentNumber == 0) {
                    throw new ParameterException("message.coreprotect.params.time_missing_number", input);
                }
                hasUnit = true;
                switch (c) {
                    case 's' -> totalSeconds += currentNumber;
                    case 'm' -> totalSeconds += currentNumber * 60;
                    case 'h' -> totalSeconds += currentNumber * 60 * 60;
                    case 'd' -> totalSeconds += currentNumber * 60 * 60 * 24;
                    default -> throw new ParameterException("message.coreprotect.params.time_unknown_unit", c, input);
                }
                currentNumber = 0;
            }
        }

        if (currentNumber != 0 && !hasUnit) {
            // Просто число без суффикса — считаем секундами
            totalSeconds += currentNumber;
        }

        if (totalSeconds <= 0) {
            throw new ParameterException("message.coreprotect.params.time_non_positive", input);
        }

        return totalSeconds;
    }
}
