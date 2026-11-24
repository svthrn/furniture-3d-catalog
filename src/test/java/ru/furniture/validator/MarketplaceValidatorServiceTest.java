package ru.furniture.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.furniture.validator.demo.DemoData;
import ru.furniture.validator.glb.GlbBinaryHeaderValidator;
import ru.furniture.validator.model.CheckOutcome;
import ru.furniture.validator.model.MarketplaceProfile;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ModelFile;
import ru.furniture.validator.model.ModelFormat;
import ru.furniture.validator.model.ProfileRevision;
import ru.furniture.validator.model.RuleCode;
import ru.furniture.validator.model.ValidationCheckResult;
import ru.furniture.validator.model.ValidationReport;
import ru.furniture.validator.model.ValidationStatus;
import ru.furniture.validator.validation.FileSizeRule;
import ru.furniture.validator.validation.GlbContentAndHeaderRule;
import ru.furniture.validator.validation.MarketplaceValidatorService;
import ru.furniture.validator.validation.RequiredFormatsRule;

class MarketplaceValidatorServiceTest {

    private static final long OWN_STORE_LIMIT = 30 * TestData.MB;

    private final MarketplaceValidatorService service = MarketplaceValidatorService.withDefaults();

    private static boolean detailContains(ValidationReport report, String fragment) {
        return report.failedResults().stream().anyMatch(r -> r.details().contains(fragment));
    }

    @Test
    void chairPassesWildberries() {
        byte[] glb = TestData.validGlb();
        Model3D model = TestData.model("X", ModelFile.of("m.glb", "GLB", glb.length, glb), TestData.usdz(14 * TestData.MB));
        assertEquals(ValidationStatus.PASSED, service.validate(model, "wildberries").status());
    }

    @Test
    void glbWithoutContentBytesIsNotPassed() {
        Model3D model = TestData.model("X", ModelFile.of("m.glb", "GLB", 1000, null), TestData.usdz(1000));
        ValidationReport report = service.validate(model, "wildberries");
        assertEquals(ValidationStatus.FAILED, report.status());
        assertTrue(detailContains(report, "content_bytes"));
        ValidationCheckResult failure = report.firstFailure().orElseThrow();
        assertEquals(RuleCode.GLB_CONTENT, failure.ruleCode());
        assertEquals("m.glb", failure.fileName());
    }

    @Test
    void glbWithOverlongChunkIsNotPassed() {
        byte[] data = DemoData.glbWithChunkLength(4000);
        Model3D model = TestData.model("X", ModelFile.of("m.glb", "GLB", data.length, data), TestData.usdz(1000));
        ValidationReport report = service.validate(model, "wildberries");
        assertEquals(ValidationStatus.FAILED, report.status());
        assertEquals(RuleCode.GLB_HEADER, report.firstFailure().orElseThrow().ruleCode());
        assertTrue(detailContains(report, "выходит за конец файла"));
    }

    @Test
    void missingSizeIsNotPassed() {
        Model3D model = TestData.model("X", ModelFile.of("a.glb", "GLB", null, TestData.validGlb()));
        ValidationReport report = service.validate(model, "own_web_store");
        assertEquals(ValidationStatus.FAILED, report.status());
        assertEquals(RuleCode.FILE_SIZE, report.firstFailure().orElseThrow().ruleCode());
    }

    @Test
    void negativeBooleanAndFractionalSizesAreNotPassed() {
        for (Object bad : new Object[] {-1, -1L, true, 10.5, "1000"}) {
            Model3D model = TestData.model("X", ModelFile.of("a.glb", "GLB", bad, TestData.validGlb()));
            ValidationReport report = service.validate(model, "own_web_store");
            assertEquals(ValidationStatus.FAILED, report.status(), "размер " + bad);
            assertTrue(detailContains(report, "не указан или некорректен"), "размер " + bad);
        }
    }

    @Test
    void sizeEqualToLimitPassesAndOverLimitFails() {
        byte[] glb = TestData.validGlb();
        ValidationReport ok = service.validate(
                TestData.model("X", ModelFile.of("a.glb", "GLB", OWN_STORE_LIMIT, glb)), "own_web_store");
        ValidationReport bad = service.validate(
                TestData.model("X", ModelFile.of("a.glb", "GLB", OWN_STORE_LIMIT + 1, glb)), "own_web_store");
        assertEquals(ValidationStatus.PASSED, ok.status());
        assertEquals(ValidationStatus.FAILED, bad.status());
        assertTrue(detailContains(bad, "превышает лимит"));
    }

    @Test
    void unknownProfileGivesErrorWithoutRevision() {
        ValidationReport report = service.validate(TestData.model("X"), "nope");
        assertEquals(ValidationStatus.ERROR, report.status());
        assertNull(report.revisionId());
        assertTrue(report.results().isEmpty());
        assertTrue(report.message().contains("Неизвестный профиль площадки: 'nope'"));
    }

    @Test
    void missingFormatFieldDoesNotCrash() {
        Model3D model = TestData.model("X", ModelFile.of("a.bin", null, 1000, null));
        ValidationReport report = service.validate(model, "wildberries");
        assertEquals(ValidationStatus.FAILED, report.status());
        assertTrue(detailContains(report, "Отсутствуют обязательные форматы: GLB, USDZ"));
    }

    @Test
    void nullFilesListIsTreatedAsEmpty() {
        Model3D model = new Model3D(null, null, "X", null, false, null, null);
        ValidationReport report = service.validate(model, "yandex_market");
        assertEquals(ValidationStatus.FAILED, report.status());
        assertTrue(detailContains(report, "Отсутствуют обязательные форматы: GLB"));
    }

    @Test
    void formatIsRecognisedCaseInsensitively() {
        byte[] glb = TestData.validGlb();
        Model3D model = TestData.model("X", ModelFile.of("a.glb", "glb", glb.length, glb));
        assertEquals(ValidationStatus.PASSED, service.validate(model, "yandex_market").status());
    }

    @Test
    void resultsAreOrderedByRuleThenFile() {
        byte[] glb = TestData.validGlb();
        Model3D model = TestData.model("X", ModelFile.of("a.glb", "GLB", glb.length, glb), TestData.usdz(1000));
        List<RuleCode> codes = service.validate(model, "wildberries").results().stream()
                .map(ValidationCheckResult::ruleCode).toList();
        assertEquals(List.of(RuleCode.FORMATS_COMPLETE, RuleCode.FILE_SIZE, RuleCode.FILE_SIZE,
                RuleCode.GLB_CONTENT, RuleCode.GLB_HEADER), codes);
    }

    @Test
    void failureOrderFollowsRuleOrderAndFirstFailureExplainsStatus() {
        // нет USDZ (комплектность), превышен лимит и нет байт: три разных правила проваливаются по порядку
        Model3D model = TestData.model("X", ModelFile.of("a.glb", "GLB", 26 * TestData.MB, null));
        ValidationReport report = service.validate(model, "wildberries");
        List<RuleCode> failed = report.failedResults().stream().map(ValidationCheckResult::ruleCode).toList();
        assertEquals(List.of(RuleCode.FORMATS_COMPLETE, RuleCode.FILE_SIZE, RuleCode.GLB_CONTENT), failed);
        assertEquals(RuleCode.FORMATS_COMPLETE, report.firstFailure().orElseThrow().ruleCode());
    }

    @Test
    void checkResultIsBoundToTheFileAndFormatsResultHasNoFile() {
        Model3D model = TestData.model("X", ModelFile.of("a.glb", "GLB", 1000, null), TestData.usdz(1000));
        ValidationReport report = service.validate(model, "wildberries");
        ValidationCheckResult formats = report.results().get(0);
        assertEquals(RuleCode.FORMATS_COMPLETE, formats.ruleCode());
        assertNull(formats.file());
        ValidationCheckResult content = report.failedResults().get(0);
        assertSame(model.files().get(0).fileName(), content.file().fileName());
        assertEquals("a.glb", content.fileName());
        assertEquals(CheckOutcome.FAILED, content.outcome());
    }

    @Test
    void reportIsBoundToProfileRevision() {
        Model3D model = new Model3D(5L, 1L, "CHAIR", 2, false, null,
                List.of(ModelFile.of("c.glb", "GLB", 1000, TestData.validGlb()), TestData.usdz(1000)));
        ValidationReport report = service.validate(model, "wildberries");
        assertEquals(5L, report.modelId());
        assertEquals("wildberries", report.platformKey());
        assertEquals(3L, report.revisionId());
        assertEquals(3, report.revisionNo());
        assertNotNull(report.checkedAt());
    }

    @Test
    void validationUsesLimitAndFormatsOfTheRevision() {
        ProfileRevision revision = new ProfileRevision(42L, "custom", 7, 100, 2, Set.of(ModelFormat.GLB),
                null, null, "test", null);
        Instant fixed = Instant.parse("2026-11-19T12:00:00Z");
        MarketplaceValidatorService custom = new MarketplaceValidatorService(
                Map.of("custom", new MarketplaceProfile("custom", "Custom", true, List.of(revision))),
                List.of(new RequiredFormatsRule(), new FileSizeRule(),
                        new GlbContentAndHeaderRule(new GlbBinaryHeaderValidator())),
                Clock.fixed(fixed, ZoneOffset.UTC));
        byte[] glb = TestData.validGlb();
        ValidationReport ok = custom.validate(TestData.model("X", ModelFile.of("a.glb", "GLB", 100, glb)), "custom");
        ValidationReport over = custom.validate(TestData.model("X", ModelFile.of("a.glb", "GLB", 101, glb)), "custom");
        assertEquals(ValidationStatus.PASSED, ok.status());
        assertEquals(ValidationStatus.FAILED, over.status());
        assertEquals(42L, over.revisionId());
        assertEquals(7, over.revisionNo());
        assertEquals(fixed, over.checkedAt());
    }

    @Test
    void usdzContentIsNotChecked() {
        byte[] glb = TestData.validGlb();
        Model3D model = TestData.model("X", ModelFile.of("a.glb", "GLB", glb.length, glb),
                ModelFile.of("a.usdz", "USDZ", 1000, new byte[] {1, 2, 3}));
        assertEquals(ValidationStatus.PASSED, service.validate(model, "wildberries").status());
    }

    @Test
    void modelFileDefensivelyCopiesContent() {
        byte[] glb = TestData.validGlb();
        ModelFile file = ModelFile.of("a.glb", "GLB", glb.length, glb);
        glb[0] = 'X';
        assertEquals('g', file.contentBytes()[0]);
        file.contentBytes()[0] = 'Y';
        assertEquals('g', file.contentBytes()[0]);
    }
}
