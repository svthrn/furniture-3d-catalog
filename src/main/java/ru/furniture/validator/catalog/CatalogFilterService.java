package ru.furniture.validator.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.Product;
import ru.furniture.validator.model.ProductVariant;
import ru.furniture.validator.model.ValidationStatus;
import ru.furniture.validator.validation.MarketplaceValidatorService;

/**
 * Алгоритм 3: многокритериальная фильтрация каталога с контролем готовности 3D-модели.
 * Исходный каталог не изменяется: возвращаются новые объекты Product только с прошедшими вариантами.
 */
public final class CatalogFilterService {

    private final ModelSelector modelSelector;
    private final MarketplaceValidatorService validator;

    public CatalogFilterService(ModelSelector modelSelector, MarketplaceValidatorService validator) {
        this.modelSelector = Objects.requireNonNull(modelSelector);
        this.validator = Objects.requireNonNull(validator);
    }

    public List<Product> filter(List<Product> catalog, CatalogFilterCriteria criteria) {
        String platform = blankToNull(criteria.platformReadiness());
        if (platform != null && !validator.hasProfile(platform)) {
            throw new IllegalArgumentException("Неизвестный профиль площадки: '" + platform + "'");
        }
        String query = criteria.searchQuery() == null || criteria.searchQuery().isBlank()
                ? null : criteria.searchQuery().strip().toLowerCase(Locale.ROOT);
        String category = blankToNull(criteria.category()) == null
                ? null : criteria.category().strip().toLowerCase(Locale.ROOT);
        boolean priceBounded = criteria.minPrice() != null || criteria.maxPrice() != null;

        List<Product> result = new ArrayList<>();
        for (Product product : catalog) {
            boolean nameMatched = query != null && lower(product.name()).contains(query);

            if (category != null
                    && !category.equals(lower(product.category()))
                    && !category.equals(lower(product.categoryId()))) {
                continue;
            }

            List<ProductVariant> matching = new ArrayList<>();
            for (ProductVariant variant : product.effectiveVariants()) {
                boolean skuMatched = query != null && lower(variant.sku()).contains(query);
                if (query != null && !(nameMatched || skuMatched)) {
                    continue;
                }
                if (priceBounded && !priceInRange(variant.price(), criteria)) {
                    continue;
                }
                if (platform != null && !isReady(product, variant, platform)) {
                    continue;
                }
                matching.add(variant);
            }

            if (!matching.isEmpty()) {
                result.add(product.variants().isEmpty() ? product : product.withVariants(matching));
            }
        }
        return result;
    }

    private boolean isReady(Product product, ProductVariant variant, String platform) {
        Optional<Model3D> model = modelSelector.select(product, variant);
        return model.isPresent() && validator.validate(model.get(), platform).status() == ValidationStatus.PASSED;
    }

    /** Цена неизвестна — по границам отобрать нельзя, вариант исключается. */
    private static boolean priceInRange(BigDecimal price, CatalogFilterCriteria criteria) {
        if (price == null) {
            return false;
        }
        if (criteria.minPrice() != null && price.compareTo(criteria.minPrice()) < 0) {
            return false;
        }
        return criteria.maxPrice() == null || price.compareTo(criteria.maxPrice()) <= 0;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
