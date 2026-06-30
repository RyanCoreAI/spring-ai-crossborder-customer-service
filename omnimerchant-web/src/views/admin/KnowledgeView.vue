<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">知识库管理</h2>
        <p class="page-subtitle">维护政策、FAQ 和产品指南，向量索引只处理已审核内容。</p>
      </div>
      <a-space>
        <a-button @click="router.push('/chat')">打开知识库对话</a-button>
        <a-button type="primary" @click="showDialog(null)">
          <template #icon><PlusOutlined /></template>
          添加文档
        </a-button>
      </a-space>
    </div>

    <a-card>
      <div class="toolbar">
        <a-select
          v-model:value="filterTenantId"
          show-search
          placeholder="按租户筛选"
          style="width: 240px"
          option-filter-prop="label"
          @change="onTenantChange"
        >
          <a-select-option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id" :label="tenantOptionLabel(tenant)">
            {{ tenantOptionLabel(tenant) }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filterDocType"
          allow-clear
          placeholder="按类型筛选"
          style="width: 180px"
          @change="loadData"
        >
          <a-select-option value="REFUND_POLICY">退款政策</a-select-option>
          <a-select-option value="SHIPPING_POLICY">物流政策</a-select-option>
          <a-select-option value="FAQ">FAQ</a-select-option>
          <a-select-option value="PRODUCT_GUIDE">产品指南</a-select-option>
          <a-select-option value="PRIVACY_POLICY">隐私政策</a-select-option>
        </a-select>
      </div>

      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="docUuid"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'docType'">
            <a-tag color="blue">{{ docTypeLabel(record.docType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'vectorSynced'">
            <a-tag :color="record.vectorSynced ? 'green' : 'gold'">
              {{ record.vectorSynced ? '已同步' : '未同步' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : record.status === 0 ? 'default' : 'orange'">
              {{ record.status === 1 ? '已发布' : record.status === 0 ? '草稿' : '归档' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="showDialog(record)">编辑</a-button>
              <a-button size="small" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <div class="pager">
        <a-pagination v-model:current="page" :page-size="size" :total="total" @change="loadData" />
      </div>
    </a-card>

    <a-modal
      v-model:open="dialogVisible"
      :title="editingUuid ? '编辑文档' : '添加文档'"
      :confirm-loading="saving"
      width="720px"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="所属租户" name="tenantId">
              <a-select v-model:value="form.tenantId" :disabled="!!editingUuid" option-filter-prop="label" show-search>
                <a-select-option
                  v-for="tenant in tenants"
                  :key="tenant.id"
                  :value="tenant.id"
                  :label="tenantOptionLabel(tenant)"
                >
                  {{ tenantOptionLabel(tenant) }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="文档类型" name="docType">
              <a-select v-model:value="form.docType">
                <a-select-option value="REFUND_POLICY">退款政策</a-select-option>
                <a-select-option value="SHIPPING_POLICY">物流政策</a-select-option>
                <a-select-option value="FAQ">FAQ</a-select-option>
                <a-select-option value="PRODUCT_GUIDE">产品指南</a-select-option>
                <a-select-option value="PRIVACY_POLICY">隐私政策</a-select-option>
                <a-select-option value="TERMS_OF_SERVICE">服务条款</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="标题" name="title">
          <a-input v-model:value="form.title" placeholder="请输入文档标题" />
        </a-form-item>
        <a-form-item label="摘要">
          <a-textarea v-model:value="form.summary" :rows="2" placeholder="面向客服和审核人员的摘要" />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="语言" name="language">
              <a-select v-model:value="form.language">
                <a-select-option value="zh">简体中文</a-select-option>
                <a-select-option value="en">English</a-select-option>
                <a-select-option value="es">Español</a-select-option>
                <a-select-option value="ja">日本語</a-select-option>
                <a-select-option value="de">Deutsch</a-select-option>
                <a-select-option value="fr">Français</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="优先级">
              <a-input-number v-model:value="form.priority" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="内容" name="rawContent">
          <a-textarea v-model:value="form.rawContent" :rows="8" placeholder="粘贴政策文档、FAQ 或产品指南内容" />
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">发布</a-radio>
            <a-radio :value="0">草稿</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import api from '@/api'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'
import { tenantOptionLabel } from '@/utils/display'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const tableData = ref<any[]>([])
const tenants = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterTenantId = ref<number | null>(null)
const filterDocType = ref<string | undefined>()
const dialogVisible = ref(false)
const editingUuid = ref<string | null>(null)
const formRef = ref()

const columns = [
  { title: '标题', dataIndex: 'title', ellipsis: true },
  { title: '类型', dataIndex: 'docType', key: 'docType', width: 130 },
  { title: '语言', dataIndex: 'language', width: 90 },
  { title: '分块数', dataIndex: 'chunkCount', width: 90 },
  { title: '向量化', dataIndex: 'vectorSynced', key: 'vectorSynced', width: 100 },
  { title: '检索次数', dataIndex: 'retrievalCount', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 140, fixed: 'right' },
]

const form = reactive({
  tenantId: null as number | null,
  docType: 'FAQ',
  title: '',
  summary: '',
  language: 'zh',
  rawContent: '',
  priority: 0,
  status: 1,
})

const rules = {
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  docType: [{ required: true, message: '请选择文档类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  language: [{ required: true, message: '请选择语言', trigger: 'change' }],
  rawContent: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

function docTypeLabel(value: string) {
  const labels: Record<string, string> = {
    REFUND_POLICY: '退款政策',
    SHIPPING_POLICY: '物流政策',
    FAQ: 'FAQ',
    PRODUCT_GUIDE: '产品指南',
    PRIVACY_POLICY: '隐私政策',
    TERMS_OF_SERVICE: '服务条款',
  }
  return labels[value] || value
}

async function loadTenants() {
  const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
  tenants.value = res.data?.records || []
  filterTenantId.value = selectDefaultTenantId(tenants.value)
  setStoredTenantId(filterTenantId.value)
}

function onTenantChange() {
  setStoredTenantId(filterTenantId.value)
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (filterTenantId.value) params.tenantId = filterTenantId.value
    if (filterDocType.value) params.docType = filterDocType.value
    const res = await api.get('/knowledge/docs', { params })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    tenantId: filterTenantId.value || tenants.value[0]?.id || null,
    docType: 'FAQ',
    title: '',
    summary: '',
    language: 'zh',
    rawContent: '',
    priority: 0,
    status: 1,
  })
}

function showDialog(row: any) {
  formRef.value?.clearValidate?.()
  if (row) {
    editingUuid.value = row.docUuid
    Object.assign(form, {
      tenantId: row.tenantId,
      docType: row.docType || 'FAQ',
      title: row.title || '',
      summary: row.summary || '',
      language: row.language || 'zh',
      rawContent: '',
      priority: row.priority ?? 0,
      status: row.status ?? 1,
    })
  } else {
    editingUuid.value = null
    resetForm()
  }
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingUuid.value) {
      await api.put(`/knowledge/docs/${editingUuid.value}`, form)
      message.success('文档已更新')
    } else {
      await api.post('/knowledge/docs', form)
      message.success('文档已创建')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

function handleDelete(row: any) {
  Modal.confirm({
    title: '确认删除文档？',
    content: `文档“${row.title}”删除后需要重新创建才能进入检索。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      await api.delete(`/knowledge/docs/${row.docUuid}`)
      message.success('文档已删除')
      await loadData()
    },
  })
}

onMounted(async () => {
  await loadTenants()
  await loadData()
})
</script>
