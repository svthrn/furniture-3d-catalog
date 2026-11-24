package ru.furniture.validator.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Демонстрационные профили площадок: те же лимиты и форматы, что в {@code MARKETPLACE_PROFILES}
 * процедурного прототипа. Это конфигурационные примеры, а не подтверждённые требования площадок;
 * идентификатор редакции Wildberries (3) совпадает с примером в таблице 6.12 отчёта.
 */
public final class DemoProfiles {

    private static final long MB = 1024L * 1024L;
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final String SOURCE = "demo-config (не сверено с документом площадки)";

    private DemoProfiles() {
    }

    public static Map<String, MarketplaceProfile> all() {
        return Map.of(
                "wildberries", profile("wildberries", "Wildberries (Раздел «Мебель»)", 3L, 3, 25 * MB,
                        Set.of(ModelFormat.GLB, ModelFormat.USDZ)),
                "yandex_market", profile("yandex_market", "Яндекс Маркет", 5L, 1, 50 * MB,
                        Set.of(ModelFormat.GLB)),
                "own_web_store", profile("own_web_store", "Собственный интернет-магазин (<model-viewer>)", 6L, 1,
                        30 * MB, Set.of(ModelFormat.GLB)));
    }

    private static MarketplaceProfile profile(String code, String name, long revisionId, int revisionNo,
                                              long maxSize, Set<ModelFormat> formats) {
        ProfileRevision revision = new ProfileRevision(revisionId, code, revisionNo, maxSize, 2, formats,
                FROM, null, SOURCE, null);
        return new MarketplaceProfile(code, name, true, List.of(revision));
    }
}
