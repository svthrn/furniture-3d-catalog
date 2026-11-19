"""Автоматизированные тесты процедурного валидатора (запуск: python -m unittest discover -s tests -v)."""
import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src", "analytical"))

import procedural_validator as pv  # noqa: E402


def glb(chunk_len=28, declared=None, chunk_type=pv.GLB_CHUNK_TYPE_JSON, payload=None):
    """Собирает GLB; payload по умолчанию дополняет JSON-чанк пробелами до chunk_len."""
    if payload is None:
        payload = b'{"asset":{"version":"2.0"}}'.ljust(chunk_len, b" ")
    total = 20 + len(payload)
    header = struct.pack("<4sII", b"glTF", 2, total if declared is None else declared)
    return header + struct.pack("<II", chunk_len, chunk_type) + payload


def model(files, sku="X"):
    return {"sku": sku, "files": files}


def f(fmt, size, content=None, name=None):
    return {"file_name": name or f"m.{fmt.lower()}", "format": fmt,
            "size_bytes": size, "content_bytes": content}


class GlbHeaderTests(unittest.TestCase):
    def test_valid(self):
        self.assertTrue(pv.validate_glb_binary_header(glb())["is_valid"])

    def test_bad_magic(self):
        data = b"BADF" + glb()[4:]
        self.assertFalse(pv.validate_glb_binary_header(data)["is_valid"])

    def test_first_chunk_length_beyond_file_end(self):
        # длина JSON-чанка в заголовке больше, чем осталось байт; общая длина файла согласована
        data = glb(chunk_len=28)
        data = data[:12] + struct.pack("<II", 4000, pv.GLB_CHUNK_TYPE_JSON) + data[20:]
        res = pv.validate_glb_binary_header(data)
        self.assertFalse(res["is_valid"])
        self.assertTrue(any("чанк" in e.lower() for e in res["errors"]))

    def test_first_chunk_length_not_aligned(self):
        data = glb(chunk_len=27, payload=b'{"asset":{"version":"2.0"}}')
        self.assertFalse(pv.validate_glb_binary_header(data)["is_valid"])

    def test_truncated_file(self):
        self.assertFalse(pv.validate_glb_binary_header(glb()[:15])["is_valid"])


class MarketplaceTests(unittest.TestCase):
    def test_chair_passes(self):
        data = glb()
        m = model([f("GLB", len(data), data), f("USDZ", 14 * 1024 * 1024)])
        self.assertEqual(pv.validate_model_for_marketplace(m, "wildberries")["status"], "PASSED")

    def test_glb_without_content_bytes_is_not_passed(self):
        m = model([f("GLB", 1000, None), f("USDZ", 1000)])
        rep = pv.validate_model_for_marketplace(m, "wildberries")
        self.assertEqual(rep["status"], "FAILED")
        self.assertTrue(any("content_bytes" in x for x in rep["file_issues"]))

    def test_glb_with_overlong_chunk_is_not_passed(self):
        data = glb(chunk_len=28)
        data = data[:12] + struct.pack("<II", 4000, pv.GLB_CHUNK_TYPE_JSON) + data[20:]
        m = model([f("GLB", len(data), data), f("USDZ", 1000)])
        self.assertEqual(pv.validate_model_for_marketplace(m, "wildberries")["status"], "FAILED")

    def test_missing_size_is_not_passed(self):
        data = glb()
        m = model([{"file_name": "a.glb", "format": "GLB", "content_bytes": data}])
        self.assertEqual(pv.validate_model_for_marketplace(m, "own_web_store")["status"], "FAILED")

    def test_size_equal_to_limit_passes_and_over_fails(self):
        data = glb()
        limit = pv.MARKETPLACE_PROFILES["own_web_store"]["max_file_size_bytes"]
        ok = pv.validate_model_for_marketplace(model([f("GLB", limit, data)]), "own_web_store")
        bad = pv.validate_model_for_marketplace(model([f("GLB", limit + 1, data)]), "own_web_store")
        self.assertEqual((ok["status"], bad["status"]), ("PASSED", "FAILED"))

    def test_unknown_profile(self):
        self.assertEqual(pv.validate_model_for_marketplace(model([]), "nope")["status"], "ERROR")

    def test_missing_format_field_does_not_crash(self):
        m = model([{"file_name": "a.bin", "size_bytes": 1000, "content_bytes": None}])
        rep = pv.validate_model_for_marketplace(m, "wildberries")
        self.assertEqual(rep["status"], "FAILED")
        self.assertIn("Отсутствуют обязательные форматы: GLB, USDZ", rep["file_issues"])


class FilterTests(unittest.TestCase):
    def setUp(self):
        data = glb()
        self.good = model([f("GLB", len(data), data), f("USDZ", 1000)], "G")
        self.no_content = model([f("GLB", 1000, None), f("USDZ", 1000)], "N")

    def test_glb_without_content_is_excluded(self):
        cat = [{"name": "A", "product_variants": [{"sku": "N", "price": 1.0, "models_3d": [self.no_content]}]}]
        self.assertEqual(pv.filter_catalog(cat, platform_readiness="wildberries"), [])

    def test_model_3d_field_of_variant_is_used(self):
        cat = [{"name": "A", "product_variants": [{"sku": "G", "price": 1.0, "model_3d": self.good}]}]
        self.assertEqual(len(pv.filter_catalog(cat, platform_readiness="wildberries")), 1)

    def test_model_3d_field_of_item_is_used(self):
        cat = [{"name": "A", "sku": "G", "price": 1.0, "model_3d": self.good}]
        self.assertEqual(len(pv.filter_catalog(cat, platform_readiness="wildberries")), 1)

    def test_current_model_has_priority_over_max_version(self):
        old_ok = dict(self.good, version=1, is_current=True)
        new_bad = dict(self.no_content, version=2, is_current=False)
        cat = [{"name": "A", "product_variants": [{"sku": "G", "price": 1.0, "models_3d": [old_ok, new_bad]}]}]
        self.assertEqual(len(pv.filter_catalog(cat, platform_readiness="wildberries")), 1)

    def test_missing_price_excluded_when_bounds_set(self):
        cat = [{"name": "A", "product_variants": [{"sku": "G"}]}]
        self.assertEqual(pv.filter_catalog(cat, max_price=100.0), [])
        self.assertEqual(len(pv.filter_catalog(cat)), 1)

    def test_unknown_platform_raises(self):
        with self.assertRaises(ValueError):
            pv.filter_catalog([], platform_readiness="nope")

    def test_search_with_missing_sku_does_not_crash(self):
        cat = [{"product_variants": [{"price": 1.0}]},
               {"name": None, "product_variants": [{"sku": None, "price": 1.0}]}]
        self.assertEqual(pv.filter_catalog(cat, search_query="abc"), [])


if __name__ == "__main__":
    unittest.main()
