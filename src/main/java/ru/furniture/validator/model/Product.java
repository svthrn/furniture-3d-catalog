package ru.furniture.validator.model;

import java.util.List;

/**
 * Товар (таблица products) с вариантами.
 *
 * <p>{@code flatVariant} описывает «плоский» товар, у которого цена и артикул лежат прямо в карточке
 * товара, а списка вариантов нет (ветка алгоритма 3, шаг 5). {@code model3d} — запасная модель товара.
 */
public record Product(
        Long id,
        String name,
        String category,
        String categoryId,
        List<ProductVariant> variants,
        ProductVariant flatVariant,
        Model3D model3d) {

    public Product {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public static Product of(String name, String category, List<ProductVariant> variants) {
        return new Product(null, name, category, null, variants, null, null);
    }

    /** Варианты для обхода: список вариантов или единственный плоский вариант. */
    public List<ProductVariant> effectiveVariants() {
        if (!variants.isEmpty()) {
            return variants;
        }
        return flatVariant == null ? List.of() : List.of(flatVariant);
    }

    public Product withVariants(List<ProductVariant> newVariants) {
        return new Product(id, name, category, categoryId, newVariants, flatVariant, model3d);
    }
}
