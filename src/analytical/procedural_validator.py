"""
Процедурная реализация алгоритмов валидации 3D-моделей мебели и фильтрации каталога.
"""

import struct
import os
import hashlib
from typing import Dict, List, Any, Optional

GLB_MAGIC = b'glTF'              # 0x46546C67
GLB_HEADER_SIZE = 12
GLB_CHUNK_HEADER_SIZE = 8
GLB_CHUNK_TYPE_JSON = 0x4E4F534A # 'JSON'

MARKETPLACE_PROFILES = {
    "wildberries": {
        "name": "Wildberries (Раздел «Мебель»)",
        "max_file_size_bytes": 25 * 1024 * 1024,  # Лимит 25 МБ
        "required_formats": ["GLB", "USDZ"],     # Обязательно оба формата
        "strict_glb_version": 2
    },
    "yandex_market": {
        "name": "Яндекс Маркет",
        "max_file_size_bytes": 50 * 1024 * 1024,
        "required_formats": ["GLB"],
        "strict_glb_version": 2
    },
    "own_web_store": {
        "name": "Собственный интернет-магазин (<model-viewer>)",
        "max_file_size_bytes": 30 * 1024 * 1024,
        "required_formats": ["GLB"],
        "strict_glb_version": 2
    }
}

def validate_glb_binary_header(file_bytes: bytes) -> Dict[str, Any]:
    """Алгоритм 1: Низкоуровневая валидация заголовка GLB (ISO/IEC 12113:2022). Сложность O(1)."""
    result = {
        "is_valid": False, "magic": None, "version": None,
        "declared_length": None, "actual_length": len(file_bytes),
        "json_chunk_length": None, "errors": []
    }
    if len(file_bytes) < (GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE):
        result["errors"].append(f"Размер файла ({len(file_bytes)} байт) меньше заголовка GLB (20 байт)")
        return result

    magic_bytes, version, declared_length = struct.unpack('<4sII', file_bytes[:GLB_HEADER_SIZE])
    result["magic"] = magic_bytes.decode('ascii', errors='replace')
    result["version"] = version
    result["declared_length"] = declared_length

    if magic_bytes != GLB_MAGIC:
        result["errors"].append(f"Некорректная сигнатура magic: ожидалось 'glTF', получено '{result['magic']}'")
    if version != 2:
        result["errors"].append(f"Неподдерживаемая версия glTF: ожидается 2, получено {version}")
    if declared_length != len(file_bytes):
        result["errors"].append(f"Длина в заголовке {declared_length} байт не равна фактической {len(file_bytes)} байт")

    chunk_length, chunk_type = struct.unpack('<II', file_bytes[GLB_HEADER_SIZE : GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE])
    result["json_chunk_length"] = chunk_length
    if chunk_type != GLB_CHUNK_TYPE_JSON:
        result["errors"].append(f"Первый чанк не JSON (0x4E4F534A), получено 0x{chunk_type:08X}")

    if not result["errors"]:
        result["is_valid"] = True
    return result

def validate_model_for_marketplace(model_data: Dict[str, Any], platform_key: str) -> Dict[str, Any]:
    """Алгоритм 2: Проверка требований маркетплейса (GLB+USDZ, лимит 25 МБ). Сложность O(1)."""
    profile = MARKETPLACE_PROFILES[platform_key]
    max_size = profile["max_file_size_bytes"]
    required_formats = set(profile["required_formats"])
    files = model_data.get("files", [])
    present_formats = set(f.get("format", "").upper() for f in files)

    report = {
        "sku": model_data.get("sku"), "platform": profile["name"],
        "status": "PASSED", "file_issues": [], "passed_checks": []
    }
    missing = required_formats - present_formats
    if missing:
        report["status"] = "FAILED"
        report["file_issues"].append(f"Отсутствуют обязательные форматы: {', '.join(missing)}")
    else:
        report["passed_checks"].append(f"Все обязательные форматы присутствуют: {', '.join(required_formats)}")

    for f in files:
        if f.get("size_bytes", 0) > max_size:
            report["status"] = "FAILED"
            report["file_issues"].append(f"Файл '{f['file_name']}' превышает лимит {max_size // (1024*1024)} МБ")
        else:
            report["passed_checks"].append(f"Файл '{f['file_name']}' укладывается в весовой лимит")

        if f.get("format", "").upper() == "GLB" and f.get("content_bytes"):
            h_res = validate_glb_binary_header(f["content_bytes"])
            if not h_res["is_valid"]:
                report["status"] = "FAILED"
                report["file_issues"].extend(h_res["errors"])
    return report

def create_synthetic_glb() -> bytes:
    """Генерирует минимальный корректный GLB 2.0 (48 байт)."""
    json_bytes = b'{"asset":{"version":"2.0"}}   '
    chunk_len = len(json_bytes)
    total_len = 12 + 8 + chunk_len
    header = struct.pack('<4sII', GLB_MAGIC, 2, total_len)
    chunk_hdr = struct.pack('<II', chunk_len, GLB_CHUNK_TYPE_JSON)
    return header + chunk_hdr + json_bytes

if __name__ == "__main__":
    print("ТЕСТИРОВАНИЕ ПРОЦЕДУРНЫХ АЛГОРИТМОВ ВАЛИДАЦИИ 3D-КАТАЛОГА МЕБЕЛИ")
    valid_glb = create_synthetic_glb()

    # Тест 1
    t1 = validate_glb_binary_header(valid_glb)
    print(f"\n[ТЕСТ 1] Проверка валидного GLB: is_valid={t1['is_valid']}, magic={t1['magic']}, version={t1['version']}")

    # Тест 2
    bad_glb = b'BADF' + valid_glb[4:]
    t2 = validate_glb_binary_header(bad_glb)
    print(f"[ТЕСТ 2] Проверка битого заголовка: is_valid={t2['is_valid']}, ошибки={t2['errors']}")

    # Тест 3
    sample_chair = {
        "sku": "CHAIR-SK-01-GREY",
        "files": [
            {"file_name": "chair.glb", "format": "GLB", "size_bytes": len(valid_glb), "content_bytes": valid_glb},
            {"file_name": "chair.usdz", "format": "USDZ", "size_bytes": 14 * 1024 * 1024, "content_bytes": None}
        ]
    }
    t3 = validate_model_for_marketplace(sample_chair, "wildberries")
    print(f"[ТЕСТ 3] Модель кресла для Wildberries (GLB + USDZ): вердикт={t3['status']}")

    # Тест 4
    sample_sofa = {
        "sku": "SOFA-NR-02-BLUE",
        "files": [{"file_name": "sofa.glb", "format": "GLB", "size_bytes": 18 * 1024 * 1024, "content_bytes": valid_glb}]
    }
    t4 = validate_model_for_marketplace(sample_sofa, "wildberries")
    print(f"[ТЕСТ 4] Модель дивана без USDZ (отклонение WB): вердикт={t4['status']}, ошибки={t4['file_issues']}")