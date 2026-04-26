<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pagePresets = {
  courses: {
    actions: ['新建课程', '筛选状态'],
    fields: ['课程名称', '课程 ID', '资料数量', '最近索引状态'],
  },
  'course-detail': {
    actions: ['查看资料', '管理成员'],
    fields: ['概览', '课程资料', '知识库', '课程成员', '问答会话', '操作日志'],
  },
  'material-detail': {
    actions: ['触发解析', '查看解析结果', '导出 GraphRAG 输入'],
    fields: ['课程资料 ID', '资料对象 ID', '文件名', '解析状态', 'MinerU 批次 ID'],
  },
  'parse-results': {
    actions: ['查看产物', '下载 JSON'],
    fields: ['content_list_json', 'model_json', 'layout_json', 'markdown', 'origin_pdf'],
  },
  'knowledge-bases': {
    actions: ['新建知识库', '查看激活版本'],
    fields: ['知识库名称', '所属课程', '状态', '激活索引运行 ID'],
  },
  'knowledge-base-detail': {
    actions: ['进入构建向导', '激活成功索引', '归档知识库'],
    fields: ['概览', '文档映射', '索引运行', '问答验证', '运行日志'],
  },
  'knowledge-base-build': {
    actions: ['选择资料', '创建索引', '问答验证'],
    fields: ['选择课程资料', '解析状态检查', '导出输入', '创建索引', '激活索引', '问答验证'],
  },
  'index-run-detail': {
    actions: ['重试任务', '查看日志'],
    fields: ['知识库 ID', '引擎', '索引版本', '状态', '索引产物', '失败信息'],
  },
  'qa-sessions': {
    actions: ['正式问答', '冒烟验证'],
    fields: ['会话标题', '用户', '课程', '知识库', '状态', '会话类型过滤'],
  },
  'qa-session-detail': {
    actions: ['查看任务', '查看检索日志'],
    fields: ['消息列表', '任务状态', '查询模式', '心跳时间', '关联检索日志'],
  },
  users: {
    actions: ['新建用户', '分配角色'],
    fields: ['用户名', '展示名称', '状态', '角色', '最近登录时间'],
  },
  roles: {
    actions: ['保存矩阵', '变更确认'],
    fields: ['角色列表', '权限点分组', '勾选矩阵'],
  },
  'course-memberships': {
    actions: ['添加成员', '调整课程内角色'],
    fields: ['用户', '课程', '课程内角色', '状态', '授权来源'],
  },
}

const preset = computed(() => pagePresets[route.name] ?? { actions: [], fields: [] })
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <div>
        <h2>{{ route.meta.title }}</h2>
        <p>{{ route.meta.resource || route.meta.navGroup || 'console' }}</p>
      </div>
      <div class="button-row">
        <button v-for="action in preset.actions" :key="action" class="secondary-button" type="button">
          {{ action }}
        </button>
      </div>
    </div>

    <div class="field-grid">
      <div v-for="field in preset.fields" :key="field" class="field-tile">
        <span>{{ field }}</span>
        <strong>待接入</strong>
      </div>
    </div>
  </section>
</template>
