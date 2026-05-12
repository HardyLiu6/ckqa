#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""实体类型归一化器（公平对比用）。

用途：把自由中文实体类型（如"概念, 信息载体"、"算法"）归一化到 schema 定义的
PascalCase 类型（如 Concept、AlgorithmOrMethod），用于对比使用不同 prompt
格式的候选（如 exp4 vs auto_tuned）。

注意：
- 此模块**仅用于评分对比**，不应影响生产环境的抽取输出。
- 归一化是基于关键词的规则映射，不是 LLM 判断。
- 不能归一化的类型（如"人物，作者"）会返回 None，由调用方决定丢弃或保留原值。
"""

from __future__ import annotations

import re
from typing import Callable


# ---------------------------------------------------------------------------
# 关键词映射表
# ---------------------------------------------------------------------------
# 顺序重要：先匹配更特定的类型，再匹配通用类型。
# 例如"算法"应该优先于"方法"，因为更特定。

_TYPE_KEYWORD_MAP: list[tuple[str, tuple[str, ...]]] = [
    # 结构类型（优先级最高，因为命名特征明显）
    ("Course", ("课程",)),
    ("Chapter", ("章节", "章节结构", "教学章节")),
    ("Section", ("节", "小节", "专题", "章节片段")),
    # 学习活动
    ("Experiment", ("实验", "上机", "项目", "课程项目", "实践任务", "验证任务")),
    ("Assignment", ("作业", "习题", "题组", "报告", "测验", "考题", "复习题")),
    # 工具平台
    ("ToolOrPlatform", ("平台", "工具", "框架", "软件", "操作系统", "系统类型",
                         "数据库", "开发环境", "硬件平台")),
    # 公式定义（优先于 Concept，因为更特定）
    ("FormulaOrDefinition", ("公式", "定义", "定律", "判定条件", "符号化表达",
                              "定理", "计算公式", "表达式")),
    # 算法方法
    ("AlgorithmOrMethod", ("算法", "方法", "策略", "协议", "处理流程", "机制",
                            "技术", "技术手段", "实现策略", "步骤",
                            "调度算法", "置换算法", "分配算法")),
    # 知识点（考核/掌握对象）
    ("KnowledgePoint", ("知识点", "考点", "学习目标", "教学目标",
                         "掌握对象", "重点内容")),
    # 术语（缩写/符号）
    ("Term", ("术语", "缩写", "符号", "英文全称", "名词", "专有名词",
               "命名约定", "标识符")),
    # 概念（最通用，放最后）
    ("Concept", ("概念", "机制概念", "原理", "对象", "属性", "特征",
                  "现象", "条件", "状态", "性质", "理论概念")),
]


# ---------------------------------------------------------------------------
# 核心归一化函数
# ---------------------------------------------------------------------------

def normalize_entity_type(raw_type: str) -> str | None:
    """将自由类型归一化到 schema PascalCase 类型。

    匹配策略：
      1. 如果 raw_type 已经是 schema 类型（大小写不敏感），直接返回标准形式。
      2. 否则按关键词匹配表顺序查找，返回第一个命中的 schema 类型。
      3. 无法匹配则返回 None。

    raw_type 可能包含多个中文描述（如 "概念, 信息载体"），会按整体做关键词匹配。
    """
    if not raw_type:
        return None

    normalized = raw_type.strip()

    # 1. 精确匹配 schema 类型（casefold 不敏感）
    schema_types = {
        "course": "Course",
        "chapter": "Chapter",
        "section": "Section",
        "knowledgepoint": "KnowledgePoint",
        "concept": "Concept",
        "term": "Term",
        "formulaordefinition": "FormulaOrDefinition",
        "algorithmormethod": "AlgorithmOrMethod",
        "experiment": "Experiment",
        "assignment": "Assignment",
        "toolorplatform": "ToolOrPlatform",
    }
    lower = normalized.casefold().replace(" ", "").replace("_", "")
    if lower in schema_types:
        return schema_types[lower]

    # 2. 关键词匹配
    for schema_type, keywords in _TYPE_KEYWORD_MAP:
        for keyword in keywords:
            if keyword in normalized:
                return schema_type

    # 3. 无法匹配
    return None


# ---------------------------------------------------------------------------
# 关系描述 → 关系类型提取（用于自由文本 description）
# ---------------------------------------------------------------------------

# 关系类型的语义关键词
_RELATION_KEYWORD_MAP: list[tuple[str, tuple[str, ...]]] = [
    ("contains", ("包含", "包括", "分为", "组成", "构成", "由.*组成", "具有", "含有")),
    ("belongs_to", ("属于", "隶属于", "归入", "位于")),
    ("defined_by", ("定义为", "由.*定义", "定义", "规定为", "表示为", "被定义")),
    ("prerequisite_of", ("先修于", "前置", "前提", "先学", "基础是")),
    ("depends_on", ("依赖于", "依赖", "取决于", "基于", "需要")),
    ("implemented_by", ("由.*实现", "通过.*实现", "使用.*实现", "实现为")),
    ("applied_in", ("应用于", "用于", "应用在", "运用于", "适用于")),
    ("evaluated_by", ("评估", "考核", "测试", "考察")),
    ("appears_in", ("出现于", "出现在", "位于", "在.*中")),
    ("related_to", ("相关", "有关", "关联", "联系")),
]


def normalize_relation_type(description: str) -> str | None:
    """从关系 description 中推断关系类型。

    匹配策略：
      1. 先尝试提取 [type=xxx] 前缀（exp4 格式）。
      2. 否则按关键词匹配 description 内容。
      3. 无法匹配返回 None。
    """
    if not description:
        return None

    # 1. [type=xxx] 前缀（exp4 格式）
    match = re.match(r"^\[type=([^\]]+)\]", description.strip())
    if match:
        rtype = match.group(1).strip().casefold()
        valid = {"contains", "belongs_to", "defined_by", "prerequisite_of",
                 "depends_on", "implemented_by", "applied_in",
                 "evaluated_by", "related_to", "appears_in"}
        if rtype in valid:
            return rtype

    # 2. 自由文本关键词匹配
    for rtype, keywords in _RELATION_KEYWORD_MAP:
        for kw in keywords:
            if re.search(kw, description):
                return rtype

    return None


# ---------------------------------------------------------------------------
# 归一化配置工厂
# ---------------------------------------------------------------------------

def make_normalizer(mode: str = "strict") -> Callable[[str], str | None] | None:
    """根据模式返回合适的归一化函数。

    mode:
      - strict: 返回 None，评分器使用原始精确匹配（默认）
      - fuzzy: 返回 normalize_entity_type，启用宽松关键词归一化
    """
    if mode == "strict":
        return None
    if mode == "fuzzy":
        return normalize_entity_type
    raise ValueError(f"未知的归一化模式: {mode}，应为 'strict' 或 'fuzzy'")
