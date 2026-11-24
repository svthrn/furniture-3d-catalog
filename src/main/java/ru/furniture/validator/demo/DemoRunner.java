package ru.furniture.validator.demo;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import ru.furniture.validator.catalog.CatalogFilterCriteria;
import ru.furniture.validator.catalog.CatalogFilterService;
import ru.furniture.validator.catalog.CurrentModelSelector;
import ru.furniture.validator.glb.GlbBinaryHeaderValidator;
import ru.furniture.validator.glb.GlbHeaderResult;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ModelFile;
import ru.furniture.validator.model.ModelLifecycleStatus;
import ru.furniture.validator.model.Product;
import ru.furniture.validator.model.ProductVariant;
import ru.furniture.validator.model.ValidationCheckResult;
import ru.furniture.validator.model.ValidationReport;
import ru.furniture.validator.validation.MarketplaceValidatorService;

/** Демонстрация: семь сценариев процедурного прототипа (1.1-1.3, 2.1-2.3, 3) и отклонённая версия 2 из таблицы 6.12. */
public final class DemoRunner {

    private static final long MB = 1024L * 1024L;

    private DemoRunner() {
    }

    public static void main(String[] args) {
        GlbBinaryHeaderValidator headerValidator = new GlbBinaryHeaderValidator();
        MarketplaceValidatorService service = MarketplaceValidatorService.withDefaults();
        byte[] valid = DemoData.validGlb();

        System.out.println("=== Алгоритм 1: заголовок GLB ===");
        GlbHeaderResult t11 = headerValidator.validate(valid);
        System.out.printf("[ТЕСТ 1.1] Валидный GLB: is_valid=%s, magic='%s', version=%d%n",
                t11.valid(), t11.magic(), t11.version());

        byte[] badMagic = valid.clone();
        System.arraycopy("BADF".getBytes(), 0, badMagic, 0, 4);
        GlbHeaderResult t12 = headerValidator.validate(badMagic);
        System.out.printf("[ТЕСТ 1.2] Ошибка magic: is_valid=%s, ошибки=%s%n", t12.valid(), t12.errors());

        GlbHeaderResult t13 = headerValidator.validate(DemoData.glbWithChunkLength(4000));
        System.out.printf("[ТЕСТ 1.3] Длина JSON-чанка выходит за файл: is_valid=%s, ошибки=%s%n",
                t13.valid(), t13.errors());

        System.out.println("\n=== Алгоритм 2: проверка по профилю площадки ===");
        Model3D chair = new Model3D(null, null, "CHAIR-SK-01-GREY", 1, true, ModelLifecycleStatus.DRAFT, List.of(
                ModelFile.of("chair.glb", "GLB", valid.length, valid),
                ModelFile.of("chair.usdz", "USDZ", 14 * MB, null)));
        System.out.printf("%n[ТЕСТ 2.1] Кресло для Wildberries (GLB + USDZ): вердикт=%s%n",
                service.validate(chair, "wildberries").status());

        Model3D sofa = new Model3D(null, null, "SOFA-NR-02-BLUE", 1, true, ModelLifecycleStatus.DRAFT, List.of(
                ModelFile.of("sofa.glb", "GLB", 18 * MB, valid)));
        ValidationReport r22 = service.validate(sofa, "wildberries");
        System.out.printf("[ТЕСТ 2.2] Диван без USDZ (отклонение WB): вердикт=%s, ошибки=%s%n",
                r22.status(), details(r22));

        Model3D noBytes = Model3D.of("CHAIR-NB", List.of(
                ModelFile.of("c.glb", "GLB", 1000, null),
                ModelFile.of("c.usdz", "USDZ", 1000, null)));
        ValidationReport r23 = service.validate(noBytes, "wildberries");
        System.out.printf("[ТЕСТ 2.3] GLB без content_bytes: вердикт=%s, ошибки=%s%n", r23.status(), details(r23));

        System.out.println("\n=== Алгоритм 3: фильтрация каталога ===");
        List<Product> catalog = List.of(
                Product.of("Кресло скандинавское 'Осло'", "Кресла", List.of(
                        ProductVariant.of("CHAIR-SK-01-GREY", new BigDecimal("18900.0"), List.of(chair)))),
                Product.of("Диван прямой 'Нордик 3'", "Мягкая мебель", List.of(
                        ProductVariant.of("SOFA-NR-02-BLUE", new BigDecimal("34900.0"), List.of(sofa)))));
        CatalogFilterService filter = new CatalogFilterService(new CurrentModelSelector(), service);
        List<Product> found = filter.filter(catalog, CatalogFilterCriteria.empty().withPlatformReadiness("wildberries"));
        System.out.printf("[ТЕСТ 3] Фильтр по готовности к Wildberries: найдено %d позиций:%n", found.size());
        for (Product p : found) {
            System.out.printf(" -> %s (%s)%n", p.name(), p.variants().get(0).sku());
        }

        System.out.println("\n=== Таблица 6.12: отклонённая версия 2 CHAIR-SK-01-GREY ===");
        byte[] broken = DemoData.glbWithChunkLength(4000);
        Model3D chairV2 = new Model3D(5L, 1L, "CHAIR-SK-01-GREY", 2, false, ModelLifecycleStatus.DRAFT, List.of(
                ModelFile.of("chair.glb", "GLB", broken.length, broken),
                ModelFile.of("chair.usdz", "USDZ", 14 * MB, null)));
        ValidationReport report = service.validate(chairV2, "wildberries");
        System.out.printf("Файл: chair.glb, %d байт; длина первого чанка в заголовке: 4000%n", broken.length);
        System.out.printf("modelId=%d, версия=%d, платформа=%s, revisionId=%d (редакция №%d), итог=%s%n",
                report.modelId(), chairV2.version(), report.platformKey(), report.revisionId(),
                report.revisionNo(), report.status());
        for (ValidationCheckResult failed : report.failedResults()) {
            System.out.printf("  сработало: ruleCode=%s, file=%s, outcome=%s%n    details=%s%n",
                    failed.ruleCode(), failed.fileName(), failed.outcome(), failed.details());
        }
        report.firstFailure().ifPresent(f ->
                System.out.println("Правило, определившее итог (первое по порядку): " + f.ruleCode()));
    }

    private static List<String> details(ValidationReport report) {
        return report.failedResults().stream().map(ValidationCheckResult::details).collect(Collectors.toList());
    }
}
