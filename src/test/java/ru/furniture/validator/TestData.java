package ru.furniture.validator;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import ru.furniture.validator.demo.DemoData;
import ru.furniture.validator.model.Model3D;
import ru.furniture.validator.model.ModelFile;

/** Общие фабрики тестовых данных. */
final class TestData {

    static final long MB = 1024L * 1024L;

    private TestData() {
    }

    static byte[] validGlb() {
        return DemoData.validGlb();
    }

    /** Записывает uint32 (little-endian) по смещению. */
    static byte[] patchUint32(byte[] source, int offset, long value) {
        byte[] copy = source.clone();
        ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, (int) value);
        return copy;
    }

    static ModelFile glbFile(byte[] content) {
        return ModelFile.of("m.glb", "GLB", content == null ? 1000 : content.length, content);
    }

    static ModelFile usdz(long size) {
        return ModelFile.of("m.usdz", "USDZ", size, null);
    }

    static Model3D model(String sku, ModelFile... files) {
        return Model3D.of(sku, List.of(files));
    }

    /** Модель, проходящая Wildberries: валидный GLB и USDZ. */
    static Model3D good(String sku) {
        return model(sku, glbFile(validGlb()), usdz(1000));
    }

    /** Модель, не проходящая: у GLB нет содержимого. */
    static Model3D noContent(String sku) {
        return model(sku, glbFile(null), usdz(1000));
    }

    static BigDecimal price(String value) {
        return new BigDecimal(value);
    }
}
