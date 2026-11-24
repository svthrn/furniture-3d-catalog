package ru.furniture.validator.model;

import java.util.Comparator;
import java.util.List;

/** Площадка (таблица marketplace_profiles) со списком редакций требований. */
public record MarketplaceProfile(String code, String name, boolean active, List<ProfileRevision> revisions) {

    public MarketplaceProfile {
        revisions = revisions == null ? List.of() : List.copyOf(revisions);
    }

    /** Редакция с наибольшим номером, по которой выполняется проверка. */
    public ProfileRevision currentRevision() {
        return revisions.stream()
                .max(Comparator.comparingInt(ProfileRevision::revisionNo))
                .orElseThrow(() -> new IllegalStateException("У площадки '" + code + "' нет редакций требований"));
    }
}
