package ru.furniture.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.furniture.validator.catalog.CatalogFilterCriteria;
import ru.furniture.validator.catalog.CatalogFilterService;
import ru.furniture.validator.catalog.CurrentModelSelector;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.Product;
import ru.furniture.validator.model.ProductVariant;
import ru.furniture.validator.validation.MarketplaceValidatorService;

class CatalogFilterServiceTest {

    private final CatalogFilterService filter =
            new CatalogFilterService(new CurrentModelSelector(), MarketplaceValidatorService.withDefaults());
    private final CatalogFilterCriteria wb = CatalogFilterCriteria.empty().withPlatformReadiness("wildberries");

    private static Product product(String name, ProductVariant... variants) {
        return Product.of(name, null, List.of(variants));
    }

    private static ProductVariant variant(String sku, String price, Model3D... models) {
        return ProductVariant.of(sku, price == null ? null : TestData.price(price), List.of(models));
    }

    @Test
    void glbWithoutContentIsExcluded() {
        List<Product> catalog = List.of(product("A", variant("N", "1.0", TestData.noContent("N"))));
        assertTrue(filter.filter(catalog, wb).isEmpty());
    }

    @Test
    void model3dFieldOfVariantIsUsed() {
        ProductVariant v = new ProductVariant(null, "G", TestData.price("1.0"), null, TestData.good("G"));
        assertEquals(1, filter.filter(List.of(product("A", v)), wb).size());
    }

    @Test
    void model3dFieldOfProductIsUsedForFlatProduct() {
        ProductVariant flat = new ProductVariant(null, "G", TestData.price("1.0"), null, null);
        Product flatProduct = new Product(null, "A", null, null, List.of(), flat, TestData.good("G"));
        assertEquals(1, filter.filter(List.of(flatProduct), wb).size());
    }

    @Test
    void currentModelHasPriorityOverMaxVersion() {
        Model3D oldOk = TestData.good("G").withVersion(1, true);
        Model3D newBad = TestData.noContent("N").withVersion(2, false);
        List<Product> catalog = List.of(product("A", variant("G", "1.0", oldOk, newBad)));
        assertEquals(1, filter.filter(catalog, wb).size());
    }

    @Test
    void maxVersionIsUsedWhenNoModelIsCurrent() {
        Model3D v1Bad = TestData.noContent("N").withVersion(1, false);
        Model3D v2Ok = TestData.good("G").withVersion(2, false);
        assertEquals(1, filter.filter(List.of(product("A", variant("G", "1.0", v1Bad, v2Ok))), wb).size());
        Model3D v1Ok = TestData.good("G").withVersion(1, false);
        Model3D v2Bad = TestData.noContent("N").withVersion(2, false);
        assertTrue(filter.filter(List.of(product("A", variant("G", "1.0", v1Ok, v2Bad))), wb).isEmpty());
    }

    @Test
    void missingPriceIsExcludedOnlyWhenBoundsAreSet() {
        List<Product> catalog = List.of(product("A", variant("G", null)));
        CatalogFilterCriteria bounded = CatalogFilterCriteria.empty().withPriceRange(null, TestData.price("100"));
        assertTrue(filter.filter(catalog, bounded).isEmpty());
        assertTrue(filter.filter(catalog, CatalogFilterCriteria.empty().withPriceRange(TestData.price("0"), null)).isEmpty());
        assertEquals(1, filter.filter(catalog, CatalogFilterCriteria.empty()).size());
    }

    @Test
    void priceBoundsAreInclusive() {
        List<Product> catalog = List.of(product("A", variant("L", "100"), variant("M", "150"), variant("H", "200")));
        CatalogFilterCriteria range = CatalogFilterCriteria.empty().withPriceRange(TestData.price("100"), TestData.price("200"));
        assertEquals(3, filter.filter(catalog, range).get(0).variants().size());
        CatalogFilterCriteria narrow = CatalogFilterCriteria.empty().withPriceRange(TestData.price("101"), TestData.price("199"));
        List<ProductVariant> kept = filter.filter(catalog, narrow).get(0).variants();
        assertEquals(1, kept.size());
        assertEquals("M", kept.get(0).sku());
    }

    @Test
    void unknownPlatformThrowsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> filter.filter(List.of(), CatalogFilterCriteria.empty().withPlatformReadiness("nope")));
        assertTrue(ex.getMessage().contains("nope"));
    }

    @Test
    void searchWithMissingNameOrSkuDoesNotCrash() {
        List<Product> catalog = List.of(
                new Product(null, null, null, null, List.of(variant(null, "1.0")), null, null),
                product("B", variant(null, "1.0")));
        assertTrue(filter.filter(catalog, CatalogFilterCriteria.empty().withSearchQuery("abc")).isEmpty());
    }

    @Test
    void searchMatchesNameForAllVariantsOrSkuForOne() {
        List<Product> catalog = List.of(product("Кресло Осло", variant("AAA", "1"), variant("BBB", "1")));
        assertEquals(2, filter.filter(catalog, CatalogFilterCriteria.empty().withSearchQuery("  ОСЛО ")).get(0).variants().size());
        List<ProductVariant> bySku = filter.filter(catalog, CatalogFilterCriteria.empty().withSearchQuery("bbb")).get(0).variants();
        assertEquals(1, bySku.size());
        assertEquals("BBB", bySku.get(0).sku());
    }

    @Test
    void categoryMatchesNameOrIdCaseInsensitively() {
        Product chair = new Product(1L, "A", "Кресла", "cat-7", List.of(variant("A", "1")), null, null);
        assertEquals(1, filter.filter(List.of(chair), CatalogFilterCriteria.empty().withCategory(" кресла ")).size());
        assertEquals(1, filter.filter(List.of(chair), CatalogFilterCriteria.empty().withCategory("CAT-7")).size());
        assertTrue(filter.filter(List.of(chair), CatalogFilterCriteria.empty().withCategory("диваны")).isEmpty());
    }

    @Test
    void withoutPlatformModelsAreNotChecked() {
        List<Product> catalog = List.of(product("A", variant("N", "1.0")));
        assertEquals(1, filter.filter(catalog, CatalogFilterCriteria.empty()).size());
        assertTrue(filter.filter(catalog, wb).isEmpty());
    }

    @Test
    void sourceCatalogIsNotMutated() {
        ProductVariant good = variant("G", "1.0", TestData.good("G"));
        ProductVariant bad = variant("N", "1.0", TestData.noContent("N"));
        Product original = product("A", good, bad);
        List<Product> catalog = List.of(original);

        List<Product> result = filter.filter(catalog, wb);

        assertEquals(1, result.size());
        assertNotSame(original, result.get(0));
        assertEquals(1, result.get(0).variants().size());
        assertSame(good, result.get(0).variants().get(0));
        assertEquals(2, original.variants().size());
        assertEquals(1, catalog.size());
        assertSame(original, catalog.get(0));
        assertThrows(UnsupportedOperationException.class, () -> original.variants().add(good));
    }
}
