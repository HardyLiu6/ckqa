#!/usr/bin/env python3
"""Run a real student QA smoke for Local history memory.

This script intentionally validates only fields exposed by the student API:
task success, assistant message presence, and memoryApplied/memorySourceCount.
It does not assert queryEngineStrategy, which is reserved for admin/ops
diagnostics.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


TERMINAL_STATUSES = {"success", "failed", "stale"}


@dataclass
class ApiClient:
    base_url: str
    token: str | None = None
    timeout_seconds: float = 30.0

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        url = f"{self.base_url}{path}"
        headers = {"Accept": "application/json"}
        data = None
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(request, timeout=self.timeout_seconds) as response:
                return unwrap_response(json.loads(response.read().decode("utf-8")))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {url} failed with HTTP {exc.code}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc

    def get(self, path: str) -> Any:
        return self.request("GET", path)

    def post(self, path: str, payload: dict[str, Any]) -> Any:
        return self.request("POST", path, payload)

    def put(self, path: str, payload: dict[str, Any]) -> Any:
        return self.request("PUT", path, payload)


def unwrap_response(raw: Any) -> Any:
    if isinstance(raw, dict) and {"code", "data"}.issubset(raw.keys()):
        if raw.get("code") != 200:
            raise RuntimeError(f"API returned code={raw.get('code')} message={raw.get('message')}")
        return raw.get("data")
    return raw


def normalize_base_url(value: str) -> str:
    base = value.rstrip("/")
    return base if base.endswith("/api/v1") else f"{base}/api/v1"


def load_env_file(path: str | None) -> None:
    if not path:
        return
    if not os.path.exists(path):
        raise FileNotFoundError(f"env file not found: {path}")
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            text = line.strip()
            if not text or text.startswith("#") or "=" not in text:
                continue
            key, value = text.split("=", 1)
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = value


def encode_params(params: dict[str, Any]) -> str:
    return urllib.parse.urlencode({key: value for key, value in params.items() if value is not None})


def page_items(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ("items", "records", "list"):
            items = value.get(key)
            if isinstance(items, list):
                return items
    return []


def choose_course(client: ApiClient, course_id: str | None) -> dict[str, Any]:
    if course_id:
        return client.get(f"/courses/{urllib.parse.quote(course_id)}")
    data = client.get("/courses?page=1&size=50")
    courses = page_items(data)
    for course in courses:
        if course.get("courseId") or course.get("id"):
            return course
    raise RuntimeError("no readable course found for student")


def choose_knowledge_base(client: ApiClient, course_id: str, knowledge_base_id: int | None) -> dict[str, Any]:
    items = client.get(f"/courses/{urllib.parse.quote(course_id)}/knowledge-bases")
    candidates = [item for item in items if item.get("activeIndexRunId")]
    if knowledge_base_id is not None:
        for item in candidates:
            if int(item.get("id")) == knowledge_base_id:
                return item
        raise RuntimeError(f"knowledge base {knowledge_base_id} is not active/readable for course {course_id}")
    if not candidates:
        raise RuntimeError(f"course {course_id} has no knowledge base with activeIndexRunId")
    return candidates[0]


def poll_task(client: ApiClient, session_id: int, task_id: int, timeout_seconds: int) -> dict[str, Any]:
    deadline = time.time() + timeout_seconds
    latest: dict[str, Any] | None = None
    while time.time() < deadline:
        latest = client.get(f"/qa-sessions/{session_id}/tasks/{task_id}")
        if latest.get("taskStatus") in TERMINAL_STATUSES:
            return latest
        time.sleep(max(1, int(latest.get("recommendedPollingIntervalSeconds") or 2)))
    raise TimeoutError(f"task {task_id} did not finish within {timeout_seconds}s; latest={latest}")


def send_and_wait(client: ApiClient, session_id: int, question: str, timeout_seconds: int) -> tuple[dict[str, Any], dict[str, Any]]:
    submission = client.post(
        f"/qa-sessions/{session_id}/messages",
        {"mode": "local", "content": question, "memoryPolicy": "default"},
    )
    task_id = int(submission["taskId"])
    detail = poll_task(client, session_id, task_id, timeout_seconds)
    if detail.get("taskStatus") != "success":
        raise RuntimeError(f"task {task_id} ended as {detail.get('taskStatus')}: {detail.get('errorMessage')}")
    if not detail.get("assistantMessage"):
        raise RuntimeError(f"task {task_id} succeeded without assistantMessage")
    return submission, detail


def compact_task(detail: dict[str, Any]) -> dict[str, Any]:
    assistant = detail.get("assistantMessage") or {}
    content = assistant.get("content") or assistant.get("contentText") or ""
    return {
        "taskId": detail.get("taskId"),
        "taskStatus": detail.get("taskStatus"),
        "memoryApplied": detail.get("memoryApplied"),
        "memoryStrategy": detail.get("memoryStrategy"),
        "memorySourceCount": detail.get("memorySourceCount"),
        "assistantMessageId": assistant.get("id"),
        "answerPreview": content[:260],
    }


def run(args: argparse.Namespace) -> dict[str, Any]:
    load_env_file(args.env_file)
    base_url = normalize_base_url(args.base_url or os.getenv("CKQA_API_BASE_URL", "http://127.0.0.1:18081"))
    username = args.username or os.getenv("CKQA_STUDENT_USERNAME", "student.zhouzh")
    password = args.password or os.getenv("CKQA_STUDENT_PASSWORD")
    if not password:
        raise RuntimeError("student password is required; pass --password or set CKQA_STUDENT_PASSWORD")

    anonymous = ApiClient(base_url=base_url, timeout_seconds=args.request_timeout)
    login = anonymous.post("/auth/student/login", {"username": username, "password": password})
    token = login["accessToken"]
    user = login["user"]
    client = ApiClient(base_url=base_url, token=token, timeout_seconds=args.request_timeout)

    course = choose_course(client, args.course_id)
    course_id = course.get("courseId") or course.get("id")
    knowledge_base = choose_knowledge_base(client, course_id, args.knowledge_base_id)
    kb_id = int(knowledge_base["id"])

    preference = client.put(
        "/qa-memory/preferences",
        {
            "courseId": course_id,
            "knowledgeBaseId": kb_id,
            "enabled": True,
        },
    )
    session = client.post(
        "/qa-sessions",
        {
            "userId": user["id"],
            "courseId": course_id,
            "knowledgeBaseId": kb_id,
            "sessionType": "formal",
            "title": args.title,
        },
    )
    session_id = int(session["id"])

    first_submission, first_detail = send_and_wait(client, session_id, args.first_question, args.task_timeout)
    second_submission, second_detail = send_and_wait(client, session_id, args.second_question, args.task_timeout)

    if not second_detail.get("memoryApplied"):
        raise RuntimeError("second task succeeded but memoryApplied is not true")
    if int(second_detail.get("memorySourceCount") or 0) <= 0:
        raise RuntimeError("second task succeeded but memorySourceCount is empty")

    return {
        "baseUrl": base_url,
        "user": {"id": user.get("id"), "username": user.get("username"), "userCode": user.get("userCode")},
        "course": {"id": course_id, "name": course.get("name") or course.get("courseName")},
        "knowledgeBase": {"id": kb_id, "activeIndexRunId": knowledge_base.get("activeIndexRunId")},
        "memoryPreference": preference,
        "session": {"id": session_id, "indexRunId": session.get("indexRunId")},
        "first": {
            "submission": {
                "taskId": first_submission.get("taskId"),
                "memoryApplied": first_submission.get("memoryApplied"),
                "contextStrategy": first_submission.get("contextStrategy"),
            },
            "detail": compact_task(first_detail),
        },
        "second": {
            "submission": {
                "taskId": second_submission.get("taskId"),
                "memoryApplied": second_submission.get("memoryApplied"),
                "memorySourceCount": second_submission.get("memorySourceCount"),
                "contextStrategy": second_submission.get("contextStrategy"),
            },
            "detail": compact_task(second_detail),
        },
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run real student local-history QA smoke.")
    parser.add_argument("--base-url", help="Java API base URL. Defaults to CKQA_API_BASE_URL or http://127.0.0.1:18081.")
    parser.add_argument("--env-file", help="Optional env file to load before running.")
    parser.add_argument("--username", help="Student username. Defaults to CKQA_STUDENT_USERNAME or student.zhouzh.")
    parser.add_argument("--password", help="Student password. Defaults to CKQA_STUDENT_PASSWORD.")
    parser.add_argument("--course-id", help="Optional course id. Defaults to the first readable course.")
    parser.add_argument("--knowledge-base-id", type=int, help="Optional knowledge base id. Defaults to the first active KB.")
    parser.add_argument("--first-question", default="什么是死锁？")
    parser.add_argument("--second-question", default="它和资源分配图有什么关系？")
    parser.add_argument("--title", default="Local history smoke")
    parser.add_argument("--task-timeout", type=int, default=180)
    parser.add_argument("--request-timeout", type=float, default=30.0)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        report = run(args)
    except Exception as exc:  # noqa: BLE001 - CLI smoke should print the terminal failure.
        print(json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1
    print(json.dumps({"ok": True, **report}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
