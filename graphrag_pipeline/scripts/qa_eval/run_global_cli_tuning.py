from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import score_baseline_run
from graphrag_pipeline.scripts.qa_eval.latency_reporter import generate_latency_report
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import REQUIRED_INDEX_OUTPUT_PATHS
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem
from graphrag_pipeline.scripts.qa_eval.test_set_validator import validate_jsonl


DEFAULT_OUTPUT_ROOT = Path("graphrag_pipeline/results/qa_eval/runs")
GLOBAL_MODE = "graphrag-global-search:latest"
TIMEOUT_ERROR_PATTERN = re.compile(
    r"\b(readtimeout|timeouterror|timeoutexpired)\b|\bread timed out\b|\btimed out\b"
)
ANSI_PATTERN = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
SUCCESS_MARKERS = (
    "SUCCESS: Global Search Response:",
    "Global Search Response:",
)


@dataclass(frozen=True, slots=True)
class GlobalSearchConfig:
    concurrent_requests: int
    max_context_tokens: int
    data_max_tokens: int
    map_max_length: int
    reduce_max_length: int

    def as_dict(self) -> dict[str, int]:
        return {
            "concurrent_requests": self.concurrent_requests,
            "max_context_tokens": self.max_context_tokens,
            "data_max_tokens": self.data_max_tokens,
            "map_max_length": self.map_max_length,
            "reduce_max_length": self.reduce_max_length,
        }


GROUP_PRESETS: dict[str, GlobalSearchConfig] = {
    "G0": GlobalSearchConfig(20, 24000, 3000, 250, 600),
    "G1": GlobalSearchConfig(20, 18000, 3000, 250, 600),
    "G2": GlobalSearchConfig(20, 30000, 3000, 250, 600),
    "G3": GlobalSearchConfig(30, 24000, 3000, 250, 600),
    "G5": GlobalSearchConfig(20, 24000, 3000, 200, 500),
}


@dataclass(slots=True)
class GlobalCliTuningRunner:
    graphrag_root: Path
    test_set_path: Path
    index_output_dir: Path
    output_root: Path = DEFAULT_OUTPUT_ROOT
    settings_path: Path | None = None
    env_file: Path | None = None
    groups: list[str] | None = None
    question_ids: list[str] | None = None
    run_id_prefix: str = "global-tuning"
    python_executable: str = sys.executable
    request_timeout_seconds: float = 240.0
    stop_after_timeout_count: int = 2

    def run(self) -> list[Path]:
        graphrag_root = self.graphrag_root.expanduser().resolve()
        settings_path = (self.settings_path or graphrag_root / "settings.yaml").expanduser().resolve()
        env_file = (self.env_file or graphrag_root / ".env").expanduser().resolve()
        test_set_path = self.test_set_path.expanduser().resolve()
        index_output_dir = self._validated_index_output_dir()
        output_root = self.output_root.expanduser().resolve()
        selected_groups = self._selected_groups()
        source_items = _load_test_items(test_set_path)
        items = _select_items(source_items, self.question_ids)
        base_env = _build_subprocess_env(env_file, index_output_dir)

        report = validate_jsonl(test_set_path)
        if not report.ok:
            details = "\n".join(report.errors) if report.errors else "unknown validation error"
            raise ValueError(f"QA test set validation failed:\n{details}")

        original_settings = settings_path.read_text(encoding="utf-8")
        run_dirs: list[Path] = []
        try:
            for group_name in selected_groups:
                config = GROUP_PRESETS[group_name]
                settings_path.write_text(
                    apply_global_search_config(original_settings, config),
                    encoding="utf-8",
                )
                run_dir = self._run_group(
                    group_name=group_name,
                    config=config,
                    items=items,
                    source_total_questions=len(source_items),
                    graphrag_root=graphrag_root,
                    test_set_path=test_set_path,
                    index_output_dir=index_output_dir,
                    output_root=output_root,
                    settings_path=settings_path,
                    env_file=env_file,
                    env=base_env,
                )
                generate_latency_report(run_dir)
                score_baseline_run(run_dir)
                run_dirs.append(run_dir)
        finally:
            settings_path.write_text(original_settings, encoding="utf-8")

        return run_dirs

    def _run_group(
        self,
        *,
        group_name: str,
        config: GlobalSearchConfig,
        items: list[QaTestItem],
        source_total_questions: int,
        graphrag_root: Path,
        test_set_path: Path,
        index_output_dir: Path,
        output_root: Path,
        settings_path: Path,
        env_file: Path,
        env: dict[str, str],
    ) -> Path:
        run_id = _build_run_id(self.run_id_prefix, group_name)
        run_dir = output_root / run_id
        if run_dir.exists():
            raise FileExistsError(f"run dir already exists: {run_dir}")

        raw_dir = run_dir / "raw"
        raw_dir.mkdir(parents=True, exist_ok=False)
        meta = {
            "run_id": run_id,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "runner": "graphrag_cli_global_tuning",
            "group": group_name,
            "global_search_config": config.as_dict(),
            "test_set_path": str(test_set_path),
            "index_run_label": index_output_dir.name,
            "index_output_dir": str(index_output_dir),
            "graphrag_root": str(graphrag_root),
            "settings_path": str(settings_path),
            "env_file": str(env_file),
            "api_base": env.get("GRAPHRAG_API_BASE"),
            "modes": [GLOBAL_MODE],
            "mode_labels": {GLOBAL_MODE: "global"},
            "total_items": len(items),
            "total_questions": len(items),
            "source_total_questions": source_total_questions,
            "question_ids": [item.id for item in items],
            "request_timeout_seconds": self.request_timeout_seconds,
            "stop_after_timeout_count": self.stop_after_timeout_count,
            "stopped_early": False,
            "stop_reason": None,
        }
        _write_json(run_dir / "run_meta.json", meta)

        timeout_count = 0
        executed_count = 0
        print(f"[{group_name}] run_id={run_id} questions={len(items)}", flush=True)
        for item in items:
            print(f"[{group_name}] start {item.id}", flush=True)
            payload = self._run_item(item, graphrag_root, env)
            _write_json(raw_dir / f"{item.id}.json", payload)
            executed_count += 1
            result = payload["modes"][GLOBAL_MODE]
            status = _result_status(result)
            elapsed = float(result.get("elapsed_seconds") or 0.0)
            print(f"[{group_name}] {item.id} {status} elapsed_s={elapsed:.4f}", flush=True)
            if _is_timeout_like(result.get("error", ""), result.get("error_type", "")):
                timeout_count += 1
            if self.stop_after_timeout_count > 0 and timeout_count >= self.stop_after_timeout_count:
                meta["stopped_early"] = True
                meta["stop_reason"] = (
                    f"timeout_count reached {timeout_count}/{self.stop_after_timeout_count}"
                )
                break

        meta["completed_at"] = datetime.now(timezone.utc).isoformat()
        meta["executed_items"] = executed_count
        meta["timeout_count_during_execution"] = timeout_count
        _write_json(run_dir / "run_meta.json", meta)
        print(f"[{group_name}] completed run_id={run_id}", flush=True)
        return run_dir

    def _run_item(
        self,
        item: QaTestItem,
        graphrag_root: Path,
        env: dict[str, str],
    ) -> dict[str, Any]:
        started_at = time.perf_counter()
        command = [
            self.python_executable,
            "-m",
            "graphrag",
            "query",
            "--root",
            ".",
            "--method",
            "global",
            item.question,
        ]
        try:
            completed = subprocess.run(
                command,
                cwd=graphrag_root,
                env=env,
                text=True,
                capture_output=True,
                timeout=self.request_timeout_seconds,
                check=False,
            )
        except subprocess.TimeoutExpired as exc:
            elapsed_seconds = time.perf_counter() - started_at
            stdout = _coerce_output_text(exc.stdout)
            stderr = _coerce_output_text(exc.stderr)
            mode_payload: dict[str, Any] = {
                "error": f"query timed out after {self.request_timeout_seconds:g} seconds",
                "error_type": "TimeoutExpired",
                "elapsed_seconds": elapsed_seconds,
                "raw": {
                    "command": _safe_command(command),
                    "stdout_tail": _tail_text(stdout),
                    "stderr_tail": _tail_text(stderr),
                },
            }
        else:
            elapsed_seconds = time.perf_counter() - started_at
            stdout = completed.stdout or ""
            stderr = completed.stderr or ""
            if completed.returncode == 0:
                mode_payload = {
                    "answer": extract_cli_answer(stdout),
                    "total_tokens": None,
                    "elapsed_seconds": elapsed_seconds,
                    "raw": {
                        "command": _safe_command(command),
                        "returncode": completed.returncode,
                        "stdout_tail": _tail_text(stdout),
                        "stderr_tail": _tail_text(stderr),
                    },
                }
            else:
                mode_payload = {
                    "error": _tail_text(stderr or stdout),
                    "error_type": "CalledProcessError",
                    "elapsed_seconds": elapsed_seconds,
                    "raw": {
                        "command": _safe_command(command),
                        "returncode": completed.returncode,
                        "stdout_tail": _tail_text(stdout),
                        "stderr_tail": _tail_text(stderr),
                    },
                }

        return {
            "id": item.id,
            "question_id": item.id,
            "category": item.category.value,
            "question": item.question,
            "gold_answer_summary": item.gold_answer_summary,
            "gold_entities": item.gold_entities,
            "gold_text_unit_ids": item.gold_text_unit_ids,
            "must_cite_terms": item.must_cite_terms,
            "negative_terms": item.negative_terms,
            "modes": {GLOBAL_MODE: mode_payload},
        }

    def _selected_groups(self) -> list[str]:
        groups = self.groups or list(GROUP_PRESETS)
        normalized = [group.upper() for group in groups]
        unsupported = [group for group in normalized if group not in GROUP_PRESETS]
        duplicates = _find_duplicates(normalized)
        if unsupported:
            raise ValueError(f"unsupported group(s): {', '.join(unsupported)}")
        if duplicates:
            raise ValueError(f"duplicate group(s): {', '.join(duplicates)}")
        return normalized

    def _validated_index_output_dir(self) -> Path:
        index_output_dir = self.index_output_dir.expanduser().resolve()
        missing = [
            relative_path.as_posix()
            for relative_path in REQUIRED_INDEX_OUTPUT_PATHS
            if not (index_output_dir / relative_path).exists()
        ]
        if missing:
            raise FileNotFoundError(
                "index output dir is missing required GraphRAG output file(s): "
                + ", ".join(missing)
            )
        return index_output_dir


def apply_global_search_config(settings_text: str, config: GlobalSearchConfig) -> str:
    lines = settings_text.splitlines(keepends=True)
    lines = _replace_top_level_scalar(lines, "concurrent_requests", config.concurrent_requests)
    global_start = _find_top_level_block(lines, "global_search")
    if global_start is None:
        raise ValueError("settings.yaml is missing global_search block")

    global_end = _find_block_end(lines, global_start)
    replacements = {
        "max_context_tokens": config.max_context_tokens,
        "data_max_tokens": config.data_max_tokens,
        "map_max_length": config.map_max_length,
        "reduce_max_length": config.reduce_max_length,
    }
    for key, value in replacements.items():
        lines, global_end = _replace_block_scalar(
            lines,
            start=global_start,
            end=global_end,
            key=key,
            value=value,
        )
    return "".join(lines)


def extract_cli_answer(stdout: str) -> str:
    text = ANSI_PATTERN.sub("", stdout).strip()
    for marker in SUCCESS_MARKERS:
        if marker in text:
            return text.split(marker, 1)[1].strip()

    answer_lines: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            if answer_lines:
                answer_lines.append("")
            continue
        if line.startswith(("INFO:", "WARNING:", "ERROR:", "SUCCESS:", "🚀", "⠋", "⠙", "⠹")):
            continue
        answer_lines.append(raw_line)
    return "\n".join(answer_lines).strip() or text


def _replace_top_level_scalar(lines: list[str], key: str, value: int) -> list[str]:
    pattern = re.compile(rf"^({re.escape(key)}:\s*).*$")
    for index, line in enumerate(lines):
        if pattern.match(line):
            newline = "\n" if line.endswith("\n") else ""
            lines[index] = f"{key}: {value}{newline}"
            return lines
    raise ValueError(f"settings.yaml is missing top-level key: {key}")


def _find_top_level_block(lines: list[str], key: str) -> int | None:
    pattern = re.compile(rf"^{re.escape(key)}:\s*(?:#.*)?$")
    for index, line in enumerate(lines):
        if pattern.match(line):
            return index
    return None


def _find_block_end(lines: list[str], start: int) -> int:
    for index in range(start + 1, len(lines)):
        line = lines[index]
        if line.strip() and not line.startswith((" ", "#")):
            return index
    return len(lines)


def _replace_block_scalar(
    lines: list[str],
    *,
    start: int,
    end: int,
    key: str,
    value: int,
) -> tuple[list[str], int]:
    pattern = re.compile(rf"^(\s+{re.escape(key)}:\s*).*$")
    for index in range(start + 1, end):
        match = pattern.match(lines[index])
        if match:
            newline = "\n" if lines[index].endswith("\n") else ""
            lines[index] = f"{match.group(1)}{value}{newline}"
            return lines, end

    insert_at = start + 1
    lines.insert(insert_at, f"  {key}: {value}\n")
    return lines, end + 1


def _load_test_items(path: Path) -> list[QaTestItem]:
    items: list[QaTestItem] = []
    with path.open("r", encoding="utf-8") as handle:
        for raw_line in handle:
            raw_line = raw_line.strip()
            if raw_line:
                items.append(QaTestItem.model_validate_json(raw_line))
    return items


def _select_items(source_items: list[QaTestItem], question_ids: list[str] | None) -> list[QaTestItem]:
    if question_ids is None:
        return source_items
    duplicates = _find_duplicates(question_ids)
    if duplicates:
        raise ValueError(f"duplicate question id(s): {', '.join(duplicates)}")
    items_by_id = {item.id: item for item in source_items}
    missing = [question_id for question_id in question_ids if question_id not in items_by_id]
    if missing:
        raise ValueError(f"unknown question id(s): {', '.join(missing)}")
    return [items_by_id[question_id] for question_id in question_ids]


def _find_duplicates(values: list[str]) -> list[str]:
    duplicates: list[str] = []
    seen: set[str] = set()
    for value in values:
        if value in seen and value not in duplicates:
            duplicates.append(value)
            continue
        seen.add(value)
    return duplicates


def _build_subprocess_env(env_file: Path, index_output_dir: Path) -> dict[str, str]:
    env = os.environ.copy()
    env.update(_load_dotenv(env_file))
    env["GRAPHRAG_OUTPUT_DIR"] = str(index_output_dir)
    env["GRAPHRAG_STORAGE_DIR"] = str(index_output_dir)
    env["GRAPHRAG_LANCEDB_URI"] = str(index_output_dir / "lancedb")
    env["NO_PROXY"] = _append_no_proxy(env.get("NO_PROXY", ""))
    env["no_proxy"] = _append_no_proxy(env.get("no_proxy", ""))
    return env


def _load_dotenv(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export ") :].strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'\"")
        if key:
            values[key] = value
    return values


def _append_no_proxy(current: str) -> str:
    required = ["127.0.0.1", "localhost", "::1"]
    parts = [part.strip() for part in current.split(",") if part.strip()]
    for value in required:
        if value not in parts:
            parts.append(value)
    return ",".join(parts)


def _build_run_id(prefix: str, group_name: str) -> str:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return f"{prefix}-{group_name.lower()}-{timestamp}"


def _safe_command(command: list[str]) -> list[str]:
    return list(command[:-1]) + ["<question>"]


def _coerce_output_text(value: bytes | str | None) -> str:
    if value is None:
        return ""
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return value


def _tail_text(text: str, max_chars: int = 8000) -> str:
    return text if len(text) <= max_chars else text[-max_chars:]


def _is_timeout_like(error: Any, error_type: Any) -> bool:
    text = f"{error_type} {error}".casefold()
    return bool(TIMEOUT_ERROR_PATTERN.search(text))


def _result_status(result: dict[str, Any]) -> str:
    if result.get("error"):
        return f"error:{result.get('error_type', 'UnknownError')}"
    return f"ok:chars={len(str(result.get('answer') or ''))}"


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run GraphRAG global-search tuning via CLI.")
    parser.add_argument("--graphrag-root", type=Path, default=Path("graphrag_pipeline"))
    parser.add_argument("--test-set", type=Path, required=True)
    parser.add_argument("--index-output-dir", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--settings-path", type=Path, default=None)
    parser.add_argument("--env-file", type=Path, default=None)
    parser.add_argument("--groups", nargs="+", default=None)
    parser.add_argument("--question-ids", nargs="+", default=None)
    parser.add_argument("--run-id-prefix", default="global-tuning")
    parser.add_argument("--python-executable", default=sys.executable)
    parser.add_argument("--request-timeout-seconds", type=float, default=240.0)
    parser.add_argument("--stop-after-timeout-count", type=int, default=2)
    args = parser.parse_args(argv)

    runner = GlobalCliTuningRunner(
        graphrag_root=args.graphrag_root,
        test_set_path=args.test_set,
        index_output_dir=args.index_output_dir,
        output_root=args.output_root,
        settings_path=args.settings_path,
        env_file=args.env_file,
        groups=args.groups,
        question_ids=args.question_ids,
        run_id_prefix=args.run_id_prefix,
        python_executable=args.python_executable,
        request_timeout_seconds=args.request_timeout_seconds,
        stop_after_timeout_count=args.stop_after_timeout_count,
    )
    run_dirs = runner.run()
    print(json.dumps([str(path) for path in run_dirs], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
