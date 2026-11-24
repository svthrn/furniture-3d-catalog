package ru.furniture.validator.model;

import java.time.LocalDate;
import java.util.Set;

/**
 * Неизменяемая редакция требований площадки (profile_revisions + profile_required_formats).
 * Протокол проверки ссылается на конкретную редакцию, а не на «текущий» профиль.
 *
 * <p>{@code strictGlbVersion} хранится для согласованности с моделью данных; как и в процедурном
 * прототипе, алгоритм 1 проверяет версию glTF 2 константой.
 */
public record ProfileRevision(
        Long id,
        String profileCode,
        int revisionNo,
        long maxFileSizeBytes,
        int strictGlbVersion,
        Set<ModelFormat> requiredFormats,
        LocalDate validFrom,
        LocalDate validTo,
        String sourceUrl,
        LocalDate checkedAt) {

    public ProfileRevision {
        requiredFormats = requiredFormats == null ? Set.of() : Set.copyOf(requiredFormats);
    }
}
