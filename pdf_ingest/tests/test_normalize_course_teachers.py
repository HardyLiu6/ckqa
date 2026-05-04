from pathlib import Path
import sys

import pytest


SCRIPT_DIR = Path(__file__).resolve().parents[2] / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))

from normalize_course_teachers import (  # noqa: E402
    INITIAL_TEACHER_REASON,
    CourseRecord,
    MigrationError,
    UserRecord,
    apply_memberships,
    build_insert_params,
    build_migration_report,
    load_mapping,
    write_report,
)


def test_mapping_csv_requires_course_id_and_teacher_user_code(tmp_path):
    mapping_path = tmp_path / "mapping.csv"
    mapping_path.write_text("course_id,teacher_code\nos,TCH2026001\n", encoding="utf-8")

    with pytest.raises(MigrationError, match="teacher_user_code"):
        load_mapping(mapping_path)


def test_missing_mapping_for_legacy_course_is_dry_run_error():
    rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={},
        users_by_code={},
    )

    assert rows[0].action == "error"
    assert rows[0].reason == "mapping_missing"
    assert rows[0].actor_status == "actor_unavailable"


def test_mapping_to_unknown_teacher_reports_error():
    rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={"os": "TCH404"},
        users_by_code={},
    )

    assert rows[0].action == "error"
    assert rows[0].teacher_user_code == "TCH404"
    assert rows[0].reason == "teacher_not_found"


def test_mapping_to_non_active_teacher_reports_error():
    rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={"os": "TCH2026001"},
        users_by_code={
            "TCH2026001": UserRecord(8, "TCH2026001", "disabled", has_teacher_role=True),
        },
    )

    assert rows[0].action == "error"
    assert rows[0].teacher_user_id == 8
    assert rows[0].reason == "teacher_not_active"


def test_mapping_to_active_non_teacher_reports_error():
    rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={"os": "ADM2026001"},
        users_by_code={
            "ADM2026001": UserRecord(3, "ADM2026001", "active", has_teacher_role=False),
        },
    )

    assert rows[0].action == "error"
    assert rows[0].teacher_user_id == 3
    assert rows[0].reason == "teacher_role_missing"


def test_existing_active_teacher_membership_is_skipped_without_mapping():
    rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids={"os"},
        mapping={},
        users_by_code={},
    )

    assert rows[0].action == "skip"
    assert rows[0].reason == "active_teacher_exists"


def test_fallback_teacher_only_applies_when_explicit():
    teacher = UserRecord(8, "TCH2026001", "active", has_teacher_role=True)

    missing_rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={},
        users_by_code={"TCH2026001": teacher},
    )
    fallback_rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={},
        users_by_code={"TCH2026001": teacher},
        fallback_teacher_user_code="TCH2026001",
    )

    assert missing_rows[0].reason == "mapping_missing"
    assert fallback_rows[0].action == "insert"
    assert fallback_rows[0].teacher_user_id == 8


def test_granted_by_unknown_user_aborts_plan():
    with pytest.raises(MigrationError, match="授权人不存在"):
        build_migration_report(
            courses=[CourseRecord("os")],
            active_teacher_course_ids=set(),
            mapping={},
            users_by_code={},
            granted_by_user_code="ADM404",
        )


def test_granted_by_non_active_user_aborts_plan():
    with pytest.raises(MigrationError, match="授权人不是 active 状态"):
        build_migration_report(
            courses=[CourseRecord("os")],
            active_teacher_course_ids=set(),
            mapping={},
            users_by_code={
                "ADM2026001": UserRecord(3, "ADM2026001", "disabled"),
            },
            granted_by_user_code="ADM2026001",
        )


def test_missing_granted_by_reports_actor_unavailable():
    rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={"os": "TCH2026001"},
        users_by_code={
            "TCH2026001": UserRecord(8, "TCH2026001", "active", has_teacher_role=True),
        },
    )

    assert rows[0].actor_status == "actor_unavailable"


def test_apply_mode_insert_params_include_initial_teacher_reason():
    rows, actor = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={"os": "TCH2026001"},
        users_by_code={
            "TCH2026001": UserRecord(8, "TCH2026001", "active", has_teacher_role=True),
            "ADM2026001": UserRecord(3, "ADM2026001", "active"),
        },
        granted_by_user_code="ADM2026001",
    )

    params = build_insert_params(rows[0], actor.user_id)

    assert params["user_id"] == 8
    assert params["course_id"] == "os"
    assert params["granted_by_user_id"] == 3
    assert params["change_reason"] == INITIAL_TEACHER_REASON


def test_apply_memberships_executes_insert_with_expected_params():
    rows, actor = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={"os": "TCH2026001"},
        users_by_code={
            "TCH2026001": UserRecord(8, "TCH2026001", "active", has_teacher_role=True),
            "ADM2026001": UserRecord(3, "ADM2026001", "active"),
        },
        granted_by_user_code="ADM2026001",
    )
    cursor = RecordingCursor()

    inserted = apply_memberships(cursor, rows, actor.user_id)

    assert inserted == 1
    assert "INSERT INTO course_memberships" in cursor.calls[0][0]
    assert cursor.calls[0][1]["change_reason"] == INITIAL_TEACHER_REASON


def test_write_report_outputs_required_fields(capsys):
    rows, _ = build_migration_report(
        courses=[CourseRecord("os")],
        active_teacher_course_ids=set(),
        mapping={"os": "TCH2026001"},
        users_by_code={
            "TCH2026001": UserRecord(8, "TCH2026001", "active", has_teacher_role=True),
        },
    )

    write_report(rows, sys.stdout)
    output = capsys.readouterr().out

    assert "course_id,action,teacher_user_code,teacher_user_id,reason,actor_status" in output
    assert "os,insert,TCH2026001,8,planned,actor_unavailable" in output


class RecordingCursor:
    def __init__(self):
        self.calls = []

    def execute(self, sql, params=None):
        self.calls.append((sql, params))
