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

    # Границы первого чанка: данные чанка не должны выходить за конец файла и за объявленную длину
    chunk_end = GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE + chunk_length
    if chunk_end > len(file_bytes):
        result["errors"].append(
            f"Длина первого чанка {chunk_length} байт выходит за конец файла "
            f"(конец чанка {chunk_end} > длина файла {len(file_bytes)})"
        )
    elif chunk_end > declared_length:
        result["errors"].append(
            f"Длина первого чанка {chunk_length} байт выходит за объявленную длину файла {declared_length}"
        )
    if chunk_length % 4 != 0:
        result["errors"].append(f"Длина первого чанка {chunk_length} не кратна 4 (требование выравнивания glTF 2.0)")

    if not result["errors"]:
        result["is_valid"] = True

    return result


def validate_model_for_marketplace(model_data: Dict[str, Any], platform_key: str) -> Dict[str, Any]:
    """Алгоритм 2: Проверка модели по профилю площадки (форматы, размер, заголовок GLB). Сложность O(k)."""
    if platform_key not in MARKETPLACE_PROFILES:
        return {"status": "ERROR", "message": f"Неизвестный профиль площадки: '{platform_key}'"}

    profile = MARKETPLACE_PROFILES[platform_key]
    max_size = profile["max_file_size_bytes"]
    required_formats = set(profile["required_formats"])

    files = model_data.get("files") or []
    present_formats = set(str(f.get("format") or "").upper() for f in files)

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
        fmt = str(f.get("format") or "").upper()
        size = f.get("size_bytes")
        content = f.get("content_bytes")
        file_name = f.get("file_name", "model_file")

        if not isinstance(size, int) or isinstance(size, bool) or size < 0:
            report["status"] = "FAILED"
            report["file_issues"].append(f"Файл '{file_name}' ({fmt}): размер size_bytes не указан или некорректен")
        elif size > max_size:
            report["status"] = "FAILED"
            report["file_issues"].append(
                f"Файл '{file_name}' ({fmt}) превышает лимит: {size / (1024*1024):.2f} МБ > {max_size / (1024*1024):.2f} МБ"
            )
        else:
            report["passed_checks"].append(f"Файл '{file_name}' укладывается в весовой лимит")

        if fmt == "GLB":
            if not isinstance(content, (bytes, bytearray)):
                report["status"] = "FAILED"
                report["file_issues"].append(
                    f"Файл '{file_name}' (GLB): content_bytes отсутствуют, заголовок не проверен"
                )
                continue
            header_res = validate_glb_binary_header(bytes(content))
            if not header_res["is_valid"]:
                report["status"] = "FAILED"
                report["file_issues"].extend(header_res["errors"])
            else:
                report["passed_checks"].append(f"Заголовок GLB '{file_name}' валиден")

    return report


def select_current_model(item: Dict[str, Any], var: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Выбор модели варианта: is_current=True, иначе максимальная version; затем model_3d варианта и товара."""
    models = var.get("models_3d")
    if isinstance(models, dict):
        return models
    if isinstance(models, list) and models:
        for m in models:
            if m.get("is_current", False) is True:
                return m
        return max(models, key=lambda m: m.get("version", 1))
    return var.get("model_3d") or item.get("model_3d") or None


def filter_catalog(
    catalog: List[Dict[str, Any]],
    search_query: Optional[str] = None,
    category: Optional[str] = None,
    platform_readiness: Optional[str] = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None
) -> List[Dict[str, Any]]:
    """Алгоритм 3: Многокритериальная фильтрация каталога мебели с контролем 3D/AR.
    Сложность O(N·V·(m + k + q)). Неизвестный ключ платформы — ValueError."""
    if platform_readiness and platform_readiness not in MARKETPLACE_PROFILES:
        raise ValueError(f"Неизвестный профиль площадки: '{platform_readiness}'")
    filtered_items = []
    query_lower = search_query.strip().lower() if search_query else None

    for item in catalog:
        # Проверка названия
        name_matched = bool(query_lower and query_lower in str(item.get("name") or "").lower())

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
            var_sku = str(var.get("sku") or "")
            sku_matched = bool(query_lower and query_lower in var_sku.lower())

            if query_lower and not (name_matched or sku_matched):
                continue

            if min_price is not None or max_price is not None:
                if var.get("price") is None:
                    continue  # цена неизвестна — по границам отобрать нельзя
                price = float(var["price"])
                if min_price is not None and price < min_price:
                    continue
                if max_price is not None and price > max_price:
                    continue

            # Проверка готовности 3D-модели для площадки (is_current == True)
            if platform_readiness:
                current_model = select_current_model(item, var)

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
    json_bytes = b'{"asset":{"version":"2.0"}}'.ljust(28, b" ")  # длина чанка кратна 4
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

    overlong = valid_glb_bytes[:12] + struct.pack('<II', 4000, GLB_CHUNK_TYPE_JSON) + valid_glb_bytes[20:]
    t2b = validate_glb_binary_header(overlong)
    print(f"[ТЕСТ 1.3] Длина JSON-чанка выходит за файл: is_valid={t2b['is_valid']}, ошибки={t2b['errors']}")

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

    model_no_bytes = {"sku": "CHAIR-NB", "files": [
        {"file_name": "c.glb", "format": "GLB", "size_bytes": 1000, "content_bytes": None},
        {"file_name": "c.usdz", "format": "USDZ", "size_bytes": 1000, "content_bytes": None}]}
    t5 = validate_model_for_marketplace(model_no_bytes, "wildberries")
    print(f"[ТЕСТ 2.3] GLB без content_bytes: вердикт={t5['status']}, ошибки={t5['file_issues']}")

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

