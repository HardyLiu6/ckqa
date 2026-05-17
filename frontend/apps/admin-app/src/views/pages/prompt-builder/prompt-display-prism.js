// 自定义 prismjs 语言：prompt-tune
// 识别 prompt 文件中的常见 token：
// - section：以 `-` 开头结尾的标题行（如 `-Goal-`, `-Schema Constraints-`）
// - placeholder：`{var}` 占位符
// - arrow：`->` 关系箭头
// - comment：`#` 行注释
// - keyword：实体类型 hardcode 列表（与课程域 schema 对齐）
//
// 不依赖 prismjs/components/prism-* 任何内置语言（避免 vite SSR / 全局 attach 兼容问题）。
import Prism from 'prismjs'
import 'prismjs/themes/prism-tomorrow.css'

const ENTITY_KEYWORDS = [
  'Course',
  'Concept',
  'Term',
  'KnowledgePoint',
  'FormulaOrDefinition',
  'AlgorithmOrMethod',
  'Theorem',
  'Property',
  'Example',
  'Topic',
]

Prism.languages['prompt-tune'] = {
  comment: {
    pattern: /(^|[^\\])#.*/,
    lookbehind: true,
  },
  section: {
    // 整行匹配 `-Section Title-` 形式
    pattern: /^-[^-\n][^\n]*?-\s*$/m,
    alias: 'keyword',
  },
  placeholder: {
    pattern: /\{[a-zA-Z_][a-zA-Z0-9_]*\}/,
    alias: 'variable',
  },
  arrow: {
    pattern: /->/,
    alias: 'operator',
  },
  keyword: new RegExp(`\\b(?:${ENTITY_KEYWORDS.join('|')})\\b`),
}

export default Prism
