package ru.furniture.validator.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Вариант товара (таблица product_variants).
 *
 * @param price     цена; null означает «цена неизвестна»
 * @param models3d  версии модели варианта
 * @param model3d   единственная модель варианта (запасное поле, если списка версий нет)
 */
public record ProductVariant(
        Long id,
        String sku,
        BigDecimal price,
        List<Model3D> models3d,
        Model3D model3d) {

    public ProductVariant {
        models3d = models3d == null ? List.of() : List.copyOf(models3d);
    }

    public static ProductVariant of(String sku, BigDecimal price, List<Model3D> models3d) {
        return new ProductVariant(null, sku, price, models3d, null);
    }
}
