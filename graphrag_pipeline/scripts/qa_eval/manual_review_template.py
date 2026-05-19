from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import score_baseline_run
from graphrag_pipeline.scripts.qa_eval.judge_scorer import JUDGE_METRICS


LOGGER = logging.getLogger(__name__)
_HYPOTHESIS_RE = re.compile(r"^\s*[-*]\s*(H\d+)[:：]?\s*(.*)$")


def write_manual_review_template(
    run_dir: Path | str,
    *,
    hypotheses_path: Path | None = None,
) -> Path:
    run_path = Path(run_dir)
    raw_items = _load_raw_items(run_path / "raw")
    hypotheses = _extract_hypotheses(hypotheses_path)

    lines: list[str] = [
        f"# 人工复核 - {run_path.name}",
        "",
        "## 评分标准",
        "",
        "- `答对?`：题面要求的核心事实是否完整给出（是 / 部分 / 否）。",
        "- `有依据?`：答案是否引用了 `[Data: ...]` 或可追溯的章节/段落。",
        "- `命中正确内容?`：依据指向的内容是否真的支持该结论。",
        "- `幻觉?`：是否出现原文中不存在的实体、章节号、年份、人名。",
        "- `冗长?`：是否大段铺垫无关内容（参考 category_thresholds.py 的题型期望区间）。",
        "",
        "## 总结",
        "",
        "### 假设验证",
        "",
    ]
    if hypotheses:
        for hypothesis_id, body in hypotheses:
            lines.append(f"- {hypothesis_id}：[ 验证 / 部分验证 / 不支持 ]，数据依据：__")
            lines.append(f"  原假设：{body}")
    else:
        lines.append("- （未找到 `hypotheses.md`，请先在 Task 9 撰写假设）")

    lines += [
        "",
        "### hybrid 路由建议",
        "",
        "- factual_lookup -> __，依据：entity_hit_rate=__, semantic_correctness=__",
        "- relation_reasoning -> __，依据：entity_hit_rate=__, semantic_correctness=__",
        "- chapter_summary -> __，依据：semantic_correctness=__, length_score=__",
        "- global_overview -> __，依据：semantic_correctness=__, faithfulness=__",
        "",
        "### 规则 vs 语义一致性核对",
        "",
        "- 是否存在「规则高分但语义错误」的典型案例？(是 / 否)：__",
        "- 若是，列出 question_id 与模式：__",
        "",
        "## 指标均值",
        "",
    ]
    lines += _render_summary_means(run_path)
    lines += ["", "## 单题复核", ""]

    for item in raw_items:
        lines += _render_item(item)

    out = run_path / "manual_review.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out


def _load_raw_items(raw_dir: Path) -> list[dict]:
    items = [json.loads(path.read_text(encoding="utf-8")) for path in sorted(raw_dir.glob("Q*.json"))]
    if not items:
        raise FileNotFoundError(f"no raw items in {raw_dir}")
    return items


def _extract_hypotheses(path: Path | None) -> list[tuple[str, str]]:
    if path is None or not path.exists():
        return []
    hypotheses: list[tuple[str, str]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = _HYPOTHESIS_RE.match(line)
        if match:
            hypotheses.append((match.group(1), match.group(2).strip()))
    return hypotheses


def _render_summary_means(run_dir: Path) -> list[str]:
    summary = score_baseline_run(run_dir)
    judge_payload = _load_optional_json(run_dir / "judge_scoring.json")

    lines: list[str] = ["### 规则层按题型 x 模式（自动注入）", ""]
    for category, modes_dict in summary.per_category_mode.items():
        lines.append(f"- {category}")
        for mode in summary.modes:
            row = modes_dict[mode]
            lines.append(
                f"  - {mode}: entity_hit_rate={row['entity_hit_rate']:.3f}, "
                f"must_cite_hit={row['must_cite_hit']:.3f}, "
                f"length_score={row['length_score']:.3f}, "
                f"info_density={row['info_density']:.3f}"
            )

    if judge_payload:
        lines += ["", "### 裁判层按题型 x 模式（自动注入）", ""]
        for category, modes_dict in judge_payload.get("per_category_mode", {}).items():
            lines.append(f"- {category}")
            for mode in summary.modes:
                row = modes_dict.get(mode, {})
                metrics = ", ".join(f"{metric}={row.get(metric, 0.0):.3f}" for metric in JUDGE_METRICS)
                lines.append(f"  - {mode}: {metrics}")
    return lines


def _render_item(item: dict) -> list[str]:
    lines = [
        f"### {item['id']} [{item['category']}]",
        "",
        f"**问题**：{item['question']}",
        "",
        f"**参考答案**：{item['gold_answer_summary']}",
        "",
    ]
    for mode, payload in item["modes"].items():
        lines.append(f"#### {mode}")
        if "error" in payload:
            lines += [f"- 调用失败：`{payload['error']}`", ""]
            continue
        lines += [
            "```text",
            str(payload.get("answer", "")),
            "```",
            "",
            "- 答对? (是 / 部分 / 否)：",
            "- 有依据? (是 / 否)：",
            "- 命中正确内容? (是 / 否)：",
            "- 幻觉? (是 / 否，若是请贴出错点)：",
            "- 冗长? (是 / 否)：",
            "- 备注：",
            "",
        ]
    return lines


def _load_optional_json(path: Path) -> dict | None:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Write a manual review template for a QA baseline run.")
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument(
        "--hypotheses-path",
        type=Path,
        default=Path("graphrag_pipeline/results/qa_eval/hypotheses.md"),
    )
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    write_manual_review_template(args.run_dir, hypotheses_path=args.hypotheses_path)
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
