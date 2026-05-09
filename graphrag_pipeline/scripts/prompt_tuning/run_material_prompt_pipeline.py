#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
material 级 Prompt 调优流水线编排
================================

把单份课程材料的 prompt tuning 实验串成可复现的最小闭环：
fetch -> sample -> audit -> candidate -> prompt-tune -> extract -> score。

默认只执行 smoke 链路，每个候选抽 1 条样本。完整 audit 集、Prompt 固化和索引
都需要显式参数，避免调试时误改当前活动 Prompt。
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Sequence


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SAMPLES_DIR = Path("data") / "prompt_tuning_samples"
DEFAULT_EVAL_DIR = Path("data") / "eval"
DEFAULT_CANDIDATE_MANIFEST = Path("prompts") / "candidates" / "manifest.json"
DEFAULT_QA_SMOKE_DIR = Path("data") / "eval"
DEFAULT_PROMPT_TUNE_DOMAIN = "计算机操作系统课程教材知识图谱抽取"
DEFAULT_PROMPT_TUNE_LANGUAGE = "中文"
EXPECTED_PYTHON_ENV_NAME = "graphrag-oneapi"

Runner = Callable[[Sequence[str], Path], subprocess.CompletedProcess[str]]


@dataclass(frozen=True)
class PipelineStep:
    name: str
    command: list[str]
    cwd: str


def _as_posix(path: str | Path) -> str:
    return Path(path).as_posix()


def _material_slug(material_id: int) -> str:
    return f"material_{material_id}"


def _smoke_run_id(run_id: str) -> str:
    return run_id if run_id.endswith("_smoke") else f"{run_id}_smoke"


def _python_env_warning(python_executable: str) -> str | None:
    if EXPECTED_PYTHON_ENV_NAME in python_executable:
        return None
    return (
        f"当前 Python 可执行文件路径不包含 {EXPECTED_PYTHON_ENV_NAME}，"
        "请确认是否使用 GraphRAG 调优推荐环境。"
    )


def _default_runner(command: Sequence[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(command),
        cwd=str(cwd),
        text=True,
        capture_output=True,
        check=False,
    )


def _step(name: str, command: Sequence[str], root: Path) -> PipelineStep:
    return PipelineStep(name=name, command=list(command), cwd=str(root))


def build_material_prompt_pipeline_steps(
    *,
    root: Path = PROJECT_ROOT,
    course_id: str,
    material_id: int,
    json_file: str,
    run_id: str,
    mode: str = "smoke",
    prompt_tune_mode: str = "dry-run",
    python_executable: str | None = None,
    max_samples: int = 80,
    audit_sample_size: int = 20,
    smoke_limit: int = 1,
    full_limit: int = 20,
    concurrency: int = 1,
    sample_timeout_seconds: float = 300,
    stream_mode: str = "on",
    candidate_view: str = "compact",
    prompt_tune_no_entity_types: bool = False,
    prompt_tune_domain: str = DEFAULT_PROMPT_TUNE_DOMAIN,
    prompt_tune_language: str = DEFAULT_PROMPT_TUNE_LANGUAGE,
    overwrite: bool = True,
) -> list[PipelineStep]:
    """构建 material 级 prompt tuning 流水线步骤。"""

    project_root = root.resolve()
    python = python_executable or sys.executable
    material_slug = _material_slug(material_id)
    samples_file = DEFAULT_SAMPLES_DIR / f"{material_slug}_samples.json"
    audit_file = DEFAULT_EVAL_DIR / f"{material_slug}_audit_extraction_set.json"
    prompt_tune_report = Path("results") / "reports" / f"{material_slug}_prompt_tune_report.json"
    prompt_generation_report = Path("results") / "reports" / f"{material_slug}_prompt_generation_report.json"
    audit_report = Path("results") / "reports" / f"{material_slug}_audit_sampling_report.json"

    extract_base = [
        python,
        "scripts/run_candidate_extraction.py",
        "--manifest",
        _as_posix(DEFAULT_CANDIDATE_MANIFEST),
        "--concurrency",
        str(concurrency),
        "--sample-timeout",
        f"{sample_timeout_seconds:g}",
        "--stream-mode",
        stream_mode,
        "--candidate-view",
        candidate_view,
    ]
    if overwrite:
        extract_base.append("--overwrite")

    setup_steps = [
        _step(
            "fetch_input",
            [
                python,
                "utils/fetch_from_minio.py",
                course_id,
                "--material-id",
                str(material_id),
                "--json-file",
                json_file,
                "--clean",
            ],
            project_root,
        ),
        _step(
            "build_samples",
            [
                python,
                "scripts/build_prompt_tuning_samples.py",
                "--input_dir",
                "./input",
                "--output_file",
                _as_posix(samples_file),
                "--max_samples",
                str(max_samples),
            ],
            project_root,
        ),
        _step(
            "build_audit",
            [
                python,
                "scripts/build_audit_extraction_set.py",
                "--input_file",
                _as_posix(samples_file),
                "--output_file",
                _as_posix(audit_file),
                "--report_file",
                _as_posix(audit_report),
                "--sample_size",
                str(audit_sample_size),
            ],
            project_root,
        ),
    ]

    prompt_tune_steps: list[PipelineStep] = []
    if prompt_tune_mode == "dry-run":
        prompt_tune_command = [
            python,
            "scripts/run_graphrag_prompt_tune.py",
            "--root",
            ".",
            "--report_file",
            _as_posix(prompt_tune_report),
            "--run-id",
            run_id,
            "--domain",
            prompt_tune_domain,
            "--language",
            prompt_tune_language,
            "--dry_run",
            "--overwrite",
        ]
        if prompt_tune_no_entity_types:
            prompt_tune_command.append("--no_entity_types")
        prompt_tune_steps.append(
            _step(
                "prompt_tune_dry_run",
                prompt_tune_command,
                project_root,
            )
        )
    elif prompt_tune_mode == "real":
        prompt_tune_command = [
            python,
            "scripts/run_graphrag_prompt_tune.py",
            "--root",
            ".",
            "--report_file",
            _as_posix(prompt_tune_report),
            "--run-id",
            run_id,
            "--domain",
            prompt_tune_domain,
            "--language",
            prompt_tune_language,
            "--overwrite",
        ]
        if prompt_tune_no_entity_types:
            prompt_tune_command.append("--no_entity_types")
        prompt_tune_steps.append(
            _step(
                "prompt_tune_real_run",
                prompt_tune_command,
                project_root,
            )
        )

    generate_steps = [
        _step(
            "generate_candidates",
            [
                python,
                "scripts/generate_candidate_prompts.py",
                "--samples_file",
                _as_posix(samples_file),
                "--audit_file",
                _as_posix(audit_file),
                "--report_file",
                _as_posix(prompt_generation_report),
                "--overwrite",
            ],
            project_root,
        )
    ]

    smoke_run_id = _smoke_run_id(run_id)
    smoke_steps = [
        _step(
            "extract_smoke",
            [
                *extract_base,
                "--samples",
                _as_posix(samples_file),
                "--run-id",
                smoke_run_id,
                "--limit",
                str(smoke_limit),
            ],
            project_root,
        ),
        _step(
            "score_smoke",
            [
                python,
                "scripts/score_extraction_results.py",
                "--eval-dir",
                f"./results/extraction_eval/runs/{smoke_run_id}",
                "--audit",
                _as_posix(audit_file),
                "--run-id",
                smoke_run_id,
                "--overwrite",
            ],
            project_root,
        ),
    ]

    steps = setup_steps + prompt_tune_steps + generate_steps + smoke_steps

    if mode == "full":
        steps.extend(
            [
                _step(
                    "extract_full",
                    [
                        *extract_base,
                        "--samples",
                        _as_posix(audit_file),
                        "--run-id",
                        run_id,
                        "--limit",
                        str(full_limit),
                    ],
                    project_root,
                ),
                _step(
                    "score_full",
                    [
                        python,
                        "scripts/score_extraction_results.py",
                        "--eval-dir",
                        f"./results/extraction_eval/runs/{run_id}",
                        "--audit",
                        _as_posix(audit_file),
                        "--run-id",
                        run_id,
                        "--overwrite",
                    ],
                    project_root,
                ),
            ]
        )

    return steps


def _load_top_candidate_for_finalization(
    *,
    top_candidates_path: Path,
    min_parse_success_rate: float,
    require_gate_passed: bool = True,
    require_artifact_binding: bool = False,
) -> tuple[str, dict[str, Any]]:
    payload = json.loads(top_candidates_path.read_text(encoding="utf-8"))
    inputs = payload.get("inputs") if isinstance(payload.get("inputs"), dict) else {}
    if require_gate_passed and inputs.get("gold_seed_coverage_passed") is False:
        missing = inputs.get("gold_seed_missing_relation_types") or []
        raise RuntimeError(
            "manual gold seed 覆盖未达标，拒绝固化："
            f"missing_relation_types={missing}"
        )
    top_candidates = payload.get("top_candidates")
    if not isinstance(top_candidates, list) or not top_candidates:
        raise RuntimeError(f"top-k 报告没有可固化候选：{top_candidates_path}")

    best = top_candidates[0]
    if not isinstance(best, dict):
        raise RuntimeError(f"top-k 报告结构异常：{top_candidates_path}")

    candidate = str(best.get("candidate") or "").strip()
    if not candidate:
        raise RuntimeError(f"top-k 报告缺少 candidate 字段：{top_candidates_path}")

    parse_success_rate = float(best.get("parse_success_rate") or 0.0)
    gate_passed = bool(best.get("gate_passed"))
    if parse_success_rate < min_parse_success_rate or (require_gate_passed and not gate_passed):
        raise RuntimeError(
            "最佳候选未达固化门槛："
            f"candidate={candidate}, parse_success_rate={parse_success_rate:.3f}, "
            f"gate_passed={gate_passed}"
        )
    if require_gate_passed or require_artifact_binding:
        binding = best.get("artifact_binding")
        if not isinstance(binding, dict):
            raise RuntimeError(f"top-k 报告缺少候选 artifact_binding：{top_candidates_path}")
        if not binding.get("manifest_sha256") or not binding.get("scoring_result_sha256"):
            raise RuntimeError(f"top-k artifact_binding 缺少必要 hash：{top_candidates_path}")
    return candidate, best


def _parse_iso_timestamp(value: str) -> float | None:
    if not value:
        return None
    try:
        normalized = value.replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.timestamp()
    except ValueError:
        return None


def _validate_real_prompt_tune_report(*, root: Path, report_path: Path) -> None:
    if not report_path.exists():
        raise RuntimeError(f"real prompt-tune 未写出报告：{report_path}")
    payload = json.loads(report_path.read_text(encoding="utf-8"))
    if payload.get("status") != "success":
        raise RuntimeError(f"real prompt-tune 未成功：status={payload.get('status')}")

    primary_prompt = payload.get("primary_prompt_path")
    if not primary_prompt:
        primary_name = str(payload.get("primary_prompt_file") or "prompt.txt")
        primary_prompt = root / "prompts" / "candidates" / "auto_tuned" / primary_name
    primary_path = Path(primary_prompt)
    if not primary_path.is_absolute():
        primary_path = (root / primary_path).resolve()
    if not primary_path.exists():
        raise RuntimeError(f"real prompt-tune 主 Prompt 文件不存在：{primary_path}")

    start_ts = _parse_iso_timestamp(str(payload.get("start_time") or payload.get("generated_at") or ""))
    if start_ts is not None and primary_path.stat().st_mtime < start_ts:
        raise RuntimeError(
            "real prompt-tune 主 Prompt 文件早于本次执行开始时间，疑似复用旧产物："
            f"prompt={primary_path}"
        )

    resolved_paths = payload.get("resolved_paths") if isinstance(payload.get("resolved_paths"), dict) else {}
    manifest_path = Path(resolved_paths.get("manifest_file") or DEFAULT_CANDIDATE_MANIFEST)
    if not manifest_path.is_absolute():
        manifest_path = (root / manifest_path).resolve()
    if not manifest_path.exists():
        raise RuntimeError(f"real prompt-tune 后 manifest 不存在：{manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    candidates = manifest.get("candidates") if isinstance(manifest.get("candidates"), list) else []
    auto_tuned = next(
        (
            item
            for item in candidates
            if isinstance(item, dict)
            and str(item.get("candidate_name") or "").strip() == "auto_tuned"
        ),
        None,
    )
    if not auto_tuned:
        raise RuntimeError("real prompt-tune 后 manifest 缺少 auto_tuned 候选")
    if auto_tuned.get("source_type") != "graphrag_prompt_tune":
        raise RuntimeError(
            "real prompt-tune 后 auto_tuned.source_type 非 graphrag_prompt_tune："
            f"{auto_tuned.get('source_type')}"
        )


def _build_finalize_steps(
    *,
    root: Path,
    python: str,
    candidate: str,
    scoring_run_id: str,
    scoring_report_path: Path,
    artifact_binding: dict[str, Any],
    min_parse_success_rate: float,
    allow_failed_scoring_gate: bool,
    run_index_after_finalize: bool,
    qa_smoke_file: Path,
) -> list[PipelineStep]:
    finalize_command = [
        python,
        "scripts/finalize_candidate_prompt.py",
        "--candidate",
        candidate,
        "--scoring-run-id",
        scoring_run_id,
        "--scoring-report",
        _as_posix(scoring_report_path),
        "--expected-manifest-sha256",
        str(artifact_binding.get("manifest_sha256") or ""),
        "--expected-scoring-result-sha256",
        str(artifact_binding.get("scoring_result_sha256") or ""),
        "--min-parse-success-rate",
        f"{min_parse_success_rate:g}",
        "--require-scoring-binding",
    ]
    if allow_failed_scoring_gate:
        finalize_command.append("--allow-failed-scoring-gate")

    steps = [_step("finalize_best_candidate", finalize_command, root)]
    if run_index_after_finalize:
        steps.append(
            _step(
                "index_after_finalize",
                [python, "scripts/run_graphrag_index.py", "--root", "."],
                root,
            )
        )
        steps.append(
            _step(
                "qa_smoke_after_index",
                [
                    python,
                    "scripts/run_material_qa_smoke.py",
                    "--root",
                    ".",
                    "--qa-file",
                    _as_posix(qa_smoke_file),
                    "--output-file",
                    _as_posix(
                        Path("results")
                        / "qa_eval"
                        / f"{qa_smoke_file.stem.replace('_qa_smoke', '_smoke')}.json"
                    ),
                    "--method",
                    "local",
                ],
                root,
            )
        )
    return steps


def run_material_prompt_pipeline(
    *,
    root: Path = PROJECT_ROOT,
    course_id: str,
    material_id: int,
    json_file: str,
    run_id: str,
    mode: str = "smoke",
    prompt_tune_mode: str = "dry-run",
    python_executable: str | None = None,
    max_samples: int = 80,
    audit_sample_size: int = 20,
    smoke_limit: int = 1,
    full_limit: int = 20,
    concurrency: int = 1,
    sample_timeout_seconds: float = 300,
    stream_mode: str = "on",
    candidate_view: str = "compact",
    prompt_tune_no_entity_types: bool = False,
    prompt_tune_domain: str = DEFAULT_PROMPT_TUNE_DOMAIN,
    prompt_tune_language: str = DEFAULT_PROMPT_TUNE_LANGUAGE,
    overwrite: bool = True,
    dry_run: bool = False,
    finalize_best: bool = False,
    min_parse_success_rate: float = 0.8,
    index_after_finalize: bool = False,
    allow_experimental_finalize: bool = False,
    runner: Runner | None = None,
) -> dict[str, Any]:
    """执行或预览 material 级 prompt tuning 流水线。"""

    project_root = root.resolve()
    python = python_executable or sys.executable
    summary_context = {
        "root": str(project_root),
        "mode": mode,
        "prompt_tune_mode": prompt_tune_mode,
        "candidate_view": candidate_view,
        "prompt_tune_no_entity_types": prompt_tune_no_entity_types,
        "prompt_tune_domain": prompt_tune_domain,
        "prompt_tune_language": prompt_tune_language,
        "python_executable": python,
        "python_env_warning": _python_env_warning(python),
        "run_id": run_id,
        "allow_experimental_finalize": allow_experimental_finalize,
    }
    steps = build_material_prompt_pipeline_steps(
        root=project_root,
        course_id=course_id,
        material_id=material_id,
        json_file=json_file,
        run_id=run_id,
        mode=mode,
        prompt_tune_mode=prompt_tune_mode,
        python_executable=python,
        max_samples=max_samples,
        audit_sample_size=audit_sample_size,
        smoke_limit=smoke_limit,
        full_limit=full_limit,
        concurrency=concurrency,
        sample_timeout_seconds=sample_timeout_seconds,
        stream_mode=stream_mode,
        candidate_view=candidate_view,
        prompt_tune_no_entity_types=prompt_tune_no_entity_types,
        prompt_tune_domain=prompt_tune_domain,
        prompt_tune_language=prompt_tune_language,
        overwrite=overwrite,
    )
    qa_smoke_file = DEFAULT_QA_SMOKE_DIR / f"{_material_slug(material_id)}_qa_smoke.json"

    if dry_run:
        return {
            "status": "dry_run",
            **summary_context,
            "step_count": len(steps),
            "steps": [asdict(step) for step in steps],
            "qa_smoke_file": str(qa_smoke_file),
            "finalize_best": finalize_best,
        }

    execute = runner or _default_runner
    executed: list[dict[str, Any]] = []
    for step in steps:
        completed = execute(step.command, Path(step.cwd))
        executed.append(
            {
                "name": step.name,
                "command": step.command,
                "cwd": step.cwd,
                "returncode": completed.returncode,
                "stdout_tail": completed.stdout[-1200:] if completed.stdout else "",
                "stderr_tail": completed.stderr[-1200:] if completed.stderr else "",
            }
        )
        if completed.returncode != 0:
            return {
                "status": "failed",
                **summary_context,
                "failed_step": step.name,
                "executed_steps": executed,
            }
        if step.name == "prompt_tune_real_run":
            prompt_tune_report = (
                project_root
                / "results"
                / "reports"
                / f"{_material_slug(material_id)}_prompt_tune_report.json"
            )
            try:
                _validate_real_prompt_tune_report(
                    root=project_root,
                    report_path=prompt_tune_report,
                )
            except RuntimeError as exc:
                return {
                    "status": "failed",
                    **summary_context,
                    "failed_step": "prompt_tune_real_validation",
                    "error": str(exc),
                    "executed_steps": executed,
                }
        if step.name == "score_smoke" and mode == "full":
            smoke_top_path = (
                project_root
                / "results"
                / "reports"
                / "extraction_scoring"
                / "runs"
                / _smoke_run_id(run_id)
                / "top_candidates.json"
            )
            try:
                _load_top_candidate_for_finalization(
                    top_candidates_path=smoke_top_path,
                    min_parse_success_rate=min_parse_success_rate,
                    require_gate_passed=False,
                )
            except RuntimeError as exc:
                return {
                    "status": "failed",
                    **summary_context,
                    "failed_step": "smoke_gate",
                    "error": str(exc),
                    "executed_steps": executed,
                }

    finalized_candidate: str | None = None
    if finalize_best:
        if mode != "full":
            return {
                "status": "failed",
                **summary_context,
                "failed_step": "finalize_requires_full_mode",
                "error": "--finalize-best 只允许在 --mode full 后读取 full top-k 报告",
                "executed_steps": executed,
            }
        scoring_run_id = run_id if mode == "full" else _smoke_run_id(run_id)
        top_candidates_path = (
            project_root
            / "results"
            / "reports"
            / "extraction_scoring"
            / "runs"
            / scoring_run_id
            / "top_candidates.json"
        )
        try:
            candidate, best_metrics = _load_top_candidate_for_finalization(
                top_candidates_path=top_candidates_path,
                min_parse_success_rate=min_parse_success_rate,
                require_gate_passed=not allow_experimental_finalize,
                require_artifact_binding=True,
            )
        except RuntimeError as exc:
            return {
                "status": "failed",
                **summary_context,
                "failed_step": "select_best_candidate_for_finalization",
                "error": str(exc),
                "executed_steps": executed,
            }
        finalize_steps = _build_finalize_steps(
            root=project_root,
            python=python,
            candidate=candidate,
            scoring_run_id=scoring_run_id,
            scoring_report_path=top_candidates_path,
            artifact_binding=best_metrics.get("artifact_binding") or {},
            min_parse_success_rate=min_parse_success_rate,
            allow_failed_scoring_gate=allow_experimental_finalize,
            run_index_after_finalize=index_after_finalize,
            qa_smoke_file=qa_smoke_file,
        )
        for step in finalize_steps:
            completed = execute(step.command, Path(step.cwd))
            executed.append(
                {
                    "name": step.name,
                    "command": step.command,
                    "cwd": step.cwd,
                    "returncode": completed.returncode,
                    "stdout_tail": completed.stdout[-1200:] if completed.stdout else "",
                    "stderr_tail": completed.stderr[-1200:] if completed.stderr else "",
                    "best_metrics": best_metrics if step.name == "finalize_best_candidate" else None,
                }
            )
            if completed.returncode != 0:
                return {
                    "status": "failed",
                    **summary_context,
                    "failed_step": step.name,
                    "executed_steps": executed,
                }
        finalized_candidate = candidate

    return {
        "status": "success",
        **summary_context,
        "step_count": len(executed),
        "executed_steps": executed,
        "qa_smoke_file": str(qa_smoke_file),
        "finalized_candidate": finalized_candidate,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="运行单份 material 的 Prompt 调优流水线")
    parser.add_argument("--course-id", required=True, help="课程 ID，例如 crs-20260506-r4slkr")
    parser.add_argument("--material-id", required=True, type=int, help="课程材料 ID，例如 7")
    parser.add_argument("--json-file", required=True, help="从 MinIO 拉取的 GraphRAG JSON 文件名")
    parser.add_argument("--run-id", required=True, help="评分 run-id，例如 material_7_full")
    parser.add_argument("--mode", choices=["smoke", "full"], default="smoke", help="默认只跑 smoke")
    parser.add_argument(
        "--prompt-tune-mode",
        choices=["dry-run", "real", "skip"],
        default="dry-run",
        help="默认只执行 GraphRAG prompt-tune dry-run",
    )
    parser.add_argument("--python", default=None, help="覆盖子命令使用的 Python 可执行文件")
    parser.add_argument("--max-samples", type=int, default=80, help="样本构建数量")
    parser.add_argument("--audit-sample-size", type=int, default=20, help="audit 集数量")
    parser.add_argument("--smoke-limit", type=int, default=1, help="smoke 每候选抽取样本数")
    parser.add_argument("--full-limit", type=int, default=20, help="full 每候选抽取样本数")
    parser.add_argument("--concurrency", type=int, default=1, help="候选内并发数")
    parser.add_argument("--sample-timeout", type=float, default=300, help="单样本超时秒数")
    parser.add_argument("--stream-mode", choices=["on", "off"], default="on", help="LLM 流式模式")
    parser.add_argument(
        "--candidate-view",
        choices=["compact", "full"],
        default="compact",
        help="候选 Prompt 注入方式，默认 compact；固化前可用 full 复核完整候选",
    )
    parser.add_argument(
        "--prompt-tune-no-entity-types",
        action="store_true",
        help="真实 prompt-tune 时透传 --no-discover-entity-types，适配不支持 response_format 的模型通道",
    )
    parser.add_argument(
        "--prompt-tune-domain",
        default=DEFAULT_PROMPT_TUNE_DOMAIN,
        help="透传给 GraphRAG prompt-tune 的 domain 参数",
    )
    parser.add_argument(
        "--prompt-tune-language",
        default=DEFAULT_PROMPT_TUNE_LANGUAGE,
        help="透传给 GraphRAG prompt-tune 的 language 参数",
    )
    parser.add_argument("--no-overwrite", action="store_true", help="不覆盖已有产物")
    parser.add_argument("--dry-run", action="store_true", help="只打印步骤，不执行")
    parser.add_argument(
        "--finalize-best",
        action="store_true",
        help="评分后按门槛固化最佳候选；未达标会失败且不会固化",
    )
    parser.add_argument(
        "--min-parse-success-rate",
        type=float,
        default=0.8,
        help="固化所需 parse_success_rate 门槛",
    )
    parser.add_argument(
        "--index-after-finalize",
        action="store_true",
        help="固化成功后执行 graphrag index --root .",
    )
    parser.add_argument(
        "--allow-experimental-finalize",
        action="store_true",
        help="实验性允许 full top candidate 在 gate_passed=false 但 parse 成功率达标时固化；不会绕过 smoke gate",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    summary = run_material_prompt_pipeline(
        root=PROJECT_ROOT,
        course_id=args.course_id,
        material_id=args.material_id,
        json_file=args.json_file,
        run_id=args.run_id,
        mode=args.mode,
        prompt_tune_mode=args.prompt_tune_mode,
        python_executable=args.python,
        max_samples=args.max_samples,
        audit_sample_size=args.audit_sample_size,
        smoke_limit=args.smoke_limit,
        full_limit=args.full_limit,
        concurrency=args.concurrency,
        sample_timeout_seconds=args.sample_timeout,
        stream_mode=args.stream_mode,
        candidate_view=args.candidate_view,
        prompt_tune_no_entity_types=args.prompt_tune_no_entity_types,
        prompt_tune_domain=args.prompt_tune_domain,
        prompt_tune_language=args.prompt_tune_language,
        overwrite=not args.no_overwrite,
        dry_run=args.dry_run,
        finalize_best=args.finalize_best,
        min_parse_success_rate=args.min_parse_success_rate,
        index_after_finalize=args.index_after_finalize,
        allow_experimental_finalize=args.allow_experimental_finalize,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary.get("status") in {"success", "dry_run"} else 1


if __name__ == "__main__":
    raise SystemExit(main())
