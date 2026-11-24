package ru.furniture.validator.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Протокол проверки (таблица validation_reports): версия модели, редакция требований, итог и
 * упорядоченные результаты правил. По нему можно восстановить, что проверяли, по каким требованиям
 * и какое правило дало итог.
 *
 * @param revisionId идентификатор редакции; null, если проверка не выполнена (статус ERROR)
 * @param message    пояснение для статуса ERROR
 */
public record ValidationReport(
        Long modelId,
        String sku,
        String platformKey,
        String platformName,
        Long revisionId,
        Integer revisionNo,
        Instant checkedAt,
        ValidationStatus status,
        List<ValidationCheckResult> results,
        String message) {

    public ValidationReport {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static ValidationReport error(Model3D model, String platformKey, Instant at, String message) {
        return new ValidationReport(model.id(), model.sku(), platformKey, null, null, null, at,
                ValidationStatus.ERROR, List.of(), message);
    }

    public List<ValidationCheckResult> failedResults() {
        return results.stream().filter(r -> r.outcome() == CheckOutcome.FAILED).toList();
    }

    /** Первое по порядку выполнения провалившееся правило: оно объясняет итоговый статус. */
    public Optional<ValidationCheckResult> firstFailure() {
        return results.stream().filter(r -> r.outcome() == CheckOutcome.FAILED).findFirst();
    }
}
