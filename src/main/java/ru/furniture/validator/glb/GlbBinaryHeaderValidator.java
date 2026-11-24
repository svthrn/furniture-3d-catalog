package ru.furniture.validator.glb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Алгоритм 1: проверка 12-байтового заголовка GLB и границ первого чанка (ISO/IEC 12113:2022).
 * Сложность O(1). JSON и последующие чанки не разбираются.
 */
public final class GlbBinaryHeaderValidator {

    public static final int HEADER_SIZE = 12;
    public static final int CHUNK_HEADER_SIZE = 8;
    public static final long CHUNK_TYPE_JSON = 0x4E4F534AL;
    private static final String MAGIC = "glTF";
    private static final long SUPPORTED_VERSION = 2L;

    public GlbHeaderResult validate(byte[] fileBytes) {
        byte[] bytes = fileBytes == null ? new byte[0] : fileBytes;
        List<String> errors = new ArrayList<>();
        int actualLength = bytes.length;

        if (actualLength < HEADER_SIZE + CHUNK_HEADER_SIZE) {
            errors.add("Размер файла (" + actualLength + " байт) меньше заголовка GLB (20 байт)");
            return new GlbHeaderResult(false, null, null, null, actualLength, null, errors);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magicBytes = new byte[4];
        buffer.get(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);
        long version = Integer.toUnsignedLong(buffer.getInt());
        long declaredLength = Integer.toUnsignedLong(buffer.getInt());
        long chunkLength = Integer.toUnsignedLong(buffer.getInt());
        long chunkType = Integer.toUnsignedLong(buffer.getInt());

        if (!MAGIC.equals(magic)) {
            errors.add("Некорректная сигнатура magic: ожидалось 'glTF', получено '" + magic + "'");
        }
        if (version != SUPPORTED_VERSION) {
            errors.add("Неподдерживаемая версия glTF: ожидается 2, получено " + version);
        }
        if (declaredLength != actualLength) {
            errors.add("Длина в заголовке " + declaredLength + " байт не равна фактической " + actualLength + " байт");
        }
        if (chunkType != CHUNK_TYPE_JSON) {
            errors.add(String.format("Первый чанк не JSON (0x4E4F534A), получено 0x%08X", chunkType));
        }

        long chunkEnd = HEADER_SIZE + CHUNK_HEADER_SIZE + chunkLength;
        if (chunkEnd > actualLength) {
            errors.add("Длина первого чанка " + chunkLength + " байт выходит за конец файла (конец чанка "
                    + chunkEnd + " > длина файла " + actualLength + ")");
        } else if (chunkEnd > declaredLength) {
            errors.add("Длина первого чанка " + chunkLength + " байт выходит за объявленную длину файла "
                    + declaredLength);
        }
        if (chunkLength % 4 != 0) {
            errors.add("Длина первого чанка " + chunkLength + " не кратна 4 (требование выравнивания glTF 2.0)");
        }

        return new GlbHeaderResult(errors.isEmpty(), magic, version, declaredLength, actualLength, chunkLength, errors);
    }
}
