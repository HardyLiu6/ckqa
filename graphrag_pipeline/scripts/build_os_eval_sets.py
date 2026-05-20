from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any


DEFAULT_SOURCE = Path("data/eval/操作系统课程问答验证整合习题集_v1.jsonl")
DEFAULT_QA_OUTPUT = Path("data/eval/操作系统课程问答验证整合习题集_v1.jsonl")
DEFAULT_ROUTING_OUTPUT = Path("../backend/ckqa-back/src/test/resources/qa-routing-eval-set.jsonl")

SUMMARY_RE = re.compile(r"(综述|概括|总结|整体|全局|主题|脉络|知识体系|框架|overview)", re.IGNORECASE)
LOCAL_RE = re.compile(r"(第\s*\d+\s*(章|节|讲|页)|章节|教材|课件|课程资料|资料中|原文|公式|例题|图表|算法|步骤|机制|条件|过程|根据|若|某|运行时间|平均等待|响应比|页号|偏移|缺页|磁道|权限|寄存器|逻辑地址|物理地址|访问序列|总磁头|申请多少|属于哪类|破坏了哪个|抑制哪|有效位|脏位|引用位)")
RELATION_RE = re.compile(r"(关系|联系|关联|比较|对比|区别|异同|不同|差异|优缺点|优点|特点|作用|场景|影响|为什么|为何|原因|适合|应用|瓶颈|局限|开销|恶化)")
HYBRID_RE = re.compile(r"(关系|联系|关联|比较|对比|区别|异同|不同|差异|优缺点)")
CALCULATION_RE = re.compile(r"(计算|是多少|响应比|命中率|页号|块号|序列|=|\d+\s*(ms|KB|MB|GB))", re.IGNORECASE)
LISTING_RE = re.compile(r"(列举|哪些|哪几|几种|三种|四类|至少|分类|组成|保存哪些|典型转换)")

KNOWN_TERMS = [
    "操作系统", "资源管理", "用户接口", "用户态", "内核态", "系统调用", "中断", "异常",
    "进程", "线程", "PCB", "上下文切换", "进程通信", "调度", "时间片", "FCFS", "SJF",
    "HRRN", "多级反馈队列", "饥饿", "同步", "互斥", "信号量", "PV", "管程",
    "生产者消费者", "读者写者", "死锁", "银行家算法", "资源分配图", "内存管理",
    "分页", "分段", "页表", "TLB", "虚拟内存", "页面置换", "FIFO", "LRU", "Belady",
    "文件系统", "索引节点", "空闲空间", "I/O", "SPOOLing", "磁盘调度", "RAID",
]


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            payload = json.loads(line)
            required = ("id", "category", "question", "gold_answer_summary") if is_normalized_item(payload) else (
                "id", "topic", "question_type", "difficulty", "question", "reference_answer"
            )
            for key in required:
                if not str(payload.get(key) or "").strip():
                    raise ValueError(f"{path}:{line_no} missing required field {key}")
            rows.append(payload)
    return rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def build_eval_sets(source_path: Path, qa_output_path: Path, routing_output_path: Path) -> dict[str, Any]:
    source_rows = load_jsonl(source_path)
    qa_rows = [normalize_qa_item(index, row) for index, row in enumerate(source_rows, start=1)]
    routing_rows = build_routing_cases(source_rows)
    write_jsonl(qa_output_path, qa_rows)
    write_jsonl(routing_output_path, routing_rows)
    return {
        "source_items": len(source_rows),
        "qa_items": len(qa_rows),
        "routing_items": len(routing_rows),
        "qa_categories": dict(Counter(row["category"] for row in qa_rows)),
        "routing_modes": dict(Counter(row["expectedMode"] for row in routing_rows)),
    }


def normalize_qa_item(index: int, row: dict[str, Any]) -> dict[str, Any]:
    if is_normalized_item(row):
        return {
            "id": clean_text(row["id"]),
            "category": qa_category(row),
            "question": clean_text(row["question"]),
            "gold_answer_summary": clean_text(row["gold_answer_summary"]),
            "gold_entities": normalize_list(row.get("gold_entities")),
            "gold_text_unit_ids": normalize_list(row.get("gold_text_unit_ids")),
            "must_cite_terms": normalize_list(row.get("must_cite_terms")),
            "negative_terms": normalize_list(row.get("negative_terms")),
            "notes": clean_text(row.get("notes")),
        }
    question = clean_text(row["question"])
    topic = source_topic(row)
    question_type = source_question_type(row)
    difficulty = source_difficulty(row)
    source_id = clean_text(row["id"])
    return {
        "id": f"Q{2000 + index:04d}",
        "category": qa_category(row),
        "question": question,
        "gold_answer_summary": clean_text(row["reference_answer"]),
        "gold_entities": extract_terms(topic, question, row["reference_answer"]),
        "gold_text_unit_ids": [],
        "must_cite_terms": extract_terms(topic, question, row["reference_answer"])[:3],
        "negative_terms": [],
        "notes": f"source_id={source_id}; topic={topic}; type={question_type}; difficulty={difficulty}",
    }


def qa_category(row: dict[str, Any]) -> str:
    question = clean_text(row["question"])
    if is_normalized_item(row):
        category = clean_text(row.get("category"))
        if category:
            return category
    question_type = source_question_type(row)
    topic = source_topic(row)
    if SUMMARY_RE.search(question) or question_type == "综合题":
        return "global_overview"
    if LISTING_RE.search(question) and topic not in {"操作系统概述", "操作系统接口"}:
        return "chapter_summary"
    if question_type in {"比较题", "应用题", "计算题", "代码理解题"} or RELATION_RE.search(question):
        return "relation_reasoning"
    return "factual_lookup"


def build_routing_cases(source_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    for row in source_rows:
        source_number = source_suffix(row["id"])
        mode, acceptable = route_label(row)
        cases.append(
            routing_case(
                f"os-{mode_prefix(mode)}-{source_number}",
                clean_text(row["question"]),
                mode,
                acceptable,
                beta=False,
                context=False,
            )
        )
        if should_add_hybrid_variant(row):
            hybrid_question = with_evidence_request(clean_text(row["question"]))
            cases.append(
                routing_case(
                    f"os-hybrid-{source_number}",
                    hybrid_question,
                    "hybrid_v0",
                    ["hybrid_v0"],
                    beta=True,
                    context=False,
                )
            )
            cases.append(
                routing_case(
                    f"os-hybrid-gated-{source_number}",
                    hybrid_question,
                    "local",
                    ["local"],
                    beta=False,
                    context=False,
                )
            )
    for topic_index, topic in enumerate(summary_topics(source_rows), start=1):
        cases.append(
            routing_case(
                f"os-global-topic-{topic_index:03d}",
                f"请总结{topic}这一部分的知识框架。",
                "global",
                ["global"],
                beta=False,
                context=False,
            )
        )
    return cases


def route_label(row: dict[str, Any]) -> tuple[str, list[str]]:
    question = clean_text(row["question"])
    question_type = source_question_type(row)
    if SUMMARY_RE.search(question):
        return "global", ["global", "drift"]
    if question_type == "概念题" and not RELATION_RE.search(question) and not LOCAL_RE.search(question):
        return "basic", ["basic", "local"]
    if question_type in {"比较题", "应用题", "综合题"} or RELATION_RE.search(question):
        return "drift", ["drift", "local", "basic"]
    if question_type == "计算题" or CALCULATION_RE.search(question):
        return "local", ["local", "basic"]
    if LOCAL_RE.search(question) and not RELATION_RE.search(question):
        return "local", ["local", "basic"]
    if question_type == "代码理解题":
        return "local", ["local", "basic", "drift"]
    return "basic", ["basic", "local"]


def should_add_hybrid_variant(row: dict[str, Any]) -> bool:
    question = clean_text(row["question"])
    return HYBRID_RE.search(question) is not None


def summary_topics(source_rows: list[dict[str, Any]], limit: int = 8) -> list[str]:
    counts = Counter(source_topic(row) for row in source_rows)
    blocked = {"综合应用"}
    return [topic for topic, _count in counts.most_common() if topic and topic not in blocked][:limit]


def with_evidence_request(question: str) -> str:
    if re.search(r"(证据|依据|来源|引用|材料)", question):
        return question
    end = "" if question.endswith(("。", "？", "?", "！", "!")) else "。"
    return f"{question}{end}请给出课程材料依据。"


def routing_case(
    item_id: str,
    question: str,
    expected_mode: str,
    acceptable_modes: list[str],
    *,
    beta: bool,
    context: bool,
) -> dict[str, Any]:
    return {
        "id": item_id,
        "question": question,
        "expectedMode": expected_mode,
        "acceptableModes": acceptable_modes,
        "betaHybridEnabled": beta,
        "hasConversationContext": context,
    }


def extract_terms(topic: str, question: str, answer: str) -> list[str]:
    terms: list[str] = []
    push_term(terms, topic)
    text = f"{question} {answer}"
    for term in KNOWN_TERMS:
        if term in text:
            push_term(terms, term)
    for token in re.findall(r"[A-Za-z][A-Za-z0-9_+\-()]*", text):
        if len(token) >= 2:
            push_term(terms, token)
    return terms[:8]


def push_term(terms: list[str], value: str) -> None:
    value = clean_text(value)
    if value and value not in terms:
        terms.append(value)


def mode_prefix(mode: str) -> str:
    return "hybrid" if mode == "hybrid_v0" else mode


def source_suffix(source_id: Any) -> str:
    match = re.search(r"(\d+)$", str(source_id))
    if match:
        return f"{int(match.group(1)):03d}"
    return str(source_id).lower()


def clean_text(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def is_normalized_item(row: dict[str, Any]) -> bool:
    return "gold_answer_summary" in row


def normalize_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [clean_text(item) for item in value if clean_text(item)]


def source_topic(row: dict[str, Any]) -> str:
    return clean_text(row.get("topic")) or note_value(row, "topic")


def source_question_type(row: dict[str, Any]) -> str:
    return clean_text(row.get("question_type")) or note_value(row, "type")


def source_difficulty(row: dict[str, Any]) -> str:
    return clean_text(row.get("difficulty")) or note_value(row, "difficulty")


def note_value(row: dict[str, Any], key: str) -> str:
    notes = clean_text(row.get("notes"))
    for part in notes.split(";"):
        if "=" not in part:
            continue
        name, value = part.split("=", 1)
        if name.strip() == key:
            return clean_text(value)
    return ""


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build normalized OS QA eval and routing eval JSONL files.")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--qa-output", type=Path, default=DEFAULT_QA_OUTPUT)
    parser.add_argument("--routing-output", type=Path, default=DEFAULT_ROUTING_OUTPUT)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    summary = build_eval_sets(args.source, args.qa_output, args.routing_output)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
