package ru.furniture.validator.validation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ru.furniture.validator.glb.GlbBinaryHeaderValidator;
import ru.furniture.validator.model.CheckOutcome;
import ru.furniture.validator.model.DemoProfiles;
import ru.furniture.validator.model.MarketplaceProfile;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ProfileRevision;
import ru.furniture.validator.model.ValidationCheckResult;
import ru.furniture.validator.model.ValidationReport;
import ru.furniture.validator.model.ValidationStatus;

/**
 * Алгоритм 2: проверка версии модели по текущей редакции требований площадки.
 * Итог FAILED, если провалено хотя бы одно правило; неизвестная площадка даёт ERROR.
 * Порядок результатов: сначала все правила по порядку списка, внутри правила — по файлам.
 */
public final class MarketplaceValidatorService {

    private final Map<String, MarketplaceProfile> profiles;
    private final List<ValidationRule> rules;
    private final Clock clock;

    public MarketplaceValidatorService(Map<String, MarketplaceProfile> profiles, List<ValidationRule> rules,
                                       Clock clock) {
        this.profiles = Map.copyOf(profiles);
        this.rules = List.copyOf(rules);
        this.clock = Objects.requireNonNull(clock);
    }

    public static MarketplaceValidatorService withDefaults() {
        return new MarketplaceValidatorService(DemoProfiles.all(),
                List.of(new RequiredFormatsRule(), new FileSizeRule(),
                        new GlbContentAndHeaderRule(new GlbBinaryHeaderValidator())),
                Clock.systemUTC());
    }

    public boolean hasProfile(String platformKey) {
        return platformKey != null && profiles.containsKey(platformKey);
    }

    public ValidationReport validate(Model3D model, String platformKey) {
        Objects.requireNonNull(model, "model");
        if (!hasProfile(platformKey)) {
            return ValidationReport.error(model, platformKey, clock.instant(),
                    "Неизвестный профиль площадки: '" + platformKey + "'");
        }
        MarketplaceProfile profile = profiles.get(platformKey);
        ProfileRevision revision = profile.currentRevision();

        List<ValidationCheckResult> results = new ArrayList<>();
        for (ValidationRule rule : rules) {
            results.addAll(rule.check(model, revision));
        }
        boolean failed = results.stream().anyMatch(r -> r.outcome() == CheckOutcome.FAILED);
        return new ValidationReport(model.id(), model.sku(), platformKey, profile.name(), revision.id(),
                revision.revisionNo(), clock.instant(),
                failed ? ValidationStatus.FAILED : ValidationStatus.PASSED, results, null);
    }
}
