"""
Процедурная реализация алгоритмов валидации 3D-моделей мебели и фильтрации каталога.
"""

import struct
from typing import Dict, List, Any, Optional

# Константы формата glTF/GLB (ISO/IEC 12113:2022)
GLB_MAGIC = b'glTF'              # Сигнатура 0x46546C67
GLB_HEADER_SIZE = 12            # 12 байт: magic(4), version(4), length(4)
GLB_CHUNK_HEADER_SIZE = 8       # 8 байт: chunkLength(4), chunkType(4)
GLB_CHUNK_TYPE_JSON = 0x4E4F534A # Тип первого чанка: JSON

# Профили торговых площадок
MARKETPLACE_PROFILES = {
    "wildberries": {
        "name": "Wildberries (Раздел «Мебель»)",
        "max_file_size_bytes": 25 * 1024 * 1024,  # Лимит 25 МБ
        "required_formats": ["GLB", "USDZ"],     # Обязательны оба формата
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
        "is_valid": False,
        "magic": None,
        "version": None,
        "declared_length": None,
        "actual_length": len(file_bytes),
        "json_chunk_length": None,
        "errors": []
    }

    if len(file_bytes) < (GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE):
        result["errors"].append(f"Размер файла ({len(file_bytes)} байт) меньше заголовка GLB (20 байт)")
        return result

    # Распаковка первых 12 байт (little-endian: 4sII)
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

    # Проверка первого чанка (байты 12..20)
    chunk_length, chunk_type = struct.unpack(
        '<II', file_bytes[GLB_HEADER_SIZE : GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE]
    )
    result["json_chunk_length"] = chunk_length

    if chunk_type != GLB_CHUNK_TYPE_JSON:
        result["errors"].append(f"Первый чанк не JSON (0x4E4F534A), получено 0x{chunk_type:08X}")

    if not result["errors"]:
        result["is_valid"] = True

    return result


def validate_model_for_marketplace(model_data: Dict[str, Any], platform_key: str) -> Dict[str, Any]:
    """Алгоритм 2: Проверка требований маркетплейса (GLB+USDZ, лимит 25 МБ). Сложность O(k)."""
    if platform_key not in MARKETPLACE_PROFILES:
        return {"status": "ERROR", "message": f"Неизвестный профиль площадки: '{platform_key}'"}

    profile = MARKETPLACE_PROFILES[platform_key]
    max_size = profile["max_file_size_bytes"]
    required_formats = set(profile["required_formats"])

    files = model_data.get("files", [])
    present_formats = set(f.get("format", "").upper() for f in files)

    report = {
        "sku": model_data.get("sku"),
        "platform": profile["name"],
        "status": "PASSED",
        "file_issues": [],
        "passed_checks": []
    }

    # Проверка наличия обязательных форматов
    missing = required_formats - present_formats
    if missing:
        report["status"] = "FAILED"
        report["file_issues"].append(f"Отсутствуют обязательные форматы: {', '.join(sorted(missing))}")
    else:
        report["passed_checks"].append(f"Все обязательные форматы присутствуют: {', '.join(sorted(required_formats))}")

    # Проверка размера и бинарного заголовка каждого файла
    for f in files:
        fmt = f.get("format", "").upper()
        size = f.get("size_bytes", 0)
        content = f.get("content_bytes")
        file_name = f.get("file_name", "model_file")

        if size > max_size:
            report["status"] = "FAILED"
            report["file_issues"].append(
                f"Файл '{file_name}' ({fmt}) превышает лимит: {size / (1024*1024):.2f} МБ > {max_size / (1024*1024):.2f} МБ"
            )
        else:
            report["passed_checks"].append(f"Файл '{file_name}' укладывается в весовой лимит")

        if fmt == "GLB" and content is not None:
            header_res = validate_glb_binary_header(content)
            if not header_res["is_valid"]:
                report["status"] = "FAILED"
                report["file_issues"].extend(header_res["errors"])
            else:
                report["passed_checks"].append(f"Заголовок GLB '{file_name}' валиден")

    return report


def filter_catalog(
    catalog: List[Dict[str, Any]],
    search_query: Optional[str] = None,
    category: Optional[str] = None,
    platform_readiness: Optional[str] = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None
) -> List[Dict[str, Any]]:
    """Алгоритм 3: Многокритериальная фильтрация каталога мебели с контролем 3D/AR. Сложность O(N)."""
    filtered_items = []
    query_lower = search_query.strip().lower() if search_query else None

    for item in catalog:
        # Проверка названия
        name_matched = bool(query_lower and query_lower in item.get("name", "").lower())

        # Фильтр категории
        if category:
            item_cat = str(item.get("category", "")).lower()
            item_cat_id = str(item.get("category_id", "")).lower()
            if category.strip().lower() not in (item_cat, item_cat_id):
                continue

        variants = item.get("product_variants", [])
        if not variants and ("price" in item or "sku" in item):
            variants = [item]

        matching_variants = []

        for var in variants:
            var_sku = var.get("sku", "")
            sku_matched = bool(query_lower and query_lower in var_sku.lower())

            if query_lower and not (name_matched or sku_matched):
                continue

            price = float(var.get("price", 0.0))
            if min_price is not None and price < min_price:
                continue
            if max_price is not None and price > max_price:
                continue

            # Проверка готовности 3D-модели для площадки (is_current == True)
            if platform_readiness:
                models = var.get("models_3d", [])
                current_model = None

                if isinstance(models, list):
                    for m in models:
                        if m.get("is_current", False) is True:
                            current_model = m
                            break
                    if not current_model and models:
                        current_model = max(models, key=lambda m: m.get("version", 1))
                elif isinstance(models, dict):
                    current_model = models
                elif var.get("model_3d"):
                    current_model = var["model_3d"]
                elif item.get("model_3d"):
                    current_model = item["model_3d"]

                if not current_model:
                    continue

                val_report = validate_model_for_marketplace(current_model, platform_readiness)
                if val_report.get("status") != "PASSED":
                    continue

            matching_variants.append(var)

        if matching_variants:
            filtered_item = dict(item)
            if "product_variants" in item:
                filtered_item["product_variants"] = matching_variants
            filtered_items.append(filtered_item)

    return filtered_items


def create_synthetic_glb() -> bytes:
    """Генерация тестового GLB 2.0 (48 байт)."""
    json_bytes = b'{"asset":{"version":"2.0"}}   '
    chunk_len = len(json_bytes)
    total_len = GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE + chunk_len
    header = struct.pack('<4sII', GLB_MAGIC, 2, total_len)
    chunk_hdr = struct.pack('<II', chunk_len, GLB_CHUNK_TYPE_JSON)
    return header + chunk_hdr + json_bytes


if __name__ == "__main__":
    valid_glb_bytes = create_synthetic_glb()

    # Тесты Алгоритма 1
    t1 = validate_glb_binary_header(valid_glb_bytes)
    print(f"\n[ТЕСТ 1.1] Валидный GLB: is_valid={t1['is_valid']}, magic='{t1['magic']}', version={t1['version']}")

    bad_magic_bytes = b'BADF' + valid_glb_bytes[4:]
    t2 = validate_glb_binary_header(bad_magic_bytes)
    print(f"[ТЕСТ 1.2] Ошибка magic: is_valid={t2['is_valid']}, ошибки={t2['errors']}")

    # Тесты Алгоритма 2
    model_chair = {
        "sku": "CHAIR-SK-01-GREY",
        "version": 1,
        "is_current": True,
        "files": [
            {"file_name": "chair.glb", "format": "GLB", "size_bytes": len(valid_glb_bytes), "content_bytes": valid_glb_bytes},
            {"file_name": "chair.usdz", "format": "USDZ", "size_bytes": 14 * 1024 * 1024, "content_bytes": None}
        ]
    }
    t3 = validate_model_for_marketplace(model_chair, "wildberries")
    print(f"\n[ТЕСТ 2.1] Кресло для Wildberries (GLB + USDZ): вердикт={t3['status']}")

    model_sofa_no_usdz = {
        "sku": "SOFA-NR-02-BLUE",
        "version": 1,
        "is_current": True,
        "files": [{"file_name": "sofa.glb", "format": "GLB", "size_bytes": 18 * 1024 * 1024, "content_bytes": valid_glb_bytes}]
    }
    t4 = validate_model_for_marketplace(model_sofa_no_usdz, "wildberries")
    print(f"[ТЕСТ 2.2] Диван без USDZ (отклонение WB): вердикт={t4['status']}, ошибки={t4['file_issues']}")

    # Тесты Алгоритма 3
    sample_catalog = [
        {
            "id": "prod-1",
            "name": "Кресло скандинавское 'Осло'",
            "category": "Кресла",
            "product_variants": [
                {"sku": "CHAIR-SK-01-GREY", "price": 18900.0, "models_3d": [model_chair]}
            ]
        },
        {
            "id": "prod-2",
            "name": "Диван прямой 'Нордик 3'",
            "category": "Мягкая мебель",
            "product_variants": [
                {"sku": "SOFA-NR-02-BLUE", "price": 34900.0, "models_3d": [model_sofa_no_usdz]}
            ]
        }
    ]

    res_wb = filter_catalog(sample_catalog, platform_readiness="wildberries")
    print(f"\n[ТЕСТ 3] Фильтр по готовности к Wildberries: найдено {len(res_wb)} позиций:")
    for item in res_wb:
        print(f" -> {item['name']} ({item['product_variants'][0]['sku']})")

