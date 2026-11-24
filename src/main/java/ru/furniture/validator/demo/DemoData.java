package ru.furniture.validator.demo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import ru.furniture.validator.glb.GlbBinaryHeaderValidator;

/** Построение синтетических GLB для демонстрации и тестов. */
public final class DemoData {

    private DemoData() {
    }

    /** Корректный GLB 2.0 из 48 байт: JSON-чанк длиной 28 (кратно 4). */
    public static byte[] validGlb() {
        return glb(28, 28, GlbBinaryHeaderValidator.CHUNK_TYPE_JSON, null);
    }

    /** Тот же файл, но в заголовке первого чанка указана длина chunkLength (общая длина файла не меняется). */
    public static byte[] glbWithChunkLength(long chunkLength) {
        return glb(28, chunkLength, GlbBinaryHeaderValidator.CHUNK_TYPE_JSON, null);
    }

    /**
     * @param payloadLength реальная длина полезной нагрузки чанка
     * @param chunkLength   длина чанка, записанная в заголовке (uint32)
     * @param declared      объявленная длина файла; null — фактическая
     */
    public static byte[] glb(int payloadLength, long chunkLength, long chunkType, Long declared) {
        byte[] payload = new byte[payloadLength];
        Arrays.fill(payload, (byte) ' ');
        byte[] json = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(json, 0, payload, 0, Math.min(json.length, payloadLength));
        int total = 20 + payloadLength;
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("glTF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(2);
        buffer.putInt((int) (declared == null ? total : declared));
        buffer.putInt((int) chunkLength);
        buffer.putInt((int) chunkType);
        buffer.put(payload);
        return buffer.array();
    }
}
