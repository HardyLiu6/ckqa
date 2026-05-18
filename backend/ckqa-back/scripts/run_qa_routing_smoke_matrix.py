#!/usr/bin/env python3
"""Run a low-cost live smoke matrix for CKQA smart QA routing.

The default path performs real login, real Java /api/v1 routing requests, and
optional hybrid warmup/readiness checks. It intentionally does not submit QA
messages, because that can send course snippets to the configured model
provider during hybrid generation. Use --execute-qa together with the explicit
acknowledgement flag when a generation smoke is required.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_MATRIX = [
    {
        "id": "basic-definition",
        "question": "什么是死锁？",
        "acceptableModes": ["basic"],
        "betaHybridEnabled": False,
        "hasConversationContext": False,
    },
    {
        "id": "local-material",
        "question": "教材中银行家算法的步骤是怎样的？",
        "acceptableModes": ["local"],
        "betaHybridEnabled": False,
        "hasConversationContext": False,
    },
    {
        "id": "global-summary",
        "question": "请综述进程管理这一章的知识体系。",
        "acceptableModes": ["global"],
        "betaHybridEnabled": False,
        "hasConversationContext": False,
    },
    {
        "id": "drift-transfer",
        "question": "页面置换算法的思想能迁移到缓存淘汰吗？",
        "acceptableModes": ["drift"],
        "betaHybridEnabled": False,
        "hasConversationContext": False,
    },
    {
        "id": "hybrid-beta",
        "question": "请综合比较死锁和饥饿的区别，并引用课程来源。",
        "acceptableModes": ["hybrid_v0"],
        "betaHybridEnabled": True,
        "hasConversationContext": False,
        "warmupHybrid": True,
    },
    {
        "id": "hybrid-gated-fallback",
        "question": "请综合比较死锁和饥饿的区别，并引用课程来源。",
        "acceptableModes": ["local"],
        "betaHybridEnabled": False,
        "hasConversationContext": False,
    },
    {
        "id": "contextual-follow-up",
        "question": "它和前面那个概念有什么区别？",
        "acceptableModes": ["drift", "local"],
        "betaHybridEnabled": False,
        "hasConversationContext": True,
    },
]


TERMINAL_TASK_STATUSES = {"success", "failed", "stale"}


@dataclass
class ApiClient:
    base_url: str
    token: str | None = None
    timeout_seconds: float = 20.0

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        url = f"{self.base_url}{path}"
        body = None
        headers = {"Accept": "application/json"}
        if payload is not None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                return unwrap_response(json.loads(response.read().decode("utf-8")))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {url} failed with HTTP {exc.code}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc


def unwrap_response(raw: Any) -> Any:
    if isinstance(raw, dict) and {"code", "data"}.issubset(raw.keys()):
        if raw.get("code") != 200:
            raise RuntimeError(f"API returned code={raw.get('code')} message={raw.get('message')}")
        return raw.get("data")
    return raw


def load_env_file(path: str | None) -> None:
    if not path:
        return
    env_path = Path(path)
    if not env_path.exists():
        raise FileNotFoundError(f"env file not found: {env_path}")
    for line in env_path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if not text or text.startswith("#") or "=" not in text:
            continue
        key, value = text.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def normalize_base_url(value: str) -> str:
    base = value.rstrip("/")
    return base if base.endswith("/api/v1") else f"{base}/api/v1"


def load_matrix(case_file: str | None, limit: int | None) -> list[dict[str, Any]]:
    if not case_file:
        cases = list(DEFAULT_MATRIX)
    else:
        cases = []
        for line in Path(case_file).read_text(encoding="utf-8").splitlines():
            if line.strip():
                cases.append(json.loads(line))
    return cases[:limit] if limit else cases


def require(value: str | None, label: str) -> str:
    if not value:
        raise SystemExit(f"missing required {label}")
    return value


def login(client: ApiClient, username: str, password: str) -> tuple[str, dict[str, Any]]:
    data = client.request("POST", "/auth/student/login", {"username": username, "password": password})
    token = data.get("accessToken")
    if not token:
        raise RuntimeError("login response did not contain accessToken")
    return token, data.get("user") or {}


def recommend_case(
    client: ApiClient,
    case: dict[str, Any],
    course_id: str | None,
    knowledge_base_id: int | None,
    session_id: int | None,
) -> dict[str, Any]:
    payload = {
        "question": case["question"],
        "betaHybridEnabled": bool(case.get("betaHybridEnabled")),
        "hasConversationContext": bool(case.get("hasConversationContext")),
    }
    if session_id:
        payload["sessionId"] = session_id
    else:
        if course_id:
            payload["courseId"] = course_id
        if knowledge_base_id:
            payload["knowledgeBaseId"] = knowledge_base_id
    recommendation = client.request("POST", "/qa-routing/recommend", payload)
    actual = recommendation.get("recommendedMode")
    acceptable = set(case.get("acceptableModes") or [])
    return {
        "id": case.get("id"),
        "question": case.get("question"),
        "actualMode": actual,
        "acceptableModes": list(acceptable),
        "passed": actual in acceptable,
        "confidence": recommendation.get("confidence"),
        "reasons": recommendation.get("reasons") or [],
        "routeScores": recommendation.get("routeScores") or {},
        "raw": recommendation,
    }


def warmup_hybrid(client: ApiClient, course_id: str, knowledge_base_id: int) -> dict[str, Any]:
    return client.request(
        "POST",
        "/qa-sessions/hybrid-warmup",
        {"courseId": course_id, "knowledgeBaseId": knowledge_base_id},
    )


def create_session(client: ApiClient, user: dict[str, Any], course_id: str, knowledge_base_id: int) -> dict[str, Any]:
    return client.request(
        "POST",
        "/qa-sessions",
        {
            "userId": user.get("id"),
            "courseId": course_id,
            "knowledgeBaseId": knowledge_base_id,
            "sessionType": "formal",
            "title": "Routing smoke matrix",
        },
    )


def execute_case(client: ApiClient, session_id: int, case: dict[str, Any], mode: str, timeout_seconds: int) -> dict[str, Any]:
    submission = client.request(
        "POST",
        f"/qa-sessions/{session_id}/messages",
        {"mode": mode, "content": case["question"]},
    )
    task_id = submission.get("taskId")
    if not task_id:
        raise RuntimeError(f"QA submission for {case.get('id')} did not return taskId")

    deadline = time.time() + timeout_seconds
    detail = None
    while time.time() < deadline:
        detail = client.request("GET", f"/qa-sessions/{session_id}/tasks/{task_id}")
        if detail.get("taskStatus") in TERMINAL_TASK_STATUSES:
            break
        interval = detail.get("recommendedPollingIntervalSeconds") or submission.get("recommendedPollingIntervalSeconds") or 2
        time.sleep(max(1, min(int(interval), 5)))
    return {
        "taskId": task_id,
        "taskStatus": detail.get("taskStatus") if detail else "timeout",
        "assistantPresent": bool((detail or {}).get("assistantMessage")),
        "sourceCount": len(((detail or {}).get("assistantMessage") or {}).get("sources") or []),
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", help="Optional dotenv file. Existing environment variables win.")
    parser.add_argument("--base-url", default=None)
    parser.add_argument("--student-username", default=os.getenv("CKQA_STUDENT_USERNAME"))
    parser.add_argument("--student-password", default=os.getenv("CKQA_STUDENT_PASSWORD"))
    parser.add_argument("--course-id", default=os.getenv("CKQA_SMOKE_COURSE_ID"))
    parser.add_argument("--knowledge-base-id", type=int, default=env_int("CKQA_SMOKE_KNOWLEDGE_BASE_ID"))
    parser.add_argument("--session-id", type=int, default=env_int("CKQA_SMOKE_SESSION_ID"))
    parser.add_argument("--case-file", help="Optional JSONL route matrix. Defaults to built-in smoke cases.")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--skip-hybrid-warmup", action="store_true")
    parser.add_argument("--execute-qa", action="store_true", help="Submit QA messages and poll tasks. May call external model providers.")
    parser.add_argument("--i-understand-external-model-calls", action="store_true")
    parser.add_argument("--qa-timeout-seconds", type=int, default=180)
    parser.add_argument("--json-out", help="Write full JSON report to this path.")
    return parser.parse_args(argv)


def env_int(name: str) -> int | None:
    value = os.getenv(name)
    if not value:
        return None
    return int(value)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    load_env_file(args.env_file)

    if args.execute_qa and not args.i_understand_external_model_calls:
        raise SystemExit("--execute-qa may send course snippets to the configured model provider; add --i-understand-external-model-calls to proceed")

    username = require(args.student_username or os.getenv("CKQA_STUDENT_USERNAME"), "student username")
    password = require(args.student_password or os.getenv("CKQA_STUDENT_PASSWORD"), "student password")
    course_id = args.course_id or os.getenv("CKQA_SMOKE_COURSE_ID")
    knowledge_base_id = args.knowledge_base_id or env_int("CKQA_SMOKE_KNOWLEDGE_BASE_ID")
    if not args.session_id and (not course_id or not knowledge_base_id):
        raise SystemExit("course id and knowledge base id are required when session id is not provided")

    client = ApiClient(normalize_base_url(args.base_url or os.getenv("CKQA_API_BASE_URL", "http://127.0.0.1:18081/api/v1")))
    token, user = login(client, username, password)
    client.token = token

    cases = load_matrix(args.case_file, args.limit)
    report: dict[str, Any] = {
        "baseUrl": client.base_url,
        "student": {"id": user.get("id"), "username": user.get("username"), "userCode": user.get("userCode")},
        "courseId": course_id,
        "knowledgeBaseId": knowledge_base_id,
        "sessionId": args.session_id,
        "qaExecuted": bool(args.execute_qa),
        "warmup": None,
        "cases": [],
    }

    failures = []
    if not args.skip_hybrid_warmup and any(case.get("warmupHybrid") for case in cases):
        if not course_id or not knowledge_base_id:
            raise SystemExit("course id and knowledge base id are required for hybrid warmup")
        report["warmup"] = warmup_hybrid(client, course_id, knowledge_base_id)
        if not report["warmup"].get("ready"):
            failures.append(
                "hybrid_warmup: "
                f"status={report['warmup'].get('status')} missing={report['warmup'].get('missing')}"
            )

    qa_session_id = args.session_id
    if args.execute_qa and not qa_session_id:
        if not course_id or not knowledge_base_id:
            raise SystemExit("course id and knowledge base id are required to create QA session")
        qa_session_id = create_session(client, user, course_id, knowledge_base_id).get("id")
        report["sessionId"] = qa_session_id

    for case in cases:
        result = recommend_case(client, case, course_id, knowledge_base_id, args.session_id)
        if args.execute_qa:
            result["qa"] = execute_case(client, int(qa_session_id), case, result["actualMode"], args.qa_timeout_seconds)
            if result["qa"].get("taskStatus") != "success" or not result["qa"].get("assistantPresent"):
                failures.append(
                    f"{result['id']}: qa taskStatus={result['qa'].get('taskStatus')} "
                    f"assistantPresent={result['qa'].get('assistantPresent')}"
                )
        if not result["passed"]:
            failures.append(f"{result['id']}: actual={result['actualMode']} acceptable={result['acceptableModes']}")
        report["cases"].append(result)

    print_summary(report, failures)
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return 1 if failures else 0


def print_summary(report: dict[str, Any], failures: list[str]) -> None:
    print("CKQA QA routing smoke matrix")
    print(f"base_url={report['baseUrl']}")
    print(f"student={report['student'].get('username') or report['student'].get('userCode')}")
    if report.get("warmup") is not None:
        warmup = report["warmup"]
        print(f"hybrid_warmup status={warmup.get('status')} ready={warmup.get('ready')} cached={warmup.get('cached')}")
    for item in report["cases"]:
        marker = "OK" if item["passed"] else "FAIL"
        print(f"{marker} {item['id']}: {item['actualMode']} confidence={item.get('confidence')} reasons={','.join(item.get('reasons') or [])}")
        if item.get("qa"):
            qa = item["qa"]
            print(f"  qa task={qa.get('taskId')} status={qa.get('taskStatus')} assistant={qa.get('assistantPresent')} sources={qa.get('sourceCount')}")
    if failures:
        print("failures:")
        for failure in failures:
            print(f"  - {failure}")


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
