package ru.furniture.validator.model;

/** Итог запуска проверки (validation_reports.validation_status). */
public enum ValidationStatus {
    PASSED,
    FAILED,
    /** Проверка не выполнена (например, неизвестная площадка). */
    ERROR
}
