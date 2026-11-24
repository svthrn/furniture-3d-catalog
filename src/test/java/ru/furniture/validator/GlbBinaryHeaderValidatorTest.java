package ru.furniture.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ru.furniture.validator.demo.DemoData;
import ru.furniture.validator.glb.GlbBinaryHeaderValidator;
import ru.furniture.validator.glb.GlbHeaderResult;

class GlbBinaryHeaderValidatorTest {

    private final GlbBinaryHeaderValidator validator = new GlbBinaryHeaderValidator();

    private static boolean anyError(GlbHeaderResult result, String fragment) {
        return result.errors().stream().anyMatch(e -> e.contains(fragment));
    }

    @Test
    void validFile() {
        GlbHeaderResult result = validator.validate(TestData.validGlb());
        assertTrue(result.valid());
        assertEquals("glTF", result.magic());
        assertEquals(2L, result.version());
        assertEquals(48, result.actualLength());
        assertEquals(28L, result.jsonChunkLength());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void badMagic() {
        byte[] data = TestData.validGlb();
        System.arraycopy("BADF".getBytes(), 0, data, 0, 4);
        GlbHeaderResult result = validator.validate(data);
        assertFalse(result.valid());
        assertTrue(anyError(result, "сигнатура"));
    }

    @Test
    void firstChunkLengthBeyondFileEnd() {
        GlbHeaderResult result = validator.validate(DemoData.glbWithChunkLength(4000));
        assertFalse(result.valid());
        assertTrue(anyError(result, "выходит за конец файла"));
        assertTrue(anyError(result, "конец чанка 4020 > длина файла 48"));
    }

    @Test
    void firstChunkLengthNotAligned() {
        GlbHeaderResult result = validator.validate(DemoData.glb(27, 27, GlbBinaryHeaderValidator.CHUNK_TYPE_JSON, null));
        assertFalse(result.valid());
        assertTrue(anyError(result, "не кратна 4"));
    }

    @Test
    void truncatedFile() {
        byte[] data = TestData.validGlb();
        GlbHeaderResult result = validator.validate(java.util.Arrays.copyOf(data, 15));
        assertFalse(result.valid());
        assertTrue(anyError(result, "меньше заголовка"));
        assertNull(result.magic());
    }

    @Test
    void nullBytesAreRejectedWithoutException() {
        GlbHeaderResult result = validator.validate(null);
        assertFalse(result.valid());
        assertEquals(0, result.actualLength());
    }

    @Test
    void chunkBeyondDeclaredLengthWhenFileIsLonger() {
        // файл 48 байт, объявленная длина 30: чанк помещается в файл, но выходит за объявленную длину
        byte[] data = DemoData.glb(28, 28, GlbBinaryHeaderValidator.CHUNK_TYPE_JSON, 30L);
        GlbHeaderResult result = validator.validate(data);
        assertFalse(result.valid());
        assertTrue(anyError(result, "выходит за объявленную длину файла 30"));
        assertFalse(anyError(result, "выходит за конец файла"));
    }

    @Test
    void uint32ChunkLengthAboveIntMaxIsNotNegative() {
        GlbHeaderResult result = validator.validate(DemoData.glbWithChunkLength(0xFFFFFFFFL));
        assertFalse(result.valid());
        assertEquals(4_294_967_295L, result.jsonChunkLength());
        assertTrue(anyError(result, "4294967295 байт выходит за конец файла"));
    }

    @Test
    void uint32ChunkLengthJustAboveIntMax() {
        long length = (long) Integer.MAX_VALUE + 1; // 2147483648: в int стала бы отрицательной
        GlbHeaderResult result = validator.validate(DemoData.glbWithChunkLength(length));
        assertFalse(result.valid());
        assertEquals(length, result.jsonChunkLength());
        assertTrue(anyError(result, "выходит за конец файла"));
    }

    @Test
    void uint32DeclaredLengthAboveIntMaxIsReportedUnsigned() {
        byte[] data = TestData.patchUint32(TestData.validGlb(), 8, 0xFFFFFFFFL);
        GlbHeaderResult result = validator.validate(data);
        assertFalse(result.valid());
        assertEquals(4_294_967_295L, result.declaredLength());
        assertTrue(anyError(result, "Длина в заголовке 4294967295"));
    }

    @Test
    void uint32VersionAboveIntMaxIsReportedUnsigned() {
        byte[] data = TestData.patchUint32(TestData.validGlb(), 4, 0x80000000L);
        GlbHeaderResult result = validator.validate(data);
        assertFalse(result.valid());
        assertEquals(2_147_483_648L, result.version());
        assertTrue(anyError(result, "получено 2147483648"));
    }

    @Test
    void notJsonChunkTypeIsRejected() {
        byte[] data = TestData.patchUint32(TestData.validGlb(), 16, 0x004E4942L);
        GlbHeaderResult result = validator.validate(data);
        assertFalse(result.valid());
        assertTrue(anyError(result, "Первый чанк не JSON"));
    }

    @Test
    void errorsAreAccumulatedInAlgorithmOrder() {
        byte[] data = TestData.patchUint32(TestData.validGlb(), 4, 1);
        data = TestData.patchUint32(data, 8, 10);
        GlbHeaderResult result = validator.validate(data);
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().get(0).contains("версия"));
        assertTrue(result.errors().get(1).contains("Длина в заголовке"));
    }
}
