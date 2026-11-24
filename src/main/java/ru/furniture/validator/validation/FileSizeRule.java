package ru.furniture.validator.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ModelFile;
import ru.furniture.validator.model.ProfileRevision;
import ru.furniture.validator.model.RuleCode;
import ru.furniture.validator.model.ValidationCheckResult;

/**
 * Размер каждого файла: обязателен, целый, не отрицательный и не больше лимита редакции.
 * Размер, равный лимиту, допустим.
 */
public final class FileSizeRule implements ValidationRule {

    private static final double MB = 1024.0 * 1024.0;

    @Override
    public List<ValidationCheckResult> check(Model3D model, ProfileRevision revision) {
        List<ValidationCheckResult> results = new ArrayList<>();
        for (ModelFile file : model.files()) {
            OptionalLong size = file.validSize();
            if (size.isEmpty()) {
                results.add(ValidationCheckResult.failed(RuleCode.FILE_SIZE, file, String.format(Locale.ROOT,
                        "Файл '%s' (%s): размер size_bytes не указан или некорректен",
                        file.fileName(), file.formatName())));
            } else if (size.getAsLong() > revision.maxFileSizeBytes()) {
                results.add(ValidationCheckResult.failed(RuleCode.FILE_SIZE, file, String.format(Locale.ROOT,
                        "Файл '%s' (%s) превышает лимит: %.2f МБ > %.2f МБ",
                        file.fileName(), file.formatName(), size.getAsLong() / MB, revision.maxFileSizeBytes() / MB)));
            } else {
                results.add(ValidationCheckResult.passed(RuleCode.FILE_SIZE, file,
                        "Файл '" + file.fileName() + "' укладывается в весовой лимит"));
            }
        }
        return results;
    }
}
