"""2026-05-11 新版 generator smoke 测试。

只断言活跃候选清单和新加的 strict_tuple 变体的基本属性。完整的历史行为
覆盖见 `tests/_archive_manual_json_extractor/test_generate_candidate_prompts_v1.py`
（归档不运行）。
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = PROJECT_ROOT / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from prompt_tuning.generate_candidate_prompts import (
    STRICT_TUPLE_FORMAT_BLOCK,
    PRECISION_SUPPRESSION_BLOCK,
    build_schema_fewshot_distilled_v2_strict_tuple_prompt,
)


class TestStrictTupleBuilder(unittest.TestCase):
    def test_builder_appends_precision_then_strict_tuple(self):
        base = "base prompt body\n\n-Something-\n..."
        result = build_schema_fewshot_distilled_v2_strict_tuple_prompt(base)
        self.assertIn("base prompt body", result)
        # 精度向抑制规则应当在严格 tuple 块之前
        self.assertLess(
            result.index(PRECISION_SUPPRESSION_BLOCK.splitlines()[0]),
            result.index(STRICT_TUPLE_FORMAT_BLOCK.splitlines()[0]),
        )
        # 两段都应完整出现
        self.assertIn("-精度向抑制规则", result)
        self.assertIn("-严格 tuple 输出格式约束", result)

    def test_strict_tuple_block_contains_key_rules(self):
        # 桥接验证发现的三大格式缺陷：引号、括号、边界分隔符
        self.assertIn("不要**在 entity_name", STRICT_TUPLE_FORMAT_BLOCK)
        self.assertIn("括号严格成对", STRICT_TUPLE_FORMAT_BLOCK)
        self.assertIn("##", STRICT_TUPLE_FORMAT_BLOCK)

    def test_precision_suppression_covers_nine_categories(self):
        # 对应 manifest.json 里列出的 9 类抑制对象，任一缺失都视为回归
        expected_keywords = [
            "页眉页脚",
            "章节通用前言",
            "过渡性短语",
            "辅助说明性短语",
            "单独出现的动词",
            "无命名实体指向的代词",
            "没有任何属性描述",
            "简称或缩写",
            "通用名词",
        ]
        for kw in expected_keywords:
            self.assertIn(kw, PRECISION_SUPPRESSION_BLOCK, f"missing: {kw}")


if __name__ == "__main__":
    unittest.main()
