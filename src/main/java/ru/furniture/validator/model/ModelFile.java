package ru.furniture.validator.model;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Файл 3D-модели (таблица model_files).
 *
 * <p>Размер хранится как исходное значение {@code Object}: так некорректный ввод (null, отрицательное,
 * дробное, логическое) можно отличить и забраковать, как в процедурном прототипе. Формат хранится строкой
 * для того же: отсутствующий или неизвестный формат не должен приводить к исключению.
 * Массив байт копируется при создании и при чтении.
 */
public record ModelFile(
        Long id,
        Long modelId,
        String fileName,
        String formatRaw,
        Object sizeBytes,
        byte[] contentBytes,
        String sha256Hash) {

    public ModelFile {
        fileName = (fileName == null || fileName.isEmpty()) ? "model_file" : fileName;
        formatRaw = formatRaw == null ? "" : formatRaw;
        contentBytes = contentBytes == null ? null : contentBytes.clone();
    }

    public static ModelFile of(String fileName, String format, Object sizeBytes, byte[] contentBytes) {
        return new ModelFile(null, null, fileName, format, sizeBytes, contentBytes, null);
    }

    @Override
    public byte[] contentBytes() {
        return contentBytes == null ? null : contentBytes.clone();
    }

    public boolean hasContent() {
        return contentBytes != null;
    }

    /** Формат в верхнем регистре; пустая строка, если формат не указан. */
    public String formatName() {
        return formatRaw.toUpperCase(Locale.ROOT);
    }

    public Optional<ModelFormat> format() {
        return ModelFormat.parse(formatRaw);
    }

    /** Корректный размер: целое число не меньше нуля. Логическое, дробное, null и отрицательное — пусто. */
    public OptionalLong validSize() {
        return switch (sizeBytes) {
            case Long value when value >= 0 -> OptionalLong.of(value);
            case Integer value when value >= 0 -> OptionalLong.of(value);
            case Short value when value >= 0 -> OptionalLong.of(value);
            case Byte value when value >= 0 -> OptionalLong.of(value);
            case null, default -> OptionalLong.empty();
        };
    }
}
