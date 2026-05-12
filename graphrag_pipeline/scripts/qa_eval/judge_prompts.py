from __future__ import annotations

from textwrap import dedent


SEMANTIC_PROMPT = dedent(
    """
    你是严格的课程问答评分员。请判断「学生答案」与「参考答案」在语义上是否一致。

    判分规则：
    - 1.0：核心结论与参考答案完全一致，关键实体、数值、关系都对得上。可以接受同义表达（例如「ε-邻域半径」与「eps」、「最小邻居数」与「MinPts」）。
    - 0.5：方向正确但缺少关键要点 / 含可纠正的小错误，或仅部分覆盖参考答案。
    - 0.0：与参考答案矛盾、答非所问、严重缺失要点。

    输出严格 JSON：
    {{
      "score": 1.0 | 0.5 | 0.0,
      "rationale": "<= 60 字"
    }}

    问题：{question}
    参考答案：{gold_answer_summary}
    学生答案：
    \"\"\"
    {answer}
    \"\"\"
    """
).strip()


FAITHFULNESS_PROMPT = dedent(
    """
    你是严格的课程问答忠实性评分员。请判断「学生答案」中的每一条事实陈述是否能在「证据原文」里找到支撑。

    判分规则：
    - 1：所有陈述都能在证据中找到对应。允许语义改写、同义替换。
    - 0：存在至少一条陈述在证据中找不到支撑，或与证据冲突。

    输出严格 JSON：
    {{
      "is_supported": true | false,
      "unsupported_claims": ["<最多 3 条不被支持的陈述>"]
    }}

    问题：{question}
    证据原文：
    {evidence}

    学生答案：
    \"\"\"
    {answer}
    \"\"\"
    """
).strip()
