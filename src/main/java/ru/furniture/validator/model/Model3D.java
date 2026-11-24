package ru.furniture.validator.model;

import java.util.List;

/**
 * Версия 3D-модели варианта товара (таблица models_3d).
 *
 * @param version номер версии; null трактуется как 1, как в процедурном прототипе
 * @param current признак текущей версии (is_current)
 */
public record Model3D(
        Long id,
        Long variantId,
        String sku,
        Integer version,
        boolean current,
        ModelLifecycleStatus lifecycleStatus,
        List<ModelFile> files) {

    public Model3D {
        lifecycleStatus = lifecycleStatus == null ? ModelLifecycleStatus.DRAFT : lifecycleStatus;
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static Model3D of(String sku, List<ModelFile> files) {
        return new Model3D(null, null, sku, null, false, ModelLifecycleStatus.DRAFT, files);
    }

    public int versionOrDefault() {
        return version == null ? 1 : version;
    }

    public Model3D withVersion(int newVersion, boolean isCurrent) {
        return new Model3D(id, variantId, sku, newVersion, isCurrent, lifecycleStatus, files);
    }
}
