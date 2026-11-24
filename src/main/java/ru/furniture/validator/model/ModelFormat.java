package ru.furniture.validator.model;

import java.util.Locale;
import java.util.Optional;

/** Формат файла 3D-модели (таблица model_files, поле format). */
public enum ModelFormat {
    GLB,
    USDZ;

    /** Распознаёт формат без учёта регистра; null, пустое и неизвестное значения дают пустой результат. */
    public static Optional<ModelFormat> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.toUpperCase(Locale.ROOT);
        for (ModelFormat format : values()) {
            if (format.name().equals(normalized)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
