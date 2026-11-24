package ru.furniture.validator.catalog;

import java.math.BigDecimal;

/** Критерии фильтрации каталога; любое поле может быть null («не задано»). Границы цены включительные. */
public record CatalogFilterCriteria(
        String searchQuery,
        String category,
        String platformReadiness,
        BigDecimal minPrice,
        BigDecimal maxPrice) {

    public static CatalogFilterCriteria empty() {
        return new CatalogFilterCriteria(null, null, null, null, null);
    }

    public CatalogFilterCriteria withSearchQuery(String value) {
        return new CatalogFilterCriteria(value, category, platformReadiness, minPrice, maxPrice);
    }

    public CatalogFilterCriteria withCategory(String value) {
        return new CatalogFilterCriteria(searchQuery, value, platformReadiness, minPrice, maxPrice);
    }

    public CatalogFilterCriteria withPlatformReadiness(String value) {
        return new CatalogFilterCriteria(searchQuery, category, value, minPrice, maxPrice);
    }

    public CatalogFilterCriteria withPriceRange(BigDecimal min, BigDecimal max) {
        return new CatalogFilterCriteria(searchQuery, category, platformReadiness, min, max);
    }
}
