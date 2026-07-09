<template>
  <div class="commercial-ops">
    <div class="page-head">
      <div>
        <h2 class="page-title">{{ current.title }}</h2>
        <p class="page-subtitle">{{ current.subtitle }}</p>
      </div>
      <a-space wrap>
        <a-select
          v-model:value="tenantId"
          show-search
          placeholder="选择租户"
          style="width: 240px"
          @change="onTenantChange"
        >
          <a-select-option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">
            {{ tenantOptionLabel(tenant) }}
          </a-select-option>
        </a-select>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </a-space>
    </div>

    <template v-if="section === 'channels'">
      <a-card>
        <a-table :columns="channelColumns" :data-source="channels" :pagination="false" row-key="channel">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'implementationStatus'" :color="channelStatusColor(record.implementationStatus)">
              {{ record.implementationStatus }}
            </a-tag>
            <span v-else-if="column.key === 'enabled'">
              {{ enabledText(record.inboundEnabled) }} / {{ enabledText(record.outboundEnabled) }}
            </span>
            <span v-else-if="column.key === 'avgFirstResponseSeconds'">{{ formatSeconds(record.avgFirstResponseSeconds) }}</span>
            <span v-else-if="column.key === 'csatAvg'">{{ formatDecimal(record.csatAvg) }}</span>
          </template>
        </a-table>
      </a-card>
    </template>

    <template v-else-if="section === 'inbox'">
      <div class="queue-grid">
        <button
          v-for="queue in queues"
          :key="queue.queueKey"
          :class="['queue-card', { active: activeQueue === queue.queueKey }]"
          type="button"
          @click="selectQueue(queue.queueKey)"
        >
          <span>{{ queue.queueLabel }}</span>
          <strong>{{ queue.count }}</strong>
          <em>{{ queue.description }}</em>
        </button>
      </div>

      <a-card>
        <a-table
          :columns="inboxColumns"
          :data-source="inboxItems"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1200 }"
          :row-key="inboxRowKey"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.statusLabel)">{{ record.statusLabel || '—' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'workItemType'">
              <a-tag :color="record.workItemType === 'TICKET' ? 'orange' : 'blue'">
                {{ record.workItemType === 'TICKET' ? '工单' : '会话' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'slaState'">
              <a-tag :color="slaColor(record.slaState)">{{ record.slaState || '—' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="takeover(record)">
                  {{ record.workItemType === 'TICKET' ? '接管工单' : '接管' }}
                </a-button>
                <a-button size="small" type="primary" @click="reply(record)">
                  {{ record.workItemType === 'TICKET' ? '解决' : '回复' }}
                </a-button>
              </a-space>
            </template>
            <template v-else-if="column.key === 'intent'">{{ intentLabel(record.intent) }}</template>
          </template>
        </a-table>
        <a-pagination v-model:current="page" class="pager" :page-size="size" :total="total" @change="load" />
      </a-card>
    </template>

    <template v-else-if="section === 'sla'">
      <div class="metric-grid compact">
        <a-card v-for="metric in slaMetrics" :key="metric.label">
          <a-statistic :title="metric.label" :value="metric.value" />
          <div class="metric-note">{{ metric.note }}</div>
        </a-card>
      </div>
      <a-card title="SLA 风险工单">
        <a-table :columns="slaColumns" :data-source="slaSummary?.riskTickets || []" :pagination="false" row-key="id">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'slaState'" :color="slaColor(record.slaState)">{{ record.slaState }}</a-tag>
            <a-tag v-else-if="column.key === 'status'" :color="statusColor(record.statusLabel)">{{ record.statusLabel }}</a-tag>
          </template>
        </a-table>
      </a-card>
    </template>

    <template v-else-if="section === 'macros'">
      <a-card>
        <a-table :columns="macroColumns" :data-source="macros" :pagination="false" row-key="macroCode">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'requiresApproval'" :color="record.requiresApproval ? 'orange' : 'green'">
              {{ record.requiresApproval ? '需要审批' : '可直接发送' }}
            </a-tag>
          </template>
        </a-table>
      </a-card>
    </template>

    <template v-else-if="section === 'actions'">
      <a-card class="toolbar-card">
        <div class="toolbar">
          <a-select v-model:value="actionStatus" allow-clear placeholder="筛选状态" style="width: 180px" @change="load">
            <a-select-option value="1">待人工审批</a-select-option>
            <a-select-option value="PENDING_APPROVAL">待审批动作</a-select-option>
            <a-select-option value="APPROVED_MANUAL">已人工批准</a-select-option>
            <a-select-option value="REJECTED">已拒绝</a-select-option>
          </a-select>
        </div>
      </a-card>
      <a-card>
        <a-table
          :columns="actionColumns"
          :data-source="actions"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1250 }"
          row-key="requestNo"
        >
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'status'" :color="actionColor(record.statusLabel)">{{ record.statusLabel }}</a-tag>
            <a-space v-else-if="column.key === 'actions'">
              <a-button size="small" type="primary" @click="approveAction(record)">批准</a-button>
              <a-button size="small" danger @click="rejectAction(record)">拒绝</a-button>
            </a-space>
          </template>
        </a-table>
        <a-pagination v-model:current="page" class="pager" :page-size="size" :total="total" @change="load" />
      </a-card>
    </template>

    <template v-else-if="section === 'qa'">
      <a-card>
        <a-table :columns="qaColumns" :data-source="qaItems" :loading="loading" :pagination="false" row-key="id">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'status'" :color="record.status === 'REVIEWED' ? 'green' : 'orange'">
              {{ record.status === 'REVIEWED' ? '已复核' : '待复核' }}
            </a-tag>
            <a-button v-else-if="column.key === 'actions'" size="small" type="primary" @click="reviewQa(record)">复核</a-button>
          </template>
        </a-table>
        <a-pagination v-model:current="page" class="pager" :page-size="size" :total="total" @change="load" />
      </a-card>
    </template>

    <template v-else-if="section === 'operations'">
      <div class="metric-grid">
        <a-card v-for="metric in operationMetrics" :key="metric.label">
          <a-statistic :title="metric.label" :value="metric.value" />
          <div class="metric-note">{{ metric.note }}</div>
        </a-card>
      </div>
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :lg="8">
          <a-card title="意图分布"><metric-list :items="operations?.intents || []" /></a-card>
        </a-col>
        <a-col :xs="24" :lg="8">
          <a-card title="渠道分布"><metric-list :items="operations?.channels || []" /></a-card>
        </a-col>
        <a-col :xs="24" :lg="8">
          <a-card title="失败原因"><metric-list :items="operations?.topFailureCategories || []" /></a-card>
        </a-col>
      </a-row>
    </template>

    <template v-else-if="section === 'audit'">
      <a-card>
        <a-table :columns="auditColumns" :data-source="auditEvents" :loading="loading" :pagination="false" row-key="id">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'riskLevel'" :color="riskColor(record.riskLevel)">{{ record.riskLevel }}</a-tag>
          </template>
        </a-table>
        <a-pagination v-model:current="page" class="pager" :page-size="size" :total="total" @change="load" />
      </a-card>
    </template>

    <template v-else-if="section === 'sre'">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="14">
          <a-card title="SLO">
            <a-table :columns="sloColumns" :data-source="sre?.slos || []" :pagination="false" row-key="key">
              <template #bodyCell="{ column, record }">
                <a-tag v-if="column.key === 'status'" :color="record.status === 'OK' ? 'green' : 'red'">
                  {{ record.status === 'OK' ? '正常' : '未达标' }}
                </a-tag>
              </template>
            </a-table>
          </a-card>
        </a-col>
        <a-col :xs="24" :xl="10">
          <a-card title="告警">
            <a-empty v-if="!sre?.alerts?.length" description="暂无告警" />
            <div v-for="alert in sre?.alerts || []" :key="`${alert.category}-${alert.message}`" class="alert-row">
              <a-tag :color="alert.severity === 'WARN' ? 'orange' : 'blue'">{{ alert.severity }}</a-tag>
              <div>
                <strong>{{ alert.category }}</strong>
                <span>{{ alert.message }}</span>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
      <a-card class="block" title="SLO 策略">
        <a-table :columns="sloPolicyColumns" :data-source="sre?.policies || []" :pagination="false" row-key="sloKey" />
      </a-card>
    </template>

    <template v-else-if="section === 'agent'">
      <a-card class="block">
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="工作流">{{ workflow?.workflowName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="当前模式">{{ workflow?.currentMode || '—' }}</a-descriptions-item>
        </a-descriptions>
      </a-card>
      <a-card class="block" title="路由试算">
        <a-space wrap class="toolbar">
          <a-select v-model:value="planIntent" style="width: 190px">
            <a-select-option value="ORDER_STATUS">订单查询</a-select-option>
            <a-select-option value="LOGISTICS">物流追踪</a-select-option>
            <a-select-option value="RETURN_REFUND">退货/退款</a-select-option>
            <a-select-option value="ADDRESS_CHANGE">改地址</a-select-option>
            <a-select-option value="PRODUCT_ADVICE">商品推荐</a-select-option>
            <a-select-option value="POLICY_QA">政策问答</a-select-option>
            <a-select-option value="COMPLAINT">投诉</a-select-option>
            <a-select-option value="UNKNOWN">未知意图</a-select-option>
          </a-select>
          <a-input v-model:value="planMessage" style="width: min(520px, 100%)" placeholder="输入买家问题，后端返回 specialist / 工具白名单 / 风险等级" />
          <a-button type="primary" @click="runAgentPlan">试算</a-button>
        </a-space>
        <a-descriptions v-if="agentPlan" class="plan-result" :column="2" bordered>
          <a-descriptions-item label="分派智能体">{{ agentPlan.specialistLabel }}</a-descriptions-item>
          <a-descriptions-item label="风险等级">{{ agentPlan.riskLevel }}</a-descriptions-item>
          <a-descriptions-item label="工具白名单">{{ (agentPlan.toolAllowlist || []).join(', ') || '无工具' }}</a-descriptions-item>
          <a-descriptions-item label="身份校验">{{ agentPlan.requiresIdentityVerification ? '需要' : '不需要' }}</a-descriptions-item>
          <a-descriptions-item label="人工审批">{{ agentPlan.requiresApproval ? '需要' : '不需要' }}</a-descriptions-item>
          <a-descriptions-item label="建议转人工">{{ agentPlan.recommendHumanHandoff ? '是' : '否' }}</a-descriptions-item>
        </a-descriptions>
      </a-card>
      <a-row :gutter="[16, 16]">
        <a-col v-for="node in workflow?.nodes || []" :key="node.nodeKey" :xs="24" :lg="12" :xl="8">
          <a-card :title="node.nodeLabel">
            <p class="muted">{{ node.responsibility }}</p>
            <a-tag color="blue">{{ node.status }}</a-tag>
            <div class="tool-line">{{ node.toolAllowlist }}</div>
          </a-card>
        </a-col>
      </a-row>
      <a-card class="block" title="执行策略">
        <a-table :columns="policyColumns" :data-source="workflow?.policies || []" :pagination="false" row-key="policyKey" />
      </a-card>
    </template>

    <template v-else-if="section === 'security'">
      <a-alert class="block" type="info" show-icon message="这里展示的是后端返回的生产边界，不把未完成能力伪装成已上线。" />
      <a-card class="block" title="安全与权限控制">
        <a-table :columns="readinessColumns" :data-source="readiness?.securityControls || []" :pagination="false" row-key="controlKey">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'status'" :color="readinessColor(record.status)">{{ readinessLabel(record.status) }}</a-tag>
            <a-tag v-else-if="column.key === 'riskLevel'" :color="riskColor(record.riskLevel)">{{ record.riskLevel }}</a-tag>
          </template>
        </a-table>
      </a-card>
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="12">
          <a-card title="角色与工具权限">
            <a-table :columns="roleColumns" :data-source="readiness?.rolePolicies || []" :pagination="false" row-key="roleKey" />
          </a-card>
        </a-col>
        <a-col :xs="24" :xl="12">
          <a-card title="高风险动作策略">
            <a-table :columns="actionPolicyColumns" :data-source="readiness?.actionPolicies || []" :pagination="false" row-key="actionType">
              <template #bodyCell="{ column, record }">
                <a-tag v-if="column.key === 'externalWriteEnabled'" :color="record.externalWriteEnabled ? 'red' : 'green'">
                  {{ record.externalWriteEnabled ? '允许外部写' : '仅内部审批' }}
                </a-tag>
              </template>
            </a-table>
          </a-card>
        </a-col>
      </a-row>
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="12">
          <a-card title="数据保留与脱敏">
            <a-table :columns="retentionColumns" :data-source="readiness?.retentionPolicies || []" :pagination="false" row-key="dataSet" />
          </a-card>
        </a-col>
        <a-col :xs="24" :xl="12">
          <a-card title="Shopify 生产化边界">
            <a-table :columns="shopifyCapabilityColumns" :data-source="readiness?.shopifyCapabilities || []" :pagination="false" row-key="capabilityKey">
              <template #bodyCell="{ column, record }">
                <a-tag v-if="column.key === 'status'" :color="readinessColor(record.status)">{{ readinessLabel(record.status) }}</a-tag>
              </template>
            </a-table>
          </a-card>
        </a-col>
      </a-row>
      <a-card class="block" title="生产 Runbook">
        <a-table :columns="runbookColumns" :data-source="readiness?.runbooks || []" :pagination="false" row-key="incident" />
      </a-card>
      <a-card class="block" title="Agent 幂等 guard">
        <a-table :columns="guardColumns" :data-source="readiness?.recentAgentGuards || []" :pagination="false" row-key="id" />
      </a-card>
      <a-card title="明确不承诺">
        <a-tag v-for="item in readiness?.explicitNonGoals || []" :key="item" class="non-goal">{{ item }}</a-tag>
      </a-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import api from '@/api'
import { intentLabel, tenantOptionLabel } from '@/utils/display'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

const props = defineProps<{ section: string }>()
const router = useRouter()

const MetricList = defineComponent({
  props: { items: { type: Array, default: () => [] } },
  setup(listProps) {
    return () => {
      const rows = listProps.items as any[]
      if (!rows.length) return h('div', { class: 'empty-text' }, '暂无后端数据')
      return h('div', { class: 'dimension-list' }, rows.map((item) => h('div', { class: 'dimension-row', key: item.name }, [
        h('span', item.name || '—'),
        h('strong', `${item.count ?? 0} · ${formatPercent(item.rate)}`),
      ])))
    }
  },
})

const pageConfigs: Record<string, { title: string; subtitle: string }> = {
  channels: { title: '多渠道接入', subtitle: '展示已接入渠道和路线图渠道；未接通的渠道只显示适配器状态，不伪装成可用。' },
  inbox: { title: '统一客服工作台', subtitle: '客服队列、人工接管、人工回复和会话状态来自后端真实字段。' },
  sla: { title: 'SLA 管理', subtitle: '按人工工单的响应/解决截止时间计算风险，不在前端编造超时状态。' },
  macros: { title: '客服宏回复', subtitle: '可复用话术模板，重点覆盖身份校验、退货审批和物流延误。' },
  actions: { title: '高风险动作审批', subtitle: '退款、补发、改地址、取消订单均进入人工审批，不由 AI 直接写外部系统。' },
  qa: { title: '客服质检', subtitle: '已解决工单进入质检队列，自动评分和人工复核结果落库。' },
  operations: { title: '运营指标', subtitle: '按会话、渠道、意图、成本和失败原因展示客服运营闭环。' },
  audit: { title: '审计日志', subtitle: '接管、回复、审批和质检复核等关键操作按租户记录。' },
  sre: { title: '生产健康', subtitle: 'SLO、告警、Webhook 积压、失败轨迹和成本风险的最小生产视图。' },
  agent: { title: '多智能体工作流', subtitle: '展示 supervisor-worker 编排边界、工具权限和审批策略。' },
  security: { title: '生产边界', subtitle: 'RBAC、审计、数据保留、Shopify App Store 级边界和运行手册的真实状态。' },
}

const current = computed(() => pageConfigs[props.section] || pageConfigs.inbox)
const section = computed(() => props.section)

const tenants = ref<any[]>([])
const tenantId = ref<number | null>(null)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const activeQueue = ref('all')
const actionStatus = ref<string | undefined>()

const channels = ref<any[]>([])
const queues = ref<any[]>([])
const inboxItems = ref<any[]>([])
const slaSummary = ref<any>(null)
const macros = ref<any[]>([])
const actions = ref<any[]>([])
const qaItems = ref<any[]>([])
const operations = ref<any>(null)
const auditEvents = ref<any[]>([])
const sre = ref<any>(null)
const workflow = ref<any>(null)
const readiness = ref<any>(null)
const agentPlan = ref<any>(null)
const planIntent = ref('RETURN_REFUND')
const planMessage = ref('Refund my order #1005 and I am angry.')

const channelColumns = [
  { title: '渠道', dataIndex: 'channelLabel' },
  { title: '账号', dataIndex: 'accountName', width: 210, ellipsis: true },
  { title: '状态', dataIndex: 'implementationStatus', key: 'implementationStatus', width: 140 },
  { title: '收/发', key: 'enabled', width: 95 },
  { title: '授权', dataIndex: 'authMode', width: 110 },
  { title: 'Webhook', dataIndex: 'webhookStatus', width: 130 },
  { title: '会话数', dataIndex: 'conversations', width: 90 },
  { title: '未关闭', dataIndex: 'openConversations', width: 90 },
  { title: '升级人工', dataIndex: 'escalatedConversations', width: 100 },
  { title: '平均首响', dataIndex: 'avgFirstResponseSeconds', key: 'avgFirstResponseSeconds', width: 110 },
  { title: 'CSAT', dataIndex: 'csatAvg', key: 'csatAvg', width: 90 },
]

const inboxColumns = [
  { title: '类型', dataIndex: 'workItemType', key: 'workItemType', width: 95 },
  { title: '客户', dataIndex: 'customerName', width: 130 },
  { title: '邮箱', dataIndex: 'customerEmail', width: 190 },
  { title: '渠道', dataIndex: 'channelLabel', width: 130 },
  { title: '意图', dataIndex: 'intent', key: 'intent', width: 120 },
  { title: '状态', dataIndex: 'statusLabel', key: 'status', width: 120 },
  { title: 'SLA', dataIndex: 'slaState', key: 'slaState', width: 110 },
  { title: '工单', dataIndex: 'latestTicketNo', width: 190 },
  { title: '成本 USD', dataIndex: 'totalCostUsd', width: 100 },
  { title: '操作', key: 'actions', width: 150 },
]

const slaColumns = [
  { title: '工单号', dataIndex: 'ticketNo', width: 210 },
  { title: '状态', dataIndex: 'statusLabel', key: 'status', width: 110 },
  { title: 'SLA', dataIndex: 'slaState', key: 'slaState', width: 110 },
  { title: '响应截止', dataIndex: 'responseDueAt', width: 190 },
  { title: '解决截止', dataIndex: 'resolveDueAt', width: 190 },
  { title: '摘要', dataIndex: 'summary', ellipsis: true },
]

const macroColumns = [
  { title: '编码', dataIndex: 'macroCode', width: 150 },
  { title: '标题', dataIndex: 'title', width: 180 },
  { title: '分类', dataIndex: 'category', width: 110 },
  { title: '渠道', dataIndex: 'channel', width: 90 },
  { title: '审批', dataIndex: 'requiresApproval', key: 'requiresApproval', width: 110 },
  { title: '内容', dataIndex: 'content', ellipsis: true },
]

const actionColumns = [
  { title: '请求号', dataIndex: 'requestNo', width: 210 },
  { title: '动作', dataIndex: 'actionType', width: 130 },
  { title: '状态', dataIndex: 'statusLabel', key: 'status', width: 130 },
  { title: '订单', dataIndex: 'externalOrderNumber', width: 110 },
  { title: '客户', dataIndex: 'customerEmail', width: 190 },
  { title: '风险原因', dataIndex: 'riskReason', ellipsis: true },
  { title: '操作', key: 'actions', width: 140 },
]

const qaColumns = [
  { title: '工单', dataIndex: 'ticketNo', width: 210 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '自动评分', dataIndex: 'autoScore', width: 90 },
  { title: '人工评分', dataIndex: 'reviewerScore', width: 90 },
  { title: '风险标记', dataIndex: 'reviewFlags', ellipsis: true },
  { title: '发现', dataIndex: 'findings', ellipsis: true },
  { title: '操作', key: 'actions', width: 90 },
]

const auditColumns = [
  { title: '动作', dataIndex: 'action', width: 180 },
  { title: '资源', dataIndex: 'resourceType', width: 150 },
  { title: '摘要', dataIndex: 'summary', ellipsis: true },
  { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel', width: 90 },
  { title: '时间', dataIndex: 'createdAt', width: 190 },
]

const sloColumns = [
  { title: '指标', dataIndex: 'label' },
  { title: '目标', dataIndex: 'target', width: 100 },
  { title: '当前', dataIndex: 'actual', width: 100 },
  { title: '单位', dataIndex: 'unit', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
]

const sloPolicyColumns = [
  { title: 'SLO', dataIndex: 'sloLabel' },
  { title: '目标', dataIndex: 'targetValue', width: 100 },
  { title: '单位', dataIndex: 'unit', width: 80 },
  { title: '窗口', dataIndex: 'windowMinutes', width: 90 },
  { title: 'Runbook', dataIndex: 'runbook', ellipsis: true },
]

const policyColumns = [
  { title: '策略', dataIndex: 'policyKey', width: 180 },
  { title: '说明', dataIndex: 'description' },
  { title: '执行方式', dataIndex: 'enforcement', width: 240 },
]

const readinessColumns = [
  { title: '控制项', dataIndex: 'controlLabel', width: 190 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '证据', dataIndex: 'evidence', ellipsis: true },
  { title: '下一步', dataIndex: 'nextStep', ellipsis: true },
  { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel', width: 90 },
]

const retentionColumns = [
  { title: '数据集', dataIndex: 'dataSet', width: 190 },
  { title: '保留天数', dataIndex: 'defaultRetentionDays', width: 95 },
  { title: '脱敏默认值', dataIndex: 'maskingDefault', ellipsis: true },
  { title: '导出', dataIndex: 'exportSupport', width: 120 },
  { title: '删除', dataIndex: 'deletionSupport', width: 120 },
  { title: '状态', dataIndex: 'status', width: 130 },
]

const roleColumns = [
  { title: '角色', dataIndex: 'roleLabel', width: 130 },
  { title: '权限', dataIndex: 'permissionsJson', ellipsis: true },
  { title: '工具策略', dataIndex: 'toolPolicyJson', ellipsis: true },
  { title: '审批额度', dataIndex: 'approvalLimit', width: 90 },
]

const actionPolicyColumns = [
  { title: '动作', dataIndex: 'actionType', width: 120 },
  { title: '审批角色', dataIndex: 'minApproverRole', width: 170 },
  { title: '身份校验', dataIndex: 'requiresIdentityVerification', width: 90 },
  { title: '幂等窗口', dataIndex: 'idempotencyWindowMinutes', width: 90 },
  { title: '外部写', key: 'externalWriteEnabled', width: 110 },
  { title: '说明', dataIndex: 'policyNote', ellipsis: true },
]

const shopifyCapabilityColumns = [
  { title: '能力', dataIndex: 'capabilityLabel', width: 190 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '默认模式', dataIndex: 'defaultMode', width: 120 },
  { title: '证据', dataIndex: 'evidence', ellipsis: true },
]

const runbookColumns = [
  { title: '事件', dataIndex: 'incident', width: 220 },
  { title: '触发信号', dataIndex: 'triggerSignal' },
  { title: '第一动作', dataIndex: 'firstAction' },
  { title: '负责人', dataIndex: 'escalationOwner', width: 110 },
  { title: '状态', dataIndex: 'status', width: 130 },
]

const guardColumns = [
  { title: '会话', dataIndex: 'conversationUuid', width: 210 },
  { title: 'Guard Key', dataIndex: 'guardKey', ellipsis: true },
  { title: '工具', dataIndex: 'toolName', width: 170 },
  { title: '状态', dataIndex: 'status', width: 110 },
  { title: '最近出现', dataIndex: 'lastSeenAt', width: 190 },
]

const slaMetrics = computed(() => [
  { label: '未关闭工单', value: slaSummary.value?.openTickets || 0, note: '状态为待分配、待响应或处理中' },
  { label: '响应超时', value: slaSummary.value?.responseBreached || 0, note: `响应违约率 ${formatPercent(slaSummary.value?.responseBreachRate)}` },
  { label: '解决超时', value: slaSummary.value?.resolveBreached || 0, note: `解决违约率 ${formatPercent(slaSummary.value?.resolveBreachRate)}` },
  { label: '即将超时', value: slaSummary.value?.dueSoon || 0, note: '30 分钟内到期' },
])

const operationMetrics = computed(() => [
  { label: '会话总数', value: operations.value?.conversations || 0, note: '当前租户范围' },
  { label: 'AI 解决率', value: formatPercent(operations.value?.aiResolutionRate), note: `${operations.value?.aiResolved || 0} 个 AI 解决` },
  { label: '人工接管率', value: formatPercent(operations.value?.humanTakeoverRate), note: `${operations.value?.humanTakeovers || 0} 个接管/升级` },
  { label: '平均 CSAT', value: formatDecimal(operations.value?.avgCsat), note: '来自已提交满意度' },
  { label: '单解决成本', value: `$${formatDecimal(operations.value?.costPerResolvedCase)}`, note: '按解决会话和关闭工单估算' },
])

async function loadTenants() {
  const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
  tenants.value = res.data?.records || []
  tenantId.value = selectDefaultTenantId(tenants.value)
  setStoredTenantId(tenantId.value)
}

function onTenantChange() {
  setStoredTenantId(tenantId.value)
  page.value = 1
  load()
}

function selectQueue(key: string) {
  if (key === 'approval') {
    router.push('/admin/actions')
    return
  }
  activeQueue.value = key
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    if (section.value === 'channels') {
      channels.value = (await api.get('/channels/summary')).data || []
    } else if (section.value === 'inbox') {
      const [queueRes, itemRes] = await Promise.all([
        api.get('/inbox/queues'),
        api.get('/inbox/items', { params: { queue: activeQueue.value, page: page.value, size: size.value } }),
      ])
      queues.value = queueRes.data || []
      inboxItems.value = itemRes.data?.records || []
      total.value = itemRes.data?.total || 0
    } else if (section.value === 'sla') {
      slaSummary.value = (await api.get('/sla/summary')).data
    } else if (section.value === 'macros') {
      macros.value = (await api.get('/macros')).data || []
    } else if (section.value === 'actions') {
      const res = await api.get('/actions', { params: { status: actionStatus.value, page: page.value, size: size.value } })
      actions.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else if (section.value === 'qa') {
      const res = await api.get('/qa/queue', { params: { page: page.value, size: size.value } })
      qaItems.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else if (section.value === 'operations') {
      operations.value = (await api.get('/operations/summary')).data
    } else if (section.value === 'audit') {
      const res = await api.get('/audit/events', { params: { page: page.value, size: size.value } })
      auditEvents.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else if (section.value === 'sre') {
      sre.value = (await api.get('/sre/summary')).data
    } else if (section.value === 'agent') {
      workflow.value = (await api.get('/agent/workflow')).data
    } else if (section.value === 'security') {
      readiness.value = (await api.get('/security/readiness')).data
    }
  } finally {
    loading.value = false
  }
}

async function takeover(row: any) {
  if (row.workItemType === 'TICKET' && row.ticketId) {
    await api.post(`/tickets/${row.ticketId}/assign`, { agentId: 1, note: '后台人工接管' })
    message.success('已接管工单')
    load()
    return
  }
  await api.post(`/inbox/${row.conversationUuid}/takeover`, { agentId: 1, note: '后台人工接管' })
  message.success('已接管会话')
  load()
}

async function reply(row: any) {
  if (row.workItemType === 'TICKET' && row.ticketId) {
    const note = window.prompt('输入解决说明', '人工处理完成')
    if (note === null) return
    await api.post(`/tickets/${row.ticketId}/resolve`, { actorId: 1, note })
    message.success('工单已解决')
    load()
    return
  }
  const text = window.prompt('输入人工回复内容')
  if (!text) return
  await api.post(`/inbox/${row.conversationUuid}/reply`, { message: text, closeAfterReply: false })
  message.success('已发送人工回复')
  load()
}

async function approveAction(row: any) {
  await api.post(`/actions/${row.source}/${row.id}/approve`, { actorId: 1, note: '后台人工批准' })
  message.success('已批准，未执行外部写操作')
  load()
}

async function rejectAction(row: any) {
  const note = window.prompt('拒绝原因')
  if (note === null) return
  await api.post(`/actions/${row.source}/${row.id}/reject`, { actorId: 1, note })
  message.success('已拒绝')
  load()
}

async function reviewQa(row: any) {
  const score = Number(window.prompt('人工评分 0-100', String(row.autoScore || 80)))
  if (!Number.isFinite(score)) return
  await api.post(`/qa/${row.id}/review`, {
    reviewerId: 1,
    score,
    findings: '人工复核完成',
    actionItems: score < 80 ? '需要复盘话术或工具调用' : '无需进一步处理',
  })
  message.success('质检已复核')
  load()
}

async function runAgentPlan() {
  agentPlan.value = (await api.post('/agent/plan', { intent: planIntent.value, message: planMessage.value })).data
}

function formatPercent(value: any) {
  if (value === null || value === undefined || value === '') return '—'
  return `${Number(value).toFixed(1)}%`
}

function formatDecimal(value: any) {
  if (value === null || value === undefined || value === '') return '—'
  return Number(value).toFixed(2)
}

function formatSeconds(value: any) {
  if (value === null || value === undefined || value === '') return '—'
  return `${Number(value).toFixed(1)} 秒`
}

function inboxRowKey(row: any) {
  return `${row.workItemType || 'CONVERSATION'}:${row.ticketId || row.conversationUuid}`
}

function enabledText(value: any) {
  return Number(value) === 1 ? '启用' : '未启用'
}

function statusColor(value: string) {
  if (!value) return 'default'
  if (value.includes('关闭') || value.includes('完成') || value.includes('解决')) return 'green'
  if (value.includes('超时')) return 'red'
  if (value.includes('人工')) return 'blue'
  return 'default'
}

function slaColor(value: string) {
  if (value === '已超时') return 'red'
  if (value === '即将超时') return 'orange'
  if (value === '正常') return 'green'
  if (value === '待审批') return 'purple'
  return 'default'
}

function actionColor(value: string) {
  if (value?.includes('批准')) return 'green'
  if (value?.includes('拒绝')) return 'red'
  return 'orange'
}

function channelStatusColor(value: string) {
  if (value === '已接入') return 'green'
  if (value?.includes('待接入')) return 'orange'
  if (value === '路线图') return 'default'
  return 'blue'
}

function riskColor(value: string) {
  if (value === 'HIGH') return 'red'
  if (value === 'MEDIUM') return 'orange'
  return 'blue'
}

function readinessColor(value: string) {
  if (value === 'IMPLEMENTED') return 'green'
  if (value === 'PARTIAL' || value === 'POLICY_DECLARED') return 'orange'
  if (value === 'NOT_ENABLED') return 'red'
  return 'default'
}

function readinessLabel(value: string) {
  if (value === 'IMPLEMENTED') return '已实现'
  if (value === 'PARTIAL') return '部分实现'
  if (value === 'POLICY_DECLARED') return '策略已声明'
  if (value === 'NOT_ENABLED') return '未启用'
  if (value === 'ROADMAP') return '路线图'
  return value || '—'
}

watch(() => props.section, () => {
  page.value = 1
  total.value = 0
  load()
})

onMounted(async () => {
  await loadTenants()
  await load()
})
</script>

<style scoped>
.block,
.toolbar-card {
  margin-bottom: 16px;
}

.metric-grid,
.queue-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
}

.metric-grid.compact {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-note,
.muted,
.tool-line {
  color: #667085;
  font-size: 13px;
  margin-top: 6px;
}

.plan-result {
  margin-top: 16px;
}

.non-goal {
  margin-bottom: 8px;
}

.queue-card {
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 8px;
  box-shadow: 0 10px 24px rgba(16, 24, 40, 0.04);
  cursor: pointer;
  min-height: 116px;
  padding: 16px;
  text-align: left;
}

.queue-card.active {
  border-color: #1769ff;
}

.queue-card span,
.queue-card em {
  color: #667085;
  display: block;
  font-style: normal;
  font-size: 13px;
}

.queue-card strong {
  color: #07111f;
  display: block;
  font-size: 30px;
  line-height: 1.25;
  margin: 8px 0 4px;
}

.dimension-list {
  display: grid;
  gap: 10px;
}

.dimension-row,
.alert-row {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.alert-row {
  border-bottom: 1px solid #edf1f6;
  justify-content: flex-start;
  padding: 10px 0;
}

.alert-row span,
.empty-text {
  color: #667085;
  display: block;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .metric-grid,
  .queue-grid,
  .metric-grid.compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .metric-grid,
  .queue-grid,
  .metric-grid.compact {
    grid-template-columns: 1fr;
  }
}
</style>
