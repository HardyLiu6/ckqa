#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GraphRAG prompt-tune 统一封装脚本
================================

目标：
1. 自动探测 GraphRAG 官方 prompt-tune 调用方式。
2. 统一执行、记录日志、生成报告。
3. 将 auto-tuned Prompt 整理到现有候选 Prompt 目录体系。
4. 增量更新 prompts/candidates/manifest.json。

说明：
1. 仅封装 prompt-tune，不负责评分、抽取执行或 BenchmarkQED。
2. 优先兼容 GraphRAG 官方 CLI 的当前入口，并保留有序回退。
3. 尽量仅依赖 Python 标准库。
"""

from __future__ import annotations

import argparse
import contextlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, Iterator, List, Optional, Sequence


DEFAULT_OUTPUT_SUBDIR = Path("prompts") / "candidates" / "auto_tuned"
DEFAULT_LOG_SUBPATH = Path("results") / "reports" / "prompt_tune_run.log"
DEFAULT_REPORT_SUBPATH = Path("results") / "reports" / "prompt_tune_report.json"
DEFAULT_MANIFEST_SUBPATH = Path("prompts") / "candidates" / "manifest.json"
DEFAULT_CONFIG_NAMES = ("settings.yaml", "settings.yml")
TEXT_FILE_SUFFIXES = {".txt", ".md"}
PRIMARY_PROMPT_KEYWORDS = ("extract_graph", "graph", "entity", "prompt")
RESERVED_EXTRA_ARG_PREFIXES = (
    "--root",
    "-r",
    "--output",
    "-o",
)
CONDA_ACTIVATE_RE = re.compile(r"^\s*conda activate ([^\s`]+)", re.MULTILINE)


class PromptTuneError(RuntimeError):
    """prompt-tune 统一封装异常。"""


class PromptTuneDiscoveryError(PromptTuneError):
    """命令探测失败，同时携带探测详情。"""

    def __init__(self, message: str, attempts: Sequence["InvocationAttempt"]) -> None:
        super().__init__(message)
        self.attempts = list(attempts)


@dataclass(frozen=True)
class InvocationChoice:
    name: str
    display_name: str
    help_command: List[str]
    command_prefix: List[str]
    uses_poe_passthrough: bool = False


@dataclass(frozen=True)
class InvocationAttempt:
    name: str
    display_name: str
    command: List[str]
    available: bool
    exit_code: Optional[int]
    stdout_preview: str
    stderr_preview: str


@dataclass(frozen=True)
class CommandExecutionResult:
    command: List[str]
    cwd: str
    exit_code: int
    stdout: str
    stderr: str
    started_at: str = ""
    ended_at: str = ""
    duration_seconds: float = 0.0


@dataclass(frozen=True)
class InvocationRoot:
    path: Path
    staged: bool
    notes: List[str] = field(default_factory=list)


Runner = Callable[[List[str], Path, Dict[str, str]], CommandExecutionResult]


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def _shorten(text: str, limit: int = 240) -> str:
    collapsed = " ".join(text.split())
    if len(collapsed) <= limit:
        return collapsed
    return f"{collapsed[: limit - 1].rstrip()}…"


def _ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def _append_log(log_file: Path, lines: Sequence[str]) -> None:
    _ensure_parent(log_file)
    with log_file.open("a", encoding="utf-8") as handle:
        for line in lines:
            handle.write(f"{line}\n")


def _write_json(path: Path, payload: Dict[str, Any]) -> None:
    _ensure_parent(path)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _read_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _resolve_path(value: Path | str | None, *, root: Path) -> Optional[Path]:
    if value is None:
        return None
    path = value if isinstance(value, Path) else Path(value)
    if path.is_absolute():
        return path.resolve()
    return (root / path).resolve()


def _build_runtime_env(root: Path) -> Dict[str, str]:
    env = os.environ.copy()
    uv_cache_dir = Path(tempfile.gettempdir()) / "ckqa-uv-cache"
    uv_cache_dir.mkdir(parents=True, exist_ok=True)
    env.setdefault("UV_CACHE_DIR", str(uv_cache_dir))
    env.setdefault("PYTHONUNBUFFERED", "1")
    env.setdefault("CKQA_GRAPHRAG_ROOT", str(root.resolve()))
    return env


def _iter_conda_base_dirs() -> List[Path]:
    candidates: List[Path] = []
    sys_python = Path(sys.executable).resolve()
    if sys_python.name == "python" and sys_python.parent.name == "bin":
        candidates.append(sys_python.parent.parent)

    conda_exe = os.environ.get("CONDA_EXE")
    if conda_exe:
        conda_path = Path(conda_exe).resolve()
        if conda_path.parent.name == "bin":
            candidates.append(conda_path.parent.parent)

    unique: List[Path] = []
    for path in candidates:
        if path not in unique and path.exists():
            unique.append(path)
    return unique


def _extract_conda_env_hints_from_claude(root: Path) -> List[str]:
    claude_path = root / "CLAUDE.md"
    if not claude_path.exists():
        return []

    text = claude_path.read_text(encoding="utf-8")
    names = [match.group(1).strip() for match in CONDA_ACTIVATE_RE.finditer(text)]
    unique: List[str] = []
    for name in names:
        if name and name not in unique:
            unique.append(name)
    return unique


def _python_module_invocation_choices(root: Path) -> List[InvocationChoice]:
    choices: List[InvocationChoice] = [
        InvocationChoice(
            name="python_module",
            display_name="python -m graphrag prompt-tune",
            help_command=[sys.executable, "-m", "graphrag", "prompt-tune", "--help"],
            command_prefix=[sys.executable, "-m", "graphrag", "prompt-tune"],
        )
    ]

    conda_env_names = _extract_conda_env_hints_from_claude(root)
    if not conda_env_names:
        return choices

    for base_dir in _iter_conda_base_dirs():
        for env_name in conda_env_names:
            python_path = base_dir / "envs" / env_name / "bin" / "python"
            if not python_path.exists():
                continue
            if str(python_path.resolve()) == str(Path(sys.executable).resolve()):
                continue
            choices.append(
                InvocationChoice(
                    name="python_module_conda_env",
                    display_name=f"{python_path} -m graphrag prompt-tune",
                    help_command=[str(python_path), "-m", "graphrag", "prompt-tune", "--help"],
                    command_prefix=[str(python_path), "-m", "graphrag", "prompt-tune"],
                )
            )
    return choices


def _default_runner(command: List[str], cwd: Path, env: Dict[str, str]) -> CommandExecutionResult:
    started = _now_iso()
    started_at = time.perf_counter()
    try:
        completed = subprocess.run(
            command,
            cwd=str(cwd),
            env=env,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        duration = time.perf_counter() - started_at
        ended = _now_iso()
        return CommandExecutionResult(
            command=command,
            cwd=str(cwd),
            exit_code=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
            started_at=started,
            ended_at=ended,
            duration_seconds=duration,
        )
    except OSError as exc:
        duration = time.perf_counter() - started_at
        ended = _now_iso()
        return CommandExecutionResult(
            command=command,
            cwd=str(cwd),
            exit_code=127,
            stdout="",
            stderr=str(exc),
            started_at=started,
            ended_at=ended,
            duration_seconds=duration,
        )


def _invocation_candidates(root: Path) -> List[InvocationChoice]:
    return [
        *_python_module_invocation_choices(root),
        InvocationChoice(
            name="graphrag_cli",
            display_name="graphrag prompt-tune",
            help_command=["graphrag", "prompt-tune", "--help"],
            command_prefix=["graphrag", "prompt-tune"],
        ),
        InvocationChoice(
            name="uv_poe",
            display_name="uv run poe prompt_tune",
            help_command=["uv", "run", "poe", "prompt_tune", "--help"],
            command_prefix=["uv", "run", "poe", "prompt_tune"],
            uses_poe_passthrough=True,
        ),
    ]


def _format_attempts_error(attempts: Sequence[InvocationAttempt]) -> str:
    details = []
    for attempt in attempts:
        stderr = attempt.stderr_preview or attempt.stdout_preview or "无输出"
        details.append(
            f"- {attempt.display_name}: exit_code={attempt.exit_code}, 摘要={stderr}"
        )
    return (
        "未找到可用的 GraphRAG prompt-tune 调用方式。已尝试：\n"
        + "\n".join(details)
        + "\n请检查 graphrag 是否已安装，或是否已配置可用的 poe task。"
    )


def find_graphrag_invocation(
    *,
    root: Path,
    runner: Runner = _default_runner,
    env: Optional[Dict[str, str]] = None,
) -> tuple[InvocationChoice, List[InvocationAttempt]]:
    runtime_env = env or _build_runtime_env(root)
    attempts: List[InvocationAttempt] = []

    for choice in _invocation_candidates(root):
        result = runner(choice.help_command, root, runtime_env)
        attempt = InvocationAttempt(
            name=choice.name,
            display_name=choice.display_name,
            command=choice.help_command,
            available=result.exit_code == 0,
            exit_code=result.exit_code,
            stdout_preview=_shorten(result.stdout),
            stderr_preview=_shorten(result.stderr),
        )
        attempts.append(attempt)
        if attempt.available:
            return choice, attempts

    raise PromptTuneDiscoveryError(_format_attempts_error(attempts), attempts)


def resolve_config_path(root: Path, config: Path | str | None) -> Path:
    if config is not None:
        config_path = _resolve_path(config, root=root)
        assert config_path is not None
        if not config_path.exists():
            raise PromptTuneError(f"指定的配置文件不存在：{config_path}")
        if not config_path.is_file():
            raise PromptTuneError(f"指定的配置路径不是文件：{config_path}")
        return config_path

    for name in DEFAULT_CONFIG_NAMES:
        candidate = root / name
        if candidate.exists():
            return candidate.resolve()
    raise PromptTuneError(
        f"在 GraphRAG 根目录 {root} 下未找到 settings.yaml/settings.yml，请通过 --config 显式指定。"
    )


def _count_input_documents(input_dir: Path) -> int:
    if not input_dir.exists():
        return 0
    return sum(1 for path in input_dir.rglob("*") if path.is_file())


def validate_workspace(
    *,
    root: Path,
    config_path: Path,
    require_input: bool,
) -> List[str]:
    if not root.exists():
        raise PromptTuneError(f"GraphRAG 根目录不存在：{root}")
    if not root.is_dir():
        raise PromptTuneError(f"GraphRAG 根路径不是目录：{root}")

    warnings: List[str] = []
    if not config_path.exists():
        raise PromptTuneError(f"配置文件不存在：{config_path}")

    prompts_dir = root / "prompts"
    if not prompts_dir.exists():
        warnings.append(f"根目录下未发现 prompts/：{prompts_dir}")

    input_dir = root / "input"
    input_count = _count_input_documents(input_dir)
    if require_input and input_count == 0:
        raise PromptTuneError(
            f"GraphRAG root 缺少可用于 prompt-tune 的输入数据：{input_dir}"
        )
    if not require_input and input_count == 0:
        warnings.append(f"当前未检测到 input/ 数据文件：{input_dir}")

    env_file = root / ".env"
    if not env_file.exists():
        warnings.append(f"未发现 .env：{env_file}")

    return warnings


def _copy_file(src: Path, dst: Path, overwrite: bool) -> None:
    if dst.exists() and not overwrite:
        raise PromptTuneError(f"目标文件已存在，若要覆盖请传 --overwrite：{dst}")
    _ensure_parent(dst)
    shutil.copy2(src, dst)


def _link_or_copy(src: Path, dst: Path) -> None:
    if src.is_dir():
        try:
            os.symlink(src, dst, target_is_directory=True)
            return
        except OSError:
            shutil.copytree(src, dst, dirs_exist_ok=True)
            return

    try:
        os.symlink(src, dst)
    except OSError:
        _ensure_parent(dst)
        shutil.copy2(src, dst)


@contextlib.contextmanager
def prepare_invocation_root(root: Path, config_path: Path) -> Iterator[InvocationRoot]:
    default_config_paths = {(root / name).resolve() for name in DEFAULT_CONFIG_NAMES}
    if config_path.resolve() in default_config_paths:
        yield InvocationRoot(path=root.resolve(), staged=False, notes=[])
        return

    with tempfile.TemporaryDirectory(prefix="ckqa-graphrag-prompt-tune-") as tmp_dir:
        staging_root = Path(tmp_dir).resolve()
        shutil.copy2(config_path, staging_root / "settings.yaml")
        for name in ("input", "prompts", ".env"):
            source = root / name
            if source.exists():
                _link_or_copy(source, staging_root / name)
        notes = [
            "检测到外部 config 路径，已创建临时 staging root 兼容官方 --root 入口。",
            f"外部 config 来源：{config_path.resolve()}",
        ]
        yield InvocationRoot(path=staging_root, staged=True, notes=notes)


def _relative_output_arg(root: Path, path: Path) -> str:
    try:
        return str(path.resolve().relative_to(root.resolve()))
    except ValueError:
        return str(path.resolve())


def _make_run_id() -> str:
    return datetime.now(timezone.utc).astimezone().strftime("%Y%m%dT%H%M%S")


def validate_extra_args(extra_args: Sequence[str]) -> List[str]:
    cleaned: List[str] = []
    for item in extra_args:
        value = item.strip()
        if not value:
            continue
        if "\n" in value or "\r" in value:
            raise PromptTuneError(f"extra_args 包含非法换行：{value!r}")
        if value.startswith(RESERVED_EXTRA_ARG_PREFIXES):
            raise PromptTuneError(
                f"extra_args 不允许覆盖受管参数（--root/--output）：{value}"
            )
        cleaned.append(value)
    return cleaned


def build_prompt_tune_command(
    *,
    invocation: InvocationChoice,
    invocation_root: Path,
    output_arg: str,
    domain: Optional[str],
    language: Optional[str],
    chunk_size: Optional[int],
    no_entity_types: bool,
    extra_args: Sequence[str],
) -> List[str]:
    command = list(invocation.command_prefix)
    if invocation.uses_poe_passthrough:
        command.append("--")

    command.extend(["--root", str(invocation_root.resolve()), "--output", output_arg])
    if domain:
        command.extend(["--domain", domain])
    if language:
        command.extend(["--language", language])
    if chunk_size is not None:
        command.extend(["--chunk-size", str(chunk_size)])
    if no_entity_types:
        command.append("--no-discover-entity-types")
    command.extend(validate_extra_args(extra_args))
    return command


def run_command(
    *,
    command: List[str],
    cwd: Path,
    env: Dict[str, str],
    runner: Runner = _default_runner,
) -> CommandExecutionResult:
    return runner(command, cwd, env)


def _is_prompt_related_text_file(path: Path) -> bool:
    lowered = path.name.lower()
    if path.suffix.lower() in TEXT_FILE_SUFFIXES:
        return True
    return "prompt" in lowered or "graph" in lowered


def _unique_destination(path: Path) -> Path:
    if not path.exists():
        return path
    stem = path.stem
    suffix = path.suffix
    counter = 2
    while True:
        candidate = path.with_name(f"{stem}_{counter}{suffix}")
        if not candidate.exists():
            return candidate
        counter += 1


def _choose_primary_prompt(files: Sequence[Path]) -> Path:
    def score(path: Path) -> tuple[int, int, str]:
        lowered = path.name.lower()
        primary_bonus = 0 if "extract_graph" in lowered else 1
        keyword_score = -sum(1 for keyword in PRIMARY_PROMPT_KEYWORDS if keyword in lowered)
        return (primary_bonus, keyword_score, lowered)

    return sorted(files, key=score)[0]


def collect_output_files(
    *,
    raw_output_dir: Path,
    candidate_output_dir: Path,
    overwrite: bool,
) -> Dict[str, Any]:
    if not raw_output_dir.exists():
        raise PromptTuneError(f"prompt-tune 输出目录不存在：{raw_output_dir}")

    candidate_output_dir.mkdir(parents=True, exist_ok=True)
    source_files = [
        path
        for path in raw_output_dir.rglob("*")
        if path.is_file() and _is_prompt_related_text_file(path)
    ]
    if not source_files:
        raise PromptTuneError(
            f"prompt-tune 执行成功，但未在 {raw_output_dir} 中识别到 prompt 相关文本文件。"
        )

    copied_names: List[str] = []
    copied_paths: List[Path] = []
    for source_path in sorted(source_files):
        destination = candidate_output_dir / source_path.name
        if destination.exists() and source_path.resolve() != destination.resolve():
            if overwrite:
                destination.unlink()
            else:
                destination = _unique_destination(destination)
        _copy_file(source_path, destination, overwrite=True)
        copied_names.append(destination.name)
        copied_paths.append(destination)

    primary_prompt = _choose_primary_prompt(copied_paths)
    canonical_prompt = candidate_output_dir / "prompt.txt"
    if primary_prompt.resolve() != canonical_prompt.resolve():
        _copy_file(primary_prompt, canonical_prompt, overwrite=True)
        if canonical_prompt.name not in copied_names:
            copied_names.append(canonical_prompt.name)

    return {
        "primary_prompt_path": str(primary_prompt.resolve()),
        "primary_prompt_file": primary_prompt.name,
        "collected_files": sorted(copied_names),
    }


def build_auto_tuned_readme(
    *,
    generated_at: str,
    invocation: InvocationChoice,
    root_path: Path,
    config_path: Path,
    output_dir: Path,
    domain: Optional[str],
    language: Optional[str],
    chunk_size: Optional[int],
    collected_files: Sequence[str],
    notes: Sequence[str],
) -> str:
    note_lines = "\n".join(f"- {note}" for note in notes) if notes else "- 无"
    collected_lines = "\n".join(f"- `{name}`" for name in collected_files) if collected_files else "- 无"
    return f"""# auto_tuned

## 来源

本目录来源于 GraphRAG prompt-tune 自动生成结果，并已整理为当前项目候选 Prompt 目录结构。

## 本次执行

- 调用时间：`{generated_at}`
- 命令入口：`{invocation.display_name}`
- GraphRAG root：`{root_path.resolve()}`
- config 路径：`{config_path.resolve()}`
- 输出目录：`{output_dir.resolve()}`
- domain：`{domain or "未显式传入"}`
- language：`{language or "未显式传入"}`
- chunk_size：`{chunk_size if chunk_size is not None else "未显式传入"}`

## 与其他候选 Prompt 的区别

- `default`：接近当前项目默认抽取 Prompt 的基线版本。
- `schema_aware`：在基线 Prompt 上显式注入课程 Schema 约束。
- `schema_fewshot`：在 schema_aware 基础上加入轻量 few-shot 示例。
- `auto_tuned`：由 GraphRAG 官方 prompt-tune 根据当前输入数据自动生成，强调对语料自适应，而不是人工注入课程 Schema。

## 归档文件

{collected_lines}

## 备注

{note_lines}
"""


def write_run_report(report_file: Path, report_payload: Dict[str, Any]) -> None:
    _write_json(report_file, report_payload)


def update_candidate_manifest(
    *,
    manifest_file: Path,
    candidate_record: Dict[str, Any],
) -> Dict[str, Any]:
    warnings: List[str] = []
    payload: Dict[str, Any]

    if manifest_file.exists():
        try:
            raw_payload = _read_json(manifest_file)
        except json.JSONDecodeError:
            warnings.append("manifest 已存在但不是合法 JSON，已按新结构重建。")
            raw_payload = {}
        if isinstance(raw_payload, dict):
            payload = raw_payload
        else:
            warnings.append("manifest 已存在但顶层不是对象，已按新结构重建。")
            payload = {}
    else:
        payload = {}

    candidates = payload.get("candidates")
    if not isinstance(candidates, list):
        if candidates is not None:
            warnings.append("manifest.candidates 不是列表，已重置为空列表。")
        candidates = []

    existing_index = None
    existing_item: Dict[str, Any] = {}
    for index, item in enumerate(candidates):
        if isinstance(item, dict) and item.get("candidate_name") == candidate_record["candidate_name"]:
            existing_index = index
            existing_item = item
            break

    merged_record = dict(existing_item)
    merged_record.update(candidate_record)
    if warnings:
        merged_notes = list(merged_record.get("notes", []))
        merged_notes.extend(note for note in warnings if note not in merged_notes)
        merged_record["notes"] = merged_notes

    if existing_index is None:
        candidates.append(merged_record)
    else:
        candidates[existing_index] = merged_record

    payload["candidates"] = candidates
    payload.setdefault("task", "candidate_prompt_generation")
    payload["last_updated_at"] = candidate_record["generation_time"]
    _write_json(manifest_file, payload)
    return payload


def _base_report(
    *,
    status: str,
    root: Path,
    config_path: Optional[Path],
    output_dir: Path,
    log_file: Path,
    report_file: Path,
    manifest_file: Path,
    invocation_root: Optional[InvocationRoot],
    invocation: Optional[InvocationChoice],
    attempts: Sequence[InvocationAttempt],
    final_command: Sequence[str],
    domain: Optional[str],
    language: Optional[str],
    chunk_size: Optional[int],
    no_entity_types: bool,
    extra_args: Sequence[str],
    warnings: Sequence[str],
) -> Dict[str, Any]:
    return {
        "task": "graphrag_prompt_tune",
        "status": status,
        "generated_at": _now_iso(),
        "chosen_invocation": (
            {
                "name": invocation.name,
                "display_name": invocation.display_name,
            }
            if invocation is not None
            else None
        ),
        "detection_attempts": [asdict(item) for item in attempts],
        "resolved_paths": {
            "root_path": str(root.resolve()),
            "config_path": str(config_path.resolve()) if config_path else None,
            "invocation_root": str(invocation_root.path.resolve()) if invocation_root else None,
            "output_dir": str(output_dir.resolve()),
            "log_file": str(log_file.resolve()),
            "report_file": str(report_file.resolve()),
            "manifest_file": str(manifest_file.resolve()),
        },
        "parameters": {
            "domain": domain,
            "language": language,
            "chunk_size": chunk_size,
            "no_entity_types": no_entity_types,
            "extra_args": list(extra_args),
        },
        "final_command": list(final_command),
        "warnings": list(warnings),
    }


def run_prompt_tune(
    *,
    root: Path,
    config: Path | str | None,
    output_dir: Path,
    log_file: Path,
    report_file: Path,
    manifest_file: Path,
    domain: Optional[str],
    language: Optional[str],
    chunk_size: Optional[int],
    discover_only: bool,
    dry_run: bool,
    no_entity_types: bool,
    overwrite: bool,
    extra_args: Sequence[str],
    runner: Runner = _default_runner,
) -> Dict[str, Any]:
    root = root.resolve()
    output_dir = output_dir.resolve()
    log_file = log_file.resolve()
    report_file = report_file.resolve()
    manifest_file = manifest_file.resolve()

    start_time = _now_iso()
    _append_log(
        log_file,
        [
            f"[{start_time}] 开始执行 GraphRAG prompt-tune 封装",
            f"root={root}",
        ],
    )

    runtime_env = _build_runtime_env(root)
    attempts: List[InvocationAttempt] = []
    invocation: Optional[InvocationChoice] = None
    invocation_root: Optional[InvocationRoot] = None
    warnings: List[str] = []
    config_path: Optional[Path] = None
    final_command: List[str] = []

    try:
        config_path = resolve_config_path(root, config)
        warnings.extend(
            validate_workspace(
                root=root,
                config_path=config_path,
                require_input=not (discover_only or dry_run),
            )
        )
        output_dir.mkdir(parents=True, exist_ok=True)
        attempts = []
        try:
            invocation, attempts = find_graphrag_invocation(root=root, runner=runner, env=runtime_env)
        except PromptTuneDiscoveryError as exc:
            attempts = list(exc.attempts)
            raise
        warnings.extend(warning for warning in warnings if warning)

        with prepare_invocation_root(root, config_path) as prepared_root:
            invocation_root = prepared_root
            warnings.extend(note for note in prepared_root.notes if note not in warnings)

            run_id = _make_run_id()
            if prepared_root.staged:
                raw_output_dir = prepared_root.path / "_prompt_tune_output"
                output_arg = "_prompt_tune_output"
            else:
                raw_output_dir = root / "results" / "prompt_tune_runs" / run_id / "raw_output"
                raw_output_dir.mkdir(parents=True, exist_ok=True)
                output_arg = _relative_output_arg(root, raw_output_dir)

            final_command = build_prompt_tune_command(
                invocation=invocation,
                invocation_root=prepared_root.path,
                output_arg=output_arg,
                domain=domain,
                language=language,
                chunk_size=chunk_size,
                no_entity_types=no_entity_types,
                extra_args=extra_args,
            )

            if discover_only:
                report = _base_report(
                    status="discover_only",
                    root=root,
                    config_path=config_path,
                    output_dir=output_dir,
                    log_file=log_file,
                    report_file=report_file,
                    manifest_file=manifest_file,
                    invocation_root=invocation_root,
                    invocation=invocation,
                    attempts=attempts,
                    final_command=final_command,
                    domain=domain,
                    language=language,
                    chunk_size=chunk_size,
                    no_entity_types=no_entity_types,
                    extra_args=extra_args,
                    warnings=warnings,
                )
                write_run_report(report_file, report)
                _append_log(log_file, [f"discover_only: 选择命令 {invocation.display_name}"])
                return report

            if dry_run:
                report = _base_report(
                    status="dry_run",
                    root=root,
                    config_path=config_path,
                    output_dir=output_dir,
                    log_file=log_file,
                    report_file=report_file,
                    manifest_file=manifest_file,
                    invocation_root=invocation_root,
                    invocation=invocation,
                    attempts=attempts,
                    final_command=final_command,
                    domain=domain,
                    language=language,
                    chunk_size=chunk_size,
                    no_entity_types=no_entity_types,
                    extra_args=extra_args,
                    warnings=warnings,
                )
                write_run_report(report_file, report)
                _append_log(log_file, [f"dry_run: {' '.join(final_command)}"])
                return report

            execution = run_command(
                command=final_command,
                cwd=prepared_root.path,
                env=runtime_env,
                runner=runner,
            )
            _append_log(
                log_file,
                [
                    f"执行命令：{' '.join(final_command)}",
                    f"exit_code={execution.exit_code}",
                    "--- stdout ---",
                    execution.stdout or "(empty)",
                    "--- stderr ---",
                    execution.stderr or "(empty)",
                ],
            )
            if execution.exit_code != 0:
                raise PromptTuneError(
                    "GraphRAG prompt-tune 执行失败，"
                    f"exit_code={execution.exit_code}，stderr={_shorten(execution.stderr, 400)}"
                )

            collected = collect_output_files(
                raw_output_dir=raw_output_dir,
                candidate_output_dir=output_dir,
                overwrite=overwrite,
            )
            readme_text = build_auto_tuned_readme(
                generated_at=start_time,
                invocation=invocation,
                root_path=root,
                config_path=config_path,
                output_dir=output_dir,
                domain=domain,
                language=language,
                chunk_size=chunk_size,
                collected_files=collected["collected_files"],
                notes=warnings,
            )
            (output_dir / "README.md").write_text(readme_text, encoding="utf-8")

            candidate_record = {
                "candidate_name": "auto_tuned",
                "source_type": "graphrag_prompt_tune",
                "generation_method": "graphrag_official_prompt_tune",
                "graphrag_invocation": invocation.display_name,
                "root_path": str(root.resolve()),
                "config_path": str(config_path.resolve()),
                "output_path": str(output_dir.resolve()),
                "generation_time": start_time,
                "status": "success",
                "base_prompt_source": collected["primary_prompt_path"],
                "schema_used": False,
                "audit_used": False,
                "fewshot_used": False,
                "fewshot_example_count": 0,
                "fewshot_strategy": None,
                "files": {
                    "prompt": str((output_dir / "prompt.txt").resolve()),
                    "readme": str((output_dir / "README.md").resolve()),
                },
                "collected_files": collected["collected_files"],
                "notes": [
                    "auto_tuned 候选由 GraphRAG 官方 prompt-tune 生成。",
                    f"主要 Prompt 文件：{collected['primary_prompt_file']}",
                ]
                + warnings,
            }
            manifest_payload = update_candidate_manifest(
                manifest_file=manifest_file,
                candidate_record=candidate_record,
            )

            report = _base_report(
                status="success",
                root=root,
                config_path=config_path,
                output_dir=output_dir,
                log_file=log_file,
                report_file=report_file,
                manifest_file=manifest_file,
                invocation_root=invocation_root,
                invocation=invocation,
                attempts=attempts,
                final_command=final_command,
                domain=domain,
                language=language,
                chunk_size=chunk_size,
                no_entity_types=no_entity_types,
                extra_args=extra_args,
                warnings=warnings,
            )
            report.update(
                {
                    "start_time": execution.started_at or start_time,
                    "end_time": execution.ended_at or _now_iso(),
                    "duration_seconds": execution.duration_seconds,
                    "exit_code": execution.exit_code,
                    "primary_prompt_file": collected["primary_prompt_file"],
                    "primary_prompt_path": collected["primary_prompt_path"],
                    "collected_files": collected["collected_files"],
                    "manifest_candidate_count": len(manifest_payload.get("candidates", [])),
                }
            )
            write_run_report(report_file, report)
            return report

    except PromptTuneError as exc:
        report = _base_report(
            status="failed",
            root=root,
            config_path=config_path,
            output_dir=output_dir,
            log_file=log_file,
            report_file=report_file,
            manifest_file=manifest_file,
            invocation_root=invocation_root,
            invocation=invocation,
            attempts=attempts,
            final_command=final_command,
            domain=domain,
            language=language,
            chunk_size=chunk_size,
            no_entity_types=no_entity_types,
            extra_args=extra_args,
            warnings=warnings + [str(exc)],
        )
        report["error"] = str(exc)
        write_run_report(report_file, report)
        _append_log(log_file, [f"ERROR: {exc}"])
        raise


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="统一封装 GraphRAG 官方 prompt-tune")
    parser.add_argument(
        "--root",
        default=".",
        help="GraphRAG 项目根目录",
    )
    parser.add_argument(
        "--config",
        default=None,
        help="settings.yaml 或等价配置路径；若不传则在 root 下自动发现",
    )
    parser.add_argument(
        "--output_dir",
        default=str(DEFAULT_OUTPUT_SUBDIR),
        help="候选 Prompt 输出目录，默认 prompts/candidates/auto_tuned",
    )
    parser.add_argument(
        "--log_file",
        default=str(DEFAULT_LOG_SUBPATH),
        help="运行日志文件，默认 results/reports/prompt_tune_run.log",
    )
    parser.add_argument(
        "--report_file",
        default=str(DEFAULT_REPORT_SUBPATH),
        help="运行报告文件，默认 results/reports/prompt_tune_report.json",
    )
    parser.add_argument(
        "--domain",
        default=None,
        help="prompt-tune domain 参数",
    )
    parser.add_argument(
        "--language",
        default=None,
        help="prompt-tune language 参数",
    )
    parser.add_argument(
        "--chunk_size",
        type=int,
        default=None,
        help="prompt-tune chunk_size 参数",
    )
    parser.add_argument(
        "--discover_only",
        action="store_true",
        help="只探测可用命令，不执行 prompt-tune",
    )
    parser.add_argument(
        "--dry_run",
        action="store_true",
        help="打印最终命令并生成报告，但不实际执行",
    )
    parser.add_argument(
        "--no_entity_types",
        action="store_true",
        help="向 GraphRAG 透传 --no-discover-entity-types",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="覆盖 auto_tuned 目录中的现有文件",
    )
    parser.add_argument(
        "--extra_args",
        action="append",
        default=[],
        help="额外透传的 GraphRAG CLI 参数；可重复传入，如 --extra_args=--limit=8",
    )
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    output_dir = _resolve_path(args.output_dir, root=root)
    log_file = _resolve_path(args.log_file, root=root)
    report_file = _resolve_path(args.report_file, root=root)
    manifest_file = _resolve_path(DEFAULT_MANIFEST_SUBPATH, root=root)
    assert output_dir is not None and log_file is not None and report_file is not None and manifest_file is not None

    try:
        report = run_prompt_tune(
            root=root,
            config=args.config,
            output_dir=output_dir,
            log_file=log_file,
            report_file=report_file,
            manifest_file=manifest_file,
            domain=args.domain,
            language=args.language,
            chunk_size=args.chunk_size,
            discover_only=args.discover_only,
            dry_run=args.dry_run,
            no_entity_types=args.no_entity_types,
            overwrite=args.overwrite,
            extra_args=args.extra_args,
        )
    except PromptTuneError as exc:
        print(f"[失败] {exc}")
        return 1

    print(
        f"[完成] status={report['status']} report={report_file} "
        f"log={log_file}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
