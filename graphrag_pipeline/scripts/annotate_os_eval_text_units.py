from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN


DEFAULT_EVAL_PATH = Path("data/eval/操作系统课程问答验证整合习题集_v1.jsonl")
DEFAULT_TEXT_UNITS_PATH = Path("runtime/kb-build-runs/user_0/kb_5/build_19/index/output/text_units.parquet")
DEFAULT_OVERRIDES_PATH = Path("data/eval/os_eval_text_unit_audit_overrides.json")
DEFAULT_REPORT_PATH = Path("results/reports/os_eval_text_unit_annotation_report.json")

GENERIC_TERMS = {
    "操作系统",
    "系统",
    "概念",
    "特点",
    "作用",
    "原因",
    "关系",
    "区别",
    "联系",
}

IMPORTANT_ASCII_TERMS = {
    "CPU",
    "FCFS",
    "FIFO",
    "HRRN",
    "I/O",
    "LOOK",
    "LRU",
    "PCB",
    "PV",
    "RAID",
    "SCAN",
    "SJF",
    "TCB",
    "TLB",
}

KNOWN_TERMS = [
    "操作系统概述",
    "操作系统接口",
    "中断与异常",
    "操作系统结构",
    "批处理与交互",
    "虚拟化思想",
    "操作系统性能",
    "资源管理",
    "用户接口",
    "用户态",
    "核心态",
    "内核态",
    "系统调用",
    "中断",
    "异常",
    "并发",
    "共享",
    "虚拟",
    "异步",
    "微内核",
    "模块化",
    "吞吐量",
    "响应时间",
    "等待时间",
    "周转时间",
    "进程",
    "线程",
    "PCB",
    "TCB",
    "上下文切换",
    "进程通信",
    "管道",
    "消息队列",
    "共享内存",
    "信号量",
    "调度",
    "FCFS",
    "SJF",
    "HRRN",
    "时间片",
    "多级反馈队列",
    "优先级",
    "饥饿",
    "同步",
    "互斥",
    "临界区",
    "PV",
    "P 操作",
    "V 操作",
    "管程",
    "生产者消费者",
    "读者写者",
    "哲学家进餐",
    "死锁",
    "银行家算法",
    "资源分配图",
    "安全序列",
    "内存管理",
    "连续分配",
    "分页",
    "分页机制",
    "分段",
    "段页式",
    "页表",
    "页号",
    "页内偏移",
    "页面大小",
    "地址结构",
    "逻辑地址",
    "物理地址",
    "非法越界访问",
    "合法地址",
    "地址越界",
    "越界",
    "地址变换",
    "快表",
    "TLB",
    "虚拟内存",
    "请求分页",
    "缺页中断",
    "页面置换",
    "FIFO",
    "LRU",
    "Belady",
    "缺页",
    "抖动",
    "工作集",
    "文件系统",
    "文件物理结构",
    "连续组织方式",
    "链接组织方式",
    "索引组织方式",
    "索引分配",
    "随机访问",
    "文件权限",
    "文件保护",
    "访问权限",
    "索引节点",
    "目录",
    "空闲空间",
    "位示图",
    "成组链接",
    "I/O",
    "I/O 设备",
    "块设备",
    "字符设备",
    "设备控制器",
    "设备无关",
    "设备独立性",
    "I/O 软件",
    "I/O软件",
    "SPOOLing",
    "磁盘调度",
    "SCAN",
    "LOOK",
    "RAID",
]

TOPIC_ALIASES = {
    "操作系统概述": ["操作系统", "资源管理", "用户接口", "并发", "共享", "虚拟", "异步"],
    "操作系统接口": ["系统调用", "用户接口", "程序接口", "命令接口", "用户态", "核心态", "内核态"],
    "中断与异常": ["中断", "异常", "陷入", "系统调用", "外中断", "内中断"],
    "操作系统结构": ["操作系统结构", "模块", "模块化", "层次结构", "微内核", "客户服务器"],
    "批处理与交互": ["批处理", "分时", "实时", "交互", "吞吐量", "响应时间"],
    "虚拟化思想": ["虚拟", "虚拟性", "虚拟机", "虚拟存储器", "CPU 虚拟化", "内存虚拟化"],
    "操作系统性能": ["吞吐量", "响应时间", "周转时间", "利用率", "调度", "性能"],
    "进程与线程": ["进程", "线程", "PCB", "TCB", "上下文切换", "资源分配", "调度"],
    "上下文切换": ["上下文切换", "进程切换", "处理机状态", "保存现场", "恢复现场", "寄存器", "程序计数器"],
    "进程通信": ["进程通信", "管道", "消息队列", "共享存储", "共享内存", "信箱"],
    "进程调度": ["处理机调度", "进程调度", "调度算法", "FCFS", "SJF", "HRRN", "时间片", "多级反馈队列"],
    "调度指标": ["周转时间", "等待时间", "响应时间", "调度算法", "处理机调度"],
    "同步与互斥": ["同步", "互斥", "临界区", "信号量", "PV", "管程", "生产者消费者", "读者写者"],
    "同步互斥": ["进程同步", "同步", "互斥", "临界资源", "临界区", "信号量", "PV", "管程"],
    "死锁": ["死锁", "银行家算法", "资源分配图", "安全序列", "预防死锁", "避免死锁", "检测死锁"],
    "内存管理": ["内存管理", "连续分配", "分页", "分段", "段页式", "页表", "快表", "TLB"],
    "分页机制": ["分页", "页表", "页号", "页内偏移", "页面大小", "地址结构", "逻辑地址", "物理地址", "地址变换"],
    "综合应用": ["缺页", "缺页中断", "请求分页", "地址越界", "越界", "地址变换", "异常"],
    "虚拟内存": ["虚拟内存", "请求分页", "页面置换", "FIFO", "LRU", "Belady", "缺页", "抖动", "工作集"],
    "文件系统": ["文件系统", "目录", "索引节点", "文件分配", "文件物理结构", "连续组织方式", "链接组织方式", "索引组织方式", "空闲空间", "位示图", "成组链接"],
    "文件权限": ["文件保护", "访问权限", "权限", "存取控制", "口令", "访问控制表"],
    "I/O与磁盘": ["I/O", "缓冲", "设备驱动", "SPOOLing", "磁盘调度", "SCAN", "LOOK", "RAID"],
    "I/O 与设备": ["I/O", "I/O 设备", "块设备", "字符设备", "设备控制器", "设备无关", "设备驱动"],
    "I/O 软件层次": ["I/O 软件", "I/O软件", "设备独立性", "设备无关", "设备驱动程序", "I/O系统", "输入输出系统"],
}

TERM_ALIASES = {
    "内核态": ["核心态", "管态"],
    "用户态": ["目态"],
    "虚拟化": ["虚拟", "虚拟性"],
    "OSTEP": ["虚拟", "并发", "持久性"],
    "进程间通信": ["进程通信", "IPC"],
    "PV操作": ["PV", "P 操作", "V 操作", "信号量"],
    "读者写者": ["读者-写者", "读者和写者"],
    "生产者消费者": ["生产者-消费者", "生产者和消费者"],
    "页表缓存": ["快表", "TLB"],
    "页号": ["页表", "分页", "地址变换"],
    "偏移": ["页内地址", "地址变换"],
    "文件权限": ["文件保护", "访问权限", "权限"],
    "块设备": ["I/O 设备", "设备控制器"],
    "字符设备": ["I/O 设备", "设备控制器"],
    "设备独立性软件": ["设备独立性", "设备无关", "I/O软件"],
    "连续分配": ["连续组织方式", "文件物理结构"],
    "链式分配": ["链接组织方式", "文件物理结构"],
    "索引分配": ["索引组织方式", "文件物理结构"],
    "非法越界访问": ["地址越界", "越界", "地址保护"],
    "合法地址": ["缺页", "缺页中断", "请求分页"],
    "RAID0": ["RAID", "条带化"],
    "RAID1": ["RAID", "镜像"],
    "RAID5": ["RAID", "奇偶校验"],
}


@dataclass(frozen=True, slots=True)
class WeightedTerm:
    term: str
    weight: float
    source: str


@dataclass(frozen=True, slots=True)
class TextUnit:
    prefix: str
    human_readable_id: str
    text: str
    heading_path: str
    normalized_text: str
    normalized_heading: str


@dataclass(frozen=True, slots=True)
class RankedCandidate:
    prefix: str
    human_readable_id: str
    score: float
    matched_terms: tuple[str, ...]
    heading_path: str
    snippet: str


def annotate_eval_text_units(
    *,
    eval_path: Path,
    text_units_path: Path,
    output_path: Path,
    report_path: Path,
    overrides_path: Path | None = None,
    max_units: int = 3,
    min_score: float = 10.0,
    overwrite: bool = False,
) -> dict[str, Any]:
    rows = read_jsonl(eval_path)
    text_units = load_text_units(text_units_path)
    overrides = load_overrides(overrides_path) if overrides_path else {}
    annotated = 0
    existing = 0
    manual = 0
    empty = 0
    report_items: list[dict[str, Any]] = []

    output_rows: list[dict[str, Any]] = []
    for row in rows:
        current_ids = normalize_prefixes(row.get("gold_text_unit_ids"))
        candidates = rank_text_units(row, text_units)
        item_id = str(row.get("id") or "")
        if item_id in overrides:
            selected = overrides[item_id]
            manual += 1
            action = "manual_audit_override"
        elif current_ids and not overwrite:
            selected = current_ids
            existing += 1
            action = "kept_existing"
        else:
            selected = [
                candidate.prefix
                for candidate in candidates[:max_units]
                if candidate.score >= min_score
            ]
            if selected:
                annotated += 1
                action = "annotated"
            else:
                empty += 1
                action = "left_empty"

        updated = dict(row)
        updated["gold_text_unit_ids"] = selected
        output_rows.append(updated)
        report_items.append(
            {
                "id": row.get("id"),
                "question": row.get("question"),
                "topic": parse_notes(row.get("notes")).get("topic"),
                "action": action,
                "selected": selected,
                "candidates": [candidate_to_report(candidate) for candidate in candidates[:5]],
            }
        )

    write_jsonl(output_path, output_rows)
    report = {
        "method": "lexical_human_review_assisted_v1",
        "text_units_path": str(text_units_path),
        "total_items": len(rows),
        "annotated_items": annotated,
        "existing_items": existing,
        "manual_audit_items": manual,
        "empty_items": empty,
        "coverage": {
            "non_empty": sum(1 for row in output_rows if row["gold_text_unit_ids"]),
            "by_category": dict(
                Counter(
                    row.get("category", "unknown")
                    for row in output_rows
                    if row.get("gold_text_unit_ids")
                )
            ),
        },
        "items": report_items,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def load_overrides(path: Path | None) -> dict[str, list[str]]:
    if path is None or not path.exists():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain a JSON object")
    overrides: dict[str, list[str]] = {}
    for raw_id, raw_value in payload.items():
        if isinstance(raw_value, dict):
            prefixes = normalize_prefixes(raw_value.get("gold_text_unit_ids"))
        else:
            prefixes = normalize_prefixes(raw_value)
        if prefixes:
            overrides[str(raw_id)] = prefixes
    return overrides


def rank_text_units(row: dict[str, Any], text_units: list[TextUnit]) -> list[RankedCandidate]:
    terms = build_weighted_terms(row)
    candidates: list[RankedCandidate] = []
    for unit in text_units:
        score, matched_terms = score_text_unit(unit, terms)
        if score <= 0:
            continue
        candidates.append(
            RankedCandidate(
                prefix=unit.prefix,
                human_readable_id=unit.human_readable_id,
                score=round(score, 3),
                matched_terms=tuple(matched_terms),
                heading_path=unit.heading_path,
                snippet=make_snippet(unit.text, matched_terms),
            )
        )
    return sorted(candidates, key=lambda item: (-item.score, item.human_readable_id, item.prefix))


def build_weighted_terms(row: dict[str, Any]) -> list[WeightedTerm]:
    seen: set[str] = set()
    terms: list[WeightedTerm] = []

    def add(raw_term: str, weight: float, source: str) -> None:
        term = clean_text(raw_term)
        if len(term) < 2:
            return
        if term.isascii() and len(term) < 4 and term.upper() not in IMPORTANT_ASCII_TERMS:
            return
        key = normalize_text(term)
        if not key or key in seen:
            return
        seen.add(key)
        adjusted = weight
        if term in GENERIC_TERMS:
            adjusted *= 0.45
        terms.append(WeightedTerm(term=term, weight=adjusted, source=source))
        for alias in TERM_ALIASES.get(term, []):
            add(alias, max(2.0, adjusted * 0.75), f"{source}:alias")

    notes = parse_notes(row.get("notes"))
    topic = notes.get("topic")
    if topic:
        add(topic, 8.0, "topic")
        for alias in TOPIC_ALIASES.get(topic, []):
            add(alias, 6.0, "topic_alias")

    for entity in row.get("gold_entities") or []:
        add(str(entity), 10.0, "gold_entity")
    for term in row.get("must_cite_terms") or []:
        add(str(term), 8.0, "must_cite")

    question = clean_text(row.get("question"))
    answer = clean_text(row.get("gold_answer_summary"))
    combined = f"{question} {answer}"
    for known in KNOWN_TERMS:
        if contains_text(combined, known):
            add(known, 5.0, "known_term")
    for ascii_token in re.findall(r"[A-Za-z][A-Za-z0-9_+/-]{1,}", combined):
        add(ascii_token, 4.0, "ascii")

    return sorted(terms, key=lambda item: (-item.weight, -len(item.term), item.term))


def score_text_unit(unit: TextUnit, terms: list[WeightedTerm]) -> tuple[float, list[str]]:
    score = 0.0
    matched: list[str] = []
    for weighted in terms:
        normalized_term = normalize_text(weighted.term)
        if not normalized_term:
            continue
        in_body = normalized_term in unit.normalized_text
        in_heading = normalized_term in unit.normalized_heading
        if not in_body and not in_heading:
            continue
        contribution = weighted.weight
        if in_heading:
            contribution *= 2.4
        elif unit.normalized_text.find(normalized_term) <= 600:
            contribution *= 1.25
        contribution += min(len(weighted.term), 8) * 0.25
        score += contribution
        matched.append(weighted.term)

    if is_front_matter(unit) and not any(normalize_text(term) in unit.normalized_heading for term in matched):
        score *= 0.35
    if len(set(matched)) >= 3:
        score *= 1.15
    return score, sorted(set(matched), key=lambda term: (-len(term), term))


def load_text_units(path: Path) -> list[TextUnit]:
    frame = pd.read_parquet(path, columns=["id", "human_readable_id", "text"])
    text_units: list[TextUnit] = []
    for raw_id, human_readable_id, raw_text in zip(
        frame["id"],
        frame["human_readable_id"],
        frame["text"],
    ):
        text = str(raw_text or "")
        prefix = str(raw_id)[:TEXT_UNIT_ID_PREFIX_LEN]
        if not prefix or not text.strip():
            continue
        heading_path = metadata_value(text, "heading_path_text") or metadata_value(text, "chapter") or ""
        text_units.append(
            TextUnit(
                prefix=prefix,
                human_readable_id=str(human_readable_id),
                text=text,
                heading_path=heading_path,
                normalized_text=normalize_text(text),
                normalized_heading=normalize_text(heading_path),
            )
        )
    return text_units


def candidate_to_report(candidate: RankedCandidate) -> dict[str, Any]:
    return {
        "prefix": candidate.prefix,
        "human_readable_id": candidate.human_readable_id,
        "score": candidate.score,
        "matched_terms": list(candidate.matched_terms),
        "heading_path": candidate.heading_path,
        "snippet": candidate.snippet,
    }


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            stripped = line.strip()
            if stripped:
                rows.append(json.loads(stripped))
    return rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def normalize_prefixes(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    prefixes: list[str] = []
    for raw in value:
        text = str(raw or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
        if text and text not in prefixes:
            prefixes.append(text)
    return prefixes


def parse_notes(value: object) -> dict[str, str]:
    notes = str(value or "")
    parsed: dict[str, str] = {}
    for part in notes.split(";"):
        if "=" not in part:
            continue
        key, raw_value = part.split("=", 1)
        parsed[key.strip()] = raw_value.strip()
    return parsed


def metadata_value(text: str, key: str) -> str | None:
    pattern = re.compile(rf"^{re.escape(key)}:\s*(.+?)\.\s*$", re.MULTILINE)
    match = pattern.search(text)
    if match:
        return match.group(1).strip()
    return None


def make_snippet(text: str, matched_terms: list[str], *, radius: int = 70) -> str:
    compact = re.sub(r"\s+", " ", text).strip()
    if not compact:
        return ""
    normalized = normalize_text(compact)
    best_index = -1
    for term in matched_terms:
        best_index = normalized.find(normalize_text(term))
        if best_index >= 0:
            break
    if best_index < 0:
        return compact[: radius * 2]
    start = max(0, best_index - radius)
    end = min(len(compact), best_index + radius)
    prefix = "..." if start > 0 else ""
    suffix = "..." if end < len(compact) else ""
    return f"{prefix}{compact[start:end]}{suffix}"


def is_front_matter(unit: TextUnit) -> bool:
    heading = unit.normalized_heading
    return "前言" in heading or "目录" in heading or "封面" in heading


def contains_text(text: str, needle: str) -> bool:
    return normalize_text(needle) in normalize_text(text)


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", "", str(text or "").lower())


def clean_text(value: object) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="为操作系统 QA 评测集补充 text unit 级 gold 标注。")
    parser.add_argument("--eval-file", type=Path, default=DEFAULT_EVAL_PATH)
    parser.add_argument("--text-units", type=Path, default=DEFAULT_TEXT_UNITS_PATH)
    parser.add_argument("--overrides", type=Path, default=DEFAULT_OVERRIDES_PATH)
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT_PATH)
    parser.add_argument("--max-units", type=int, default=3)
    parser.add_argument("--min-score", type=float, default=10.0)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args(argv)

    output_path = args.output or args.eval_file
    summary = annotate_eval_text_units(
        eval_path=args.eval_file,
        text_units_path=args.text_units,
        output_path=output_path,
        report_path=args.report,
        overrides_path=args.overrides,
        max_units=args.max_units,
        min_score=args.min_score,
        overwrite=args.overwrite,
    )
    print(json.dumps({key: value for key, value in summary.items() if key != "items"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
