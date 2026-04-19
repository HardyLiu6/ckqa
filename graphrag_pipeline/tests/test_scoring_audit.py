from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from scoring_audit import (
    compute_audit_entity_precision,
    compute_audit_entity_recall,
    compute_audit_relation_recall,
    load_audit_index,
)


AUDIT_PAYLOAD = {
    "audit_samples": [
        {
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "ent-1", "name": "操作系统", "type": "Course",
                 "alias": [], "normalized_name": "操作系统"},
                {"entity_id": "ent-2", "name": "第一章 引论", "type": "Chapter",
                 "alias": [], "normalized_name": "第一章 引论"},
            ],
            "gold_relations": [
                {"relation_id": "rel-1", "source_entity_id": "ent-1",
                 "target_entity_id": "ent-2", "type": "contains"}
            ],
        }
    ]
}


def _success_result(sample_id, entities, relationships):
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate="c",
        status="success",
        entities=[ExtractionEntity(**e) for e in entities],
        relationships=[ExtractionRelationship(**r) for r in relationships],
        raw_output="",
    )


class TestAuditAlignment(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.audit_path = Path(self.tmpdir.name) / "audit.json"
        self.audit_path.write_text(
            json.dumps(AUDIT_PAYLOAD, ensure_ascii=False), encoding="utf-8"
        )
        self.index = load_audit_index(self.audit_path)

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_entity_recall_full_hit(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 1.0)

    def test_entity_recall_half_hit(self):
        results = [
            _success_result(
                "s1",
                [{"id": "e1", "title": "操作系统", "type": "Course"}],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.5)

    def test_entity_recall_missing_sample_excluded(self):
        results = [_success_result("s_not_in_audit", [], [])]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.0)

    def test_relation_recall_hit(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章 引论", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_audit_relation_recall(results, self.index), 1.0)

    def test_relation_recall_requires_type_match(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章 引论", "type": "related_to"}],
            )
        ]
        self.assertEqual(compute_audit_relation_recall(results, self.index), 0.0)


SHORT_GOLD_PAYLOAD = {
    "audit_samples": [
        {
            "source_sample_id": "s_short",
            "gold_entities": [
                {"entity_id": "ent-short-1", "name": "进程", "type": "Concept"},
                {"entity_id": "ent-short-2", "name": "文件", "type": "Concept"},
            ],
            "gold_relations": [],
        },
        {
            "source_sample_id": "s_long",
            "gold_entities": [
                {"entity_id": "ent-long-1", "name": "第一章 引论", "type": "Chapter"},
            ],
            "gold_relations": [],
        },
    ]
}


class TestShortGoldEntityGuard(unittest.TestCase):
    """Gold 归一化长度 < 4 时，必须精确相等才算命中，避免子串假阳性。"""

    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.audit_path = Path(self.tmpdir.name) / "audit.json"
        self.audit_path.write_text(
            json.dumps(SHORT_GOLD_PAYLOAD, ensure_ascii=False), encoding="utf-8"
        )
        self.index = load_audit_index(self.audit_path)

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_short_gold_not_matched_by_substring(self):
        # gold="进程"、"文件"，extracted 只有更长的派生词 —— 旧规则会全命中，新规则应该都 miss
        results = [
            _success_result(
                "s_short",
                [
                    {"id": "e1", "title": "进程控制块", "type": "Concept"},
                    {"id": "e2", "title": "文件系统", "type": "Concept"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.0)

    def test_short_gold_exact_match_still_hits(self):
        results = [
            _success_result(
                "s_short",
                [
                    {"id": "e1", "title": "进程", "type": "Concept"},
                    {"id": "e2", "title": "文件系统", "type": "Concept"},
                ],
                [],
            )
        ]
        # 只有"进程"精确命中，"文件"没对齐
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.5)

    def test_long_gold_keeps_substring_behavior(self):
        # gold="第一章 引论"(len=5)，extracted="第一章 引论 概述"仍走子串命中
        results = [
            _success_result(
                "s_long",
                [{"id": "e1", "title": "第一章 引论 概述", "type": "Chapter"}],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 1.0)


class TestAuditEntityPrecision(unittest.TestCase):
    """compute_audit_entity_precision：extracted 能对齐到 gold 的比例（镜像 recall）。"""

    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.audit_path = Path(self.tmpdir.name) / "audit.json"
        self.audit_path.write_text(
            json.dumps(AUDIT_PAYLOAD, ensure_ascii=False), encoding="utf-8"
        )
        self.index = load_audit_index(self.audit_path)

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_precision_all_aligned(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_precision(results, self.index), 1.0)

    def test_precision_half_aligned(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "无关实体", "type": "Concept"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_precision(results, self.index), 0.5)

    def test_precision_short_gold_symmetric_guard(self):
        # gold 短词 "操作系统"(len=4 恰好不触发守卫)、再造一条短 gold 样本验证对称行为
        payload = {
            "audit_samples": [
                {
                    "source_sample_id": "s_short",
                    "gold_entities": [
                        {"entity_id": "g1", "name": "进程", "type": "Concept"},
                    ],
                    "gold_relations": [],
                }
            ]
        }
        p = Path(self.tmpdir.name) / "audit_short.json"
        p.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        idx = load_audit_index(p)
        # extracted = ["进程控制块"]：短 gold 要求精确相等，所以 extracted 对齐不到 gold
        results = [
            _success_result(
                "s_short",
                [{"id": "e1", "title": "进程控制块", "type": "Concept"}],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_precision(results, idx), 0.0)

    def test_precision_empty_extracted_returns_zero(self):
        results = [_success_result("s1", [], [])]
        self.assertEqual(compute_audit_entity_precision(results, self.index), 0.0)

    def test_precision_missing_sample_excluded(self):
        results = [
            _success_result(
                "s_not_in_audit",
                [{"id": "e1", "title": "随便", "type": "Concept"}],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_precision(results, self.index), 0.0)


class TestAuditRelationAlignment(unittest.TestCase):
    """Step 2: compute_audit_relation_recall 通过实体先对齐（双向子串 + 确定性歧义解）容忍名称漂移。"""

    def _write_audit(self, payload, name="audit.json"):
        path = Path(self.tmpdir.name) / name
        path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        return load_audit_index(path)

    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_relation_hit_via_shorter_extracted_alignment(self):
        """gold tgt '第九章 操作系统接口习题' 漂移成 ext '操作系统接口习题' 时应通过反向子串对齐。"""
        payload = {
            "audit_samples": [{
                "source_sample_id": "s1",
                "gold_entities": [
                    {"entity_id": "g-src", "name": "第九章 操作系统接口", "type": "Chapter"},
                    {"entity_id": "g-tgt", "name": "第九章 操作系统接口习题", "type": "Assignment"},
                ],
                "gold_relations": [
                    {"source_entity_id": "g-src", "target_entity_id": "g-tgt", "type": "contains"},
                ],
            }]
        }
        idx = self._write_audit(payload)
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "第九章 操作系统接口", "type": "Chapter"},
                {"id": "e2", "title": "操作系统接口习题", "type": "Assignment"},
            ],
            [{"source": "第九章 操作系统接口", "target": "操作系统接口习题", "type": "contains"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 1.0)

    def test_relation_hit_via_longer_extracted_alignment(self):
        """gold '操作系统接口' 漂移成 ext '操作系统接口 详解' 时走原有 gold→ext 子串对齐。"""
        payload = {
            "audit_samples": [{
                "source_sample_id": "s1",
                "gold_entities": [
                    {"entity_id": "g1", "name": "第九章 操作系统接口", "type": "Chapter"},
                    {"entity_id": "g2", "name": "操作系统接口", "type": "Section"},
                ],
                "gold_relations": [
                    {"source_entity_id": "g1", "target_entity_id": "g2", "type": "contains"},
                ],
            }]
        }
        idx = self._write_audit(payload)
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "第九章 操作系统接口", "type": "Chapter"},
                {"id": "e2", "title": "操作系统接口 详解", "type": "Section"},
            ],
            [{"source": "第九章 操作系统接口", "target": "操作系统接口 详解", "type": "contains"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 1.0)

    def test_relation_miss_when_gold_entity_has_no_alignment(self):
        """gold 实体找不到对齐时，相关关系不计入命中。"""
        payload = {
            "audit_samples": [{
                "source_sample_id": "s1",
                "gold_entities": [
                    {"entity_id": "g1", "name": "操作系统", "type": "Course"},
                    {"entity_id": "g2", "name": "完全不存在的实体", "type": "Concept"},
                ],
                "gold_relations": [
                    {"source_entity_id": "g1", "target_entity_id": "g2", "type": "contains"},
                ],
            }]
        }
        idx = self._write_audit(payload)
        results = [_success_result(
            "s1",
            [{"id": "e1", "title": "操作系统", "type": "Course"}],
            [],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 0.0)

    def test_alignment_ambiguity_picks_shortest_then_earliest(self):
        """gold 无精确 ext 相等、同时命中多个 ext 子串候选时：按 ext 归一化长度升序，再按下标升序。"""
        payload = {
            "audit_samples": [{
                "source_sample_id": "s1",
                "gold_entities": [
                    {"entity_id": "g1", "name": "第一章", "type": "Chapter"},
                    {"entity_id": "g2", "name": "操作系统接口", "type": "Section"},
                ],
                "gold_relations": [
                    {"source_entity_id": "g1", "target_entity_id": "g2", "type": "contains"},
                ],
            }]
        }
        idx = self._write_audit(payload)
        # g1 "第一章" 走 exact；g2 无精确 ext，ext 有两个 gold→ext 子串候选，长者在前
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "第一章", "type": "Chapter"},
                {"id": "e2", "title": "操作系统接口 概述", "type": "Section"},
                {"id": "e3", "title": "操作系统接口 短", "type": "Section"},
            ],
            # ext relation 的 target 使用"更短"的那条，验证 alignment 真的会挑短者
            [{"source": "第一章", "target": "操作系统接口 短", "type": "contains"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 1.0)

    def test_ambiguity_ranking_follows_ext_order_when_same_length(self):
        """两个 ext 候选归一化长度相同时：按 extracted 列表下标升序（下标小的先赢）。"""
        payload = {
            "audit_samples": [{
                "source_sample_id": "s1",
                "gold_entities": [
                    {"entity_id": "g1", "name": "第一章", "type": "Chapter"},
                    {"entity_id": "g2", "name": "操作系统接口", "type": "Section"},
                ],
                "gold_relations": [
                    {"source_entity_id": "g1", "target_entity_id": "g2", "type": "contains"},
                ],
            }]
        }
        idx = self._write_audit(payload)
        # 两个 ext 候选等长（"操作系统接口 A" 与 "操作系统接口 B" 归一化后都是 7），按下标取第一个
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "第一章", "type": "Chapter"},
                {"id": "e2", "title": "操作系统接口 A", "type": "Section"},
                {"id": "e3", "title": "操作系统接口 B", "type": "Section"},
            ],
            [{"source": "第一章", "target": "操作系统接口 A", "type": "contains"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 1.0)

    def test_short_gold_does_not_align_via_substring(self):
        """gold 归一化长度 < 4 时，对齐守卫仍然生效，派生词不算对齐。"""
        payload = {
            "audit_samples": [{
                "source_sample_id": "s1",
                "gold_entities": [
                    {"entity_id": "g1", "name": "操作系统", "type": "Course"},
                    {"entity_id": "g2", "name": "进程", "type": "Concept"},
                ],
                "gold_relations": [
                    {"source_entity_id": "g1", "target_entity_id": "g2", "type": "contains"},
                ],
            }]
        }
        idx = self._write_audit(payload)
        # "进程" 短，ext 只有 "进程控制块" → 对齐失败 → 关系未命中
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "操作系统", "type": "Course"},
                {"id": "e2", "title": "进程控制块", "type": "Concept"},
            ],
            [{"source": "操作系统", "target": "进程控制块", "type": "contains"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 0.0)

    def test_type_mismatch_still_fails_after_alignment(self):
        """实体能对齐，但关系类型不同仍应 miss。"""
        payload = {
            "audit_samples": [{
                "source_sample_id": "s1",
                "gold_entities": [
                    {"entity_id": "g1", "name": "操作系统", "type": "Course"},
                    {"entity_id": "g2", "name": "第一章 引论", "type": "Chapter"},
                ],
                "gold_relations": [
                    {"source_entity_id": "g1", "target_entity_id": "g2", "type": "contains"},
                ],
            }]
        }
        idx = self._write_audit(payload)
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "操作系统", "type": "Course"},
                {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
            ],
            [{"source": "操作系统", "target": "第一章 引论", "type": "related_to"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 0.0)


class TestCanonicalizeGoldAliases(unittest.TestCase):
    def test_filters_empty_strings_and_preserves_order(self):
        from scoring_audit import canonicalize_gold_aliases
        self.assertEqual(
            canonicalize_gold_aliases(["习题", "", "  ", "IPC"]),
            ("习题", "ipc"),
        )

    def test_normalizes_via_shared_normalizer(self):
        from scoring_audit import canonicalize_gold_aliases
        # 包含半角空格与标点，应被 _normalize_title 清理
        self.assertEqual(
            canonicalize_gold_aliases(["WIMP 技术"]),
            ("wimp技术",),
        )

    def test_empty_input_returns_empty_tuple(self):
        from scoring_audit import canonicalize_gold_aliases
        self.assertEqual(canonicalize_gold_aliases([]), ())


class TestAlignOne(unittest.TestCase):
    def _gold(self, *, name_norm="a", aliases=(), type_="T"):
        from scoring_audit import GoldEntity
        return GoldEntity(gold_id="g", name_norm=name_norm,
                          alias_norms=aliases, type=type_)

    def _cand(self, idx, title_norm, type_):
        from scoring_audit import ExtCandidate
        return ExtCandidate(idx=idx, title_norm=title_norm, type=type_)

    def test_exact_hit_with_matching_type(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", type_="Concept")
        cands = [self._cand(0, "进程", "Concept")]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 0)
        self.assertEqual(r.match_mode, "exact")

    def test_exact_miss_when_type_differs(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", type_="Concept")
        cands = [self._cand(0, "进程", "Term")]
        r = _align_one(gold, cands, claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_alias_hit_when_exact_fails(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="第九章操作系统接口习题",
                          aliases=("习题",), type_="Assignment")
        cands = [self._cand(0, "习题", "Assignment")]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 0)
        self.assertEqual(r.match_mode, "alias")

    def test_alias_requires_type_match(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="x", aliases=("习题",), type_="Assignment")
        cands = [self._cand(0, "习题", "Section")]
        r = _align_one(gold, cands, claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_exact_occupied_swallows_alias_phase(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", aliases=("其它别名",), type_="Concept")
        cands = [
            self._cand(0, "进程", "Concept"),
            self._cand(1, "其它别名", "Concept"),
        ]
        r = _align_one(gold, cands, claimed={0})
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "exact_occupied")

    def test_alias_occupied_when_no_exact_exists(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="没这个", aliases=("习题",), type_="Assignment")
        cands = [self._cand(0, "习题", "Assignment")]
        r = _align_one(gold, cands, claimed={0})
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "alias_occupied")

    def test_candidate_iteration_uses_idx_asc_tie_break(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", type_="Concept")
        cands = [
            self._cand(2, "进程", "Concept"),
            self._cand(1, "进程", "Concept"),
        ]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 1)
        self.assertEqual(r.match_mode, "exact")

    def test_empty_candidates_returns_none(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", type_="Concept")
        r = _align_one(gold, [], claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_empty_alias_list_is_skipped(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="不存在", aliases=(), type_="Concept")
        cands = [self._cand(0, "进程", "Concept")]
        r = _align_one(gold, cands, claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_alias_iteration_follows_input_order(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="x", aliases=("a", "b"), type_="T")
        cands = [
            self._cand(0, "b", "T"),
            self._cand(1, "a", "T"),
        ]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 1)  # "a" 先命中，idx=1
        self.assertEqual(r.match_mode, "alias")


class TestAlignSample(unittest.TestCase):
    def _gold(self, gid, name_norm, *, aliases=(), type_="T"):
        from scoring_audit import GoldEntity
        return GoldEntity(gold_id=gid, name_norm=name_norm,
                          alias_norms=aliases, type=type_)

    def _cand(self, idx, title_norm, type_):
        from scoring_audit import ExtCandidate
        return ExtCandidate(idx=idx, title_norm=title_norm, type=type_)

    def test_gold_iteration_is_gold_id_asc(self):
        """两条 gold 抢同一 ext，gold_id 字典序靠前者赢，另一条得 exact_occupied。"""
        from scoring_audit import align_sample
        g_z = self._gold("z", "进程", type_="Concept")
        g_a = self._gold("a", "进程", type_="Concept")
        cands = [self._cand(0, "进程", "Concept")]
        result = align_sample([g_z, g_a], cands)
        self.assertEqual(result["a"].matched_ext_idx, 0)
        self.assertEqual(result["a"].match_mode, "exact")
        self.assertIsNone(result["z"].matched_ext_idx)
        self.assertEqual(result["z"].match_mode, "exact_occupied")

    def test_one_to_one_claim_across_golds(self):
        from scoring_audit import align_sample
        g1 = self._gold("g1", "a", type_="T")
        g2 = self._gold("g2", "b", type_="T")
        cands = [
            self._cand(0, "a", "T"),
            self._cand(1, "b", "T"),
        ]
        result = align_sample([g1, g2], cands)
        self.assertEqual(result["g1"].matched_ext_idx, 0)
        self.assertEqual(result["g2"].matched_ext_idx, 1)
        self.assertEqual(result["g1"].match_mode, "exact")
        self.assertEqual(result["g2"].match_mode, "exact")

    def test_chapter_vs_assignment_no_cross_type_capture(self):
        """同前缀的 Chapter gold 与 Assignment gold 不应互相抢 ext。"""
        from scoring_audit import align_sample
        g_chap = self._gold("g1", "第九章操作系统接口", type_="Chapter")
        g_asn = self._gold("g2", "第九章操作系统接口习题",
                           aliases=("习题",), type_="Assignment")
        cands = [
            self._cand(0, "第九章操作系统接口", "Chapter"),
        ]
        result = align_sample([g_chap, g_asn], cands)
        self.assertEqual(result["g1"].matched_ext_idx, 0)
        self.assertEqual(result["g1"].match_mode, "exact")
        self.assertIsNone(result["g2"].matched_ext_idx)
        self.assertEqual(result["g2"].match_mode, "none")

    def test_empty_gold_returns_empty_map(self):
        from scoring_audit import align_sample
        cands = [self._cand(0, "a", "T")]
        self.assertEqual(align_sample([], cands), {})

    def test_empty_candidates_all_none(self):
        from scoring_audit import align_sample
        g1 = self._gold("g1", "a")
        g2 = self._gold("g2", "b")
        result = align_sample([g1, g2], [])
        self.assertEqual(result["g1"].match_mode, "none")
        self.assertEqual(result["g2"].match_mode, "none")
        self.assertIsNone(result["g1"].matched_ext_idx)
        self.assertIsNone(result["g2"].matched_ext_idx)


class TestBuildAdapters(unittest.TestCase):
    def test_build_ext_candidates_preserves_order_and_idx(self):
        from scoring_audit import _build_ext_candidates
        result = _success_result(
            "s1",
            [
                {"id": "e1", "title": "操作系统", "type": "Course"},
                {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                {"id": "e3", "title": "  进程  ", "type": "Concept"},
            ],
            [],
        )
        cands = _build_ext_candidates(result)
        self.assertEqual([c.idx for c in cands], [0, 1, 2])
        self.assertEqual([c.title_norm for c in cands],
                         ["操作系统", "第一章引论", "进程"])
        self.assertEqual([c.type for c in cands],
                         ["Course", "Chapter", "Concept"])

    def test_build_ext_candidates_skips_empty_titles(self):
        from scoring_audit import _build_ext_candidates
        result = _success_result(
            "s1",
            [
                {"id": "e1", "title": "", "type": "Course"},
                {"id": "e2", "title": "   ", "type": "Chapter"},
                {"id": "e3", "title": "进程", "type": "Concept"},
            ],
            [],
        )
        cands = _build_ext_candidates(result)
        # 空 title 被跳过，但保留的 idx 应对应原 entities 下标
        self.assertEqual([c.idx for c in cands], [2])
        self.assertEqual(cands[0].title_norm, "进程")

    def test_build_gold_entities_normalizes_and_canonicalizes(self):
        from scoring_audit import _build_gold_entities, AuditEntry
        entry = AuditEntry(
            gold_entities=[
                {"entity_id": "ent-1", "name": "WIMP 技术",
                 "type": "Concept", "alias": ["WIMP", ""]},
                {"entity_id": "ent-2", "name": "第九章 操作系统接口习题",
                 "type": "Assignment", "alias": ["习题"]},
            ],
            gold_relations=[],
        )
        golds = _build_gold_entities(entry)
        self.assertEqual(golds[0].gold_id, "ent-1")
        self.assertEqual(golds[0].name_norm, "wimp技术")
        self.assertEqual(golds[0].alias_norms, ("wimp",))
        self.assertEqual(golds[0].type, "Concept")
        self.assertEqual(golds[1].gold_id, "ent-2")
        self.assertEqual(golds[1].name_norm, "第九章操作系统接口习题")
        self.assertEqual(golds[1].alias_norms, ("习题",))

    def test_build_gold_entities_missing_alias_key(self):
        """部分 audit 条目没有 alias 字段，应视为空别名。"""
        from scoring_audit import _build_gold_entities, AuditEntry
        entry = AuditEntry(
            gold_entities=[
                {"entity_id": "ent-1", "name": "进程", "type": "Concept"},
            ],
            gold_relations=[],
        )
        golds = _build_gold_entities(entry)
        self.assertEqual(golds[0].alias_norms, ())

    def test_build_gold_entities_skips_empty_name(self):
        from scoring_audit import _build_gold_entities, AuditEntry
        entry = AuditEntry(
            gold_entities=[
                {"entity_id": "ent-1", "name": "", "type": "Concept"},
                {"entity_id": "ent-2", "name": "进程", "type": "Concept"},
            ],
            gold_relations=[],
        )
        golds = _build_gold_entities(entry)
        self.assertEqual([g.gold_id for g in golds], ["ent-2"])


if __name__ == "__main__":
    unittest.main()
