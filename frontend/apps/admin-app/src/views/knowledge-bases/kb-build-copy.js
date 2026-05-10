// 构建向导 6 步的 eyebrow / hint / helper 文案。
// 所有文案面向教师 / 教务，禁止使用 embedding / 实体抽取 / MinerU / P95 / 冒烟 等内部术语。

export const KB_BUILD_COPY = Object.freeze({
  eyebrow: '生产 · 知识库 · 构建向导',
  heading: '知识库构建向导',
  placeholder: '提交后将在右侧实时显示构建过程',
  steps: {
    material: {
      title: '资料选择',
      hint: '勾选本次知识库需要包含的课程资料。',
      helper: '建议选择已通过解析检查的资料，确保可用于生成检索索引。',
    },
    parse: {
      title: '解析检查',
      hint: '确认资料已完成解析（文本 / 章节 / 图片）。',
      helper: '资料若仍在解析中或解析失败，可先回到资料详情重试。',
    },
    export: {
      title: '生成图谱输入',
      hint: '把解析产物转换为知识图谱可消费的 JSON。',
      helper: '此步骤会生成 normalized 与 section 两份输入文件。',
    },
    prompt: {
      title: 'Prompt 确认',
      hint: '确认本次构建使用的提示词版本。',
      helper: '可在结果异常时切换到其他候选 Prompt 重新构建。',
    },
    index: {
      title: '创建索引',
      hint: '提交索引构建任务，通常耗时 5~10 分钟。',
      helper: '索引完成后会自动激活，可在右侧面板跟踪阶段进度。',
    },
    qa_check: {
      title: '问答效果验证',
      hint: '使用一组预设问题校验索引质量。',
      helper: '验证通过后可进入正式问答场景。',
    },
  },
  buttons: {
    previous: '上一步',
    primary: '继续下一步',
    retry: '重试当前阶段',
    cancel: '取消构建',
    duplicate: '复制为新构建',
  },
  feedback: {
    readonly: '当前角色只读，无法操作构建流程。',
    noSelection: '请先选择至少一份资料。',
  },
})
