package ru.furniture.validator.validation;

import java.util.List;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ProfileRevision;
import ru.furniture.validator.model.ValidationCheckResult;

/**
 * Одно правило проверки модели по редакции требований площадки. Правило не меняет ни модель,
 * ни редакцию и возвращает результаты в порядке проверки.
 */
public interface ValidationRule {

    List<ValidationCheckResult> check(Model3D model, ProfileRevision revision);
}
