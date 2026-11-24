package ru.furniture.validator.model;

/**
 * Результат одного правила для одного файла (таблица validation_check_results).
 *
 * @param file файл, к которому относится проверка; null для проверок всего комплекта (FORMATS_COMPLETE)
 */
public record ValidationCheckResult(RuleCode ruleCode, ModelFile file, CheckOutcome outcome, String details) {

    public static ValidationCheckResult passed(RuleCode rule, ModelFile file, String details) {
        return new ValidationCheckResult(rule, file, CheckOutcome.PASSED, details);
    }

    public static ValidationCheckResult failed(RuleCode rule, ModelFile file, String details) {
        return new ValidationCheckResult(rule, file, CheckOutcome.FAILED, details);
    }

    public String fileName() {
        return file == null ? null : file.fileName();
    }
}
