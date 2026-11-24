package ru.furniture.validator.validation;

import java.util.ArrayList;
import java.util.List;
import ru.furniture.validator.glb.GlbBinaryHeaderValidator;
import ru.furniture.validator.glb.GlbHeaderResult;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ModelFile;
import ru.furniture.validator.model.ModelFormat;
import ru.furniture.validator.model.ProfileRevision;
import ru.furniture.validator.model.RuleCode;
import ru.furniture.validator.model.ValidationCheckResult;

/**
 * Для каждого GLB: переданы ли байты (GLB_CONTENT) и корректен ли заголовок (GLB_HEADER).
 * Без байт заголовок не проверяется, а файл считается проваленным. USDZ внутри не проверяется.
 * Разбор двоичного формата делегирован {@link GlbBinaryHeaderValidator} (композиция).
 */
public final class GlbContentAndHeaderRule implements ValidationRule {

    private final GlbBinaryHeaderValidator headerValidator;

    public GlbContentAndHeaderRule(GlbBinaryHeaderValidator headerValidator) {
        this.headerValidator = headerValidator;
    }

    @Override
    public List<ValidationCheckResult> check(Model3D model, ProfileRevision revision) {
        List<ValidationCheckResult> results = new ArrayList<>();
        for (ModelFile file : model.files()) {
            if (file.format().orElse(null) != ModelFormat.GLB) {
                continue;
            }
            if (!file.hasContent()) {
                results.add(ValidationCheckResult.failed(RuleCode.GLB_CONTENT, file,
                        "Файл '" + file.fileName() + "' (GLB): content_bytes отсутствуют, заголовок не проверен"));
                continue;
            }
            results.add(ValidationCheckResult.passed(RuleCode.GLB_CONTENT, file,
                    "Файл '" + file.fileName() + "': байты содержимого переданы"));
            GlbHeaderResult header = headerValidator.validate(file.contentBytes());
            if (header.valid()) {
                results.add(ValidationCheckResult.passed(RuleCode.GLB_HEADER, file,
                        "Заголовок GLB '" + file.fileName() + "' валиден"));
            } else {
                for (String error : header.errors()) {
                    results.add(ValidationCheckResult.failed(RuleCode.GLB_HEADER, file, error));
                }
            }
        }
        return results;
    }
}
