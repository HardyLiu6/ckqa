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
    def test_builder_appends_strict_tuple_block(self):
        base = "base prompt body\n\n-Something-\n..."
        result = build_schema_fewshot_distilled_v2_strict_tuple_prompt(base)
        self.assertIn("base prompt body", result)
        # 2026-05-11 exp5 之后只追加 strict_tuple 格式约束，精度向抑制规则回滚
        self.assertIn("-严格 tuple 输出格式约束", result)
        self.assertNotIn("-精度向抑制规则", result)

    def test_strict_tuple_block_contains_key_rules(self):
        # 桥接验证发现的三大格式缺陷：引号、括号、边界分隔符
        self.assertIn("不要**在 entity_name", STRICT_TUPLE_FORMAT_BLOCK)
        self.assertIn("括号严格成对", STRICT_TUPLE_FORMAT_BLOCK)
        self.assertIn("##", STRICT_TUPLE_FORMAT_BLOCK)

    def test_precision_suppression_block_is_archived_but_preserved(self):
        # 2026-05-11 exp5 验证该规则让 recall -36%、precision 几乎不变，已回滚。
        # 保留常量作为失败实验的记录。
        self.assertIn("页眉页脚", PRECISION_SUPPRESSION_BLOCK)


if __name__ == "__main__":
    unittest.main()
