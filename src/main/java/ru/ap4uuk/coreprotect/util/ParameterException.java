package ru.ap4uuk.coreprotect.util;

/**
 * Исключение для ошибок парсинга параметров с поддержкой локализации.
 */
public class ParameterException extends IllegalArgumentException {

    private final String translationKey;
    private final Object[] args;

    public ParameterException(String translationKey, Object... args) {
        super(translationKey);
        this.translationKey = translationKey;
        this.args = args;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
