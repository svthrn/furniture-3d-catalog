package ru.furniture.validator.glb;

import java.util.List;

/**
 * Результат алгоритма 1. Поля uint32 (версия, длины) хранятся как {@code Long}, чтобы значения
 * выше {@code Integer.MAX_VALUE} не становились отрицательными. null — поле не прочитано.
 */
public record GlbHeaderResult(
        boolean valid,
        String magic,
        Long version,
        Long declaredLength,
        int actualLength,
        Long jsonChunkLength,
        List<String> errors) {

    public GlbHeaderResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
