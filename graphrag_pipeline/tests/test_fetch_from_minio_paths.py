#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""fetch_from_minio MinIO object key 兼容路径测试。"""

import sys
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from fetch_from_minio import build_candidate_object_keys


def test_build_candidate_object_keys_prefers_material_then_pdf_namespace():
    keys = build_candidate_object_keys(
        course_id="os",
        graphrag_prefix="graphrag",
        filename="section_docs.json",
        pdf_file_id=None,
        material_id=7,
    )

    assert keys == [
        "os/graphrag/material_7/section_docs.json",
        "os/graphrag/pdf_7/section_docs.json",
    ]


def test_build_candidate_object_keys_keeps_legacy_pdf_namespace():
    keys = build_candidate_object_keys(
        course_id="os",
        graphrag_prefix="graphrag",
        filename="section_docs.json",
        pdf_file_id=7,
        material_id=None,
    )

    assert keys == ["os/graphrag/pdf_7/section_docs.json"]
