package ru.furniture.validator.model;

/** Код правила (validation_check_results.rule_code). */
public enum RuleCode {
    /** Комплектность: присутствуют все обязательные форматы редакции. */
    FORMATS_COMPLETE,
    /** Размер файла не превышает лимит редакции. */
    FILE_SIZE,
    /** У GLB переданы байты содержимого. */
    GLB_CONTENT,
    /** Заголовок и границы первого чанка GLB корректны. */
    GLB_HEADER
}
