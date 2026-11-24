package ru.furniture.validator.catalog;

import java.util.Optional;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.Product;
import ru.furniture.validator.model.ProductVariant;

/** Стратегия выбора версии модели, по которой оценивается готовность варианта. */
public interface ModelSelector {

    Optional<Model3D> select(Product product, ProductVariant variant);
}
