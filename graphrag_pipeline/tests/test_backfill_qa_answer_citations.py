from __future__ import annotations

import sys
from pathlib import Path

import pandas as pd


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from backfill_qa_answer_citations import apply_backfill_updates, build_backfill_plan, fetch_candidate_rows, resolve_output_dir


class _FakeCursor:
    def __init__(self) -> None:
        self.executed: list[tuple[str, tuple[object, ...]]] = []

    def execute(self, sql: str, params: tuple[object, ...]) -> int:
        self.executed.append((sql, params))
        return 1

    def __enter__(self) -> "_FakeCursor":
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None


class _FakeConnection:
    def __init__(self) -> None:
        self.cursor_instance = _FakeCursor()
        self.committed = False

    def cursor(self) -> _FakeCursor:
        return self.cursor_instance

    def commit(self) -> None:
        self.committed = True


class _FakeFetchCursor(_FakeCursor):
    def fetchall(self) -> list[dict[str, object]]:
        return []


class _FakeFetchConnection:
    def __init__(self) -> None:
        self.cursor_instance = _FakeFetchCursor()

    def cursor(self) -> _FakeFetchCursor:
        return self.cursor_instance


def test_build_backfill_plan_resolves_old_data_sources(tmp_path):
    output_root = tmp_path / "output"
    output_dir = output_root / "run-1"
    output_dir.mkdir(parents=True)
    pd.DataFrame(
        [
            {
                "id": "text-unit-156-full-id",
                "human_readable_id": 156,
                "text": (
                    "document_type: textbook. chapter: 第三章 处理机调度与死锁. "
                    "section: 3.8 死锁的检测与解除. subsection: 3.8.1 死锁的检测. "
                    "heading_level: 3. heading_path_text: 第三章 处理机调度与死锁 > 3.8 死锁的检测与解除 > 3.8.1 死锁的检测. "
                    "page_start: 123. page_end: 125. section_level: 3. "
                    "source_file: 计算机操作系统教材. course_id: os. "
                    "资源分配图是一种用来描述系统中资源分配状态的有向图。"
                ),
                "document_id": "doc-os",
            }
        ]
    ).to_parquet(output_dir / "text_units.parquet")

    plan = build_backfill_plan(
        [
            {
                "message_id": 42,
                "session_id": 10,
                "sequence_no": 2,
                "content": "资源分配图可用于判断死锁 [Data: Sources (156)]。",
                "index_run_id": 9,
                "storage_uri": "run-1",
            }
        ],
        output_root=output_root,
        build_runs_root=tmp_path / "runtime" / "kb-build-runs",
    )

    assert len(plan) == 1
    item = plan[0]
    assert item.message_id == 42
    assert item.source_count == 1
    assert item.output_dir == output_dir.resolve()
    assert item.warning is None
    assert item.old_content == "资源分配图可用于判断死锁 [Data: Sources (156)]。"
    assert item.new_content.startswith("资源分配图可用于判断死锁 [来源 1]。")
    assert "参考来源：" in item.new_content
    assert "计算机操作系统教材" in item.new_content


def test_build_backfill_plan_uses_generic_marker_when_artifact_missing(tmp_path):
    plan = build_backfill_plan(
        [
            {
                "message_id": 43,
                "session_id": 10,
                "sequence_no": 4,
                "content": "找不到索引文件时也要去掉编号 [Data: Sources (999)]。",
                "index_run_id": 9,
                "storage_uri": "missing-run",
            }
        ],
        output_root=tmp_path / "output",
        build_runs_root=tmp_path / "runtime" / "kb-build-runs",
    )

    assert len(plan) == 1
    assert plan[0].source_count == 0
    assert plan[0].output_dir is None
    assert plan[0].warning == "missing_output_dir"
    assert plan[0].new_content == "找不到索引文件时也要去掉编号 [已参考课程知识库]。"


def test_resolve_output_dir_falls_back_to_worktree_output(monkeypatch, tmp_path):
    run_dir = tmp_path / "output" / "run-from-worktree"
    run_dir.mkdir(parents=True)
    monkeypatch.setattr("backfill_qa_answer_citations.PROJECT_ROOT", tmp_path)

    resolved = resolve_output_dir(
        "run-from-worktree",
        output_root=tmp_path / "elsewhere",
        build_runs_root=None,
    )

    assert resolved == run_dir.resolve()


def test_apply_backfill_updates_guard_on_original_content():
    connection = _FakeConnection()
    plan = build_backfill_plan(
        [
            {
                "message_id": 44,
                "session_id": 11,
                "sequence_no": 2,
                "content": "旧答案 [Data: Sources (999)]。",
                "index_run_id": None,
                "storage_uri": None,
            }
        ],
        output_root=Path("/unused"),
        build_runs_root=Path("/unused"),
    )

    updated = apply_backfill_updates(connection, plan)

    assert updated == 1
    assert connection.committed is True
    sql, params = connection.cursor_instance.executed[0]
    assert "content_text = %s" in sql
    assert "WHERE id = %s AND content = %s" in sql
    assert params == (
        "旧答案 [已参考课程知识库]。",
        "旧答案 [已参考课程知识库]。",
        44,
        "旧答案 [Data: Sources (999)]。",
    )


def test_fetch_candidate_rows_parameterizes_data_marker():
    connection = _FakeFetchConnection()

    rows = fetch_candidate_rows(connection, limit=20, message_ids=[], session_id=None)

    assert rows == []
    sql, params = connection.cursor_instance.executed[0]
    assert "m.content LIKE %s" in sql
    assert params == ("%[Data:%", 20)
