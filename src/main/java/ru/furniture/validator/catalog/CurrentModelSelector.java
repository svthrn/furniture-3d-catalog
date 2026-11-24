package ru.furniture.validator.catalog;

import java.util.List;
import java.util.Optional;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.Product;
import ru.furniture.validator.model.ProductVariant;

/**
 * Версия с признаком is_current имеет приоритет; если такой нет, берётся максимальная version
 * (при равенстве — первая в списке). Если списка версий нет, берётся model3d варианта, затем товара.
 */
public final class CurrentModelSelector implements ModelSelector {

    @Override
    public Optional<Model3D> select(Product product, ProductVariant variant) {
        List<Model3D> models = variant.models3d();
        if (!models.isEmpty()) {
            for (Model3D model : models) {
                if (model.current()) {
                    return Optional.of(model);
                }
            }
            Model3D best = models.get(0);
            for (Model3D model : models) {
                if (model.versionOrDefault() > best.versionOrDefault()) {
                    best = model;
                }
            }
            return Optional.of(best);
        }
        if (variant.model3d() != null) {
            return Optional.of(variant.model3d());
        }
        return Optional.ofNullable(product.model3d());
    }
}
