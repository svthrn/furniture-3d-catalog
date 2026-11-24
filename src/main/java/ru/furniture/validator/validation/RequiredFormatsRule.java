package ru.furniture.validator.validation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ModelFile;
import ru.furniture.validator.model.ModelFormat;
import ru.furniture.validator.model.ProfileRevision;
import ru.furniture.validator.model.RuleCode;
import ru.furniture.validator.model.ValidationCheckResult;

/** Комплектность: в версии модели есть файлы всех обязательных форматов редакции. */
public final class RequiredFormatsRule implements ValidationRule {

    @Override
    public List<ValidationCheckResult> check(Model3D model, ProfileRevision revision) {
        Set<ModelFormat> present = EnumSet.noneOf(ModelFormat.class);
        for (ModelFile file : model.files()) {
            file.format().ifPresent(present::add);
        }
        Set<ModelFormat> missing = EnumSet.noneOf(ModelFormat.class);
        missing.addAll(revision.requiredFormats());
        missing.removeAll(present);

        if (!missing.isEmpty()) {
            return List.of(ValidationCheckResult.failed(RuleCode.FORMATS_COMPLETE, null,
                    "Отсутствуют обязательные форматы: " + join(missing)));
        }
        return List.of(ValidationCheckResult.passed(RuleCode.FORMATS_COMPLETE, null,
                "Все обязательные форматы присутствуют: " + join(revision.requiredFormats())));
    }

    private static String join(Set<ModelFormat> formats) {
        return formats.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
    }
}
