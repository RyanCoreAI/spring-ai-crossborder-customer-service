<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">租户管理</h2>
        <p class="page-subtitle">管理店铺、订阅额度和租户状态，所有业务数据都会按租户隔离。</p>
      </div>
      <a-button type="primary" @click="showDialog(null)">
        <template #icon><PlusOutlined /></template>
        新建租户
      </a-button>
    </div>

    <a-card>
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusMap[record.status] || '未知' }}
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
      :title="editingId ? '编辑租户' : '新建租户'"
      :confirm-loading="saving"
      width="680px"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="租户编码" name="tenantCode">
              <a-input v-model:value="form.tenantCode" :disabled="!!editingId" placeholder="请输入租户编码" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="店铺名称" name="storeName">
              <a-input v-model:value="form.storeName" placeholder="请输入店铺名称" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="平台" name="platform">
              <a-select v-model:value="form.platform">
                <a-select-option value="shopify">Shopify</a-select-option>
                <a-select-option value="amazon">Amazon</a-select-option>
                <a-select-option value="woocommerce">WooCommerce</a-select-option>
                <a-select-option value="tiktok_shop">TikTok Shop</a-select-option>
                <a-select-option value="custom">自定义</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="平台店铺 ID" name="externalStoreId">
              <a-input v-model:value="form.externalStoreId" placeholder="外部平台店铺标识" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="店主邮箱" name="ownerEmail">
              <a-input v-model:value="form.ownerEmail" placeholder="owner@example.com" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="店主姓名">
              <a-input v-model:value="form.ownerName" placeholder="负责人姓名" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="默认语言">
              <a-select v-model:value="form.defaultLang">
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
            <a-form-item label="订阅计划">
              <a-select v-model:value="form.subscriptionPlan">
                <a-select-option value="FREE">免费版</a-select-option>
                <a-select-option value="BASIC">基础版</a-select-option>
                <a-select-option value="PRO">专业版</a-select-option>
                <a-select-option value="ENTERPRISE">企业版</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="月 Token 预算">
              <a-input-number
                v-model:value="form.monthlyTokenBudget"
                :min="10000"
                :step="100000"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="QPS 限制">
              <a-input-number v-model:value="form.qpsLimit" :min="1" :max="200" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import api from '@/api'

const loading = ref(false)
const saving = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

const statusMap: Record<number, string> = { 0: '停用', 1: '启用', 2: '试用中', 3: '欠费暂停', 4: '封禁' }

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '租户编码', dataIndex: 'tenantCode', width: 140 },
  { title: '店铺名称', dataIndex: 'storeName', ellipsis: true },
  { title: '平台', dataIndex: 'platform', width: 120 },
  { title: '店主邮箱', dataIndex: 'ownerEmail', ellipsis: true },
  { title: '订阅计划', dataIndex: 'subscriptionPlan', width: 110 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '月 Token 预算', dataIndex: 'monthlyTokenBudget', width: 140 },
  { title: '创建时间', dataIndex: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 150, fixed: 'right' },
]

const form = reactive({
  tenantCode: '',
  storeName: '',
  platform: 'shopify',
  externalStoreId: '',
  ownerEmail: '',
  ownerName: '',
  ownerPhone: '',
  ownerCountry: '',
  defaultLang: 'zh',
  subscriptionPlan: 'FREE',
  monthlyTokenBudget: 100000,
  qpsLimit: 5,
})

const rules = {
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  storeName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  externalStoreId: [{ required: true, message: '请输入平台店铺 ID', trigger: 'blur' }],
  ownerEmail: [{ required: true, message: '请输入店主邮箱', trigger: 'blur' }],
}

function statusColor(status: number) {
  if (status === 1) return 'green'
  if (status === 2) return 'gold'
  if (status === 3) return 'orange'
  if (status === 4) return 'red'
  return 'default'
}

async function loadData() {
  loading.value = true
  try {
    const res = await api.get('/tenants', { params: { page: page.value, size: size.value } })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    tenantCode: '',
    storeName: '',
    platform: 'shopify',
    externalStoreId: '',
    ownerEmail: '',
    ownerName: '',
    ownerPhone: '',
    ownerCountry: '',
    defaultLang: 'zh',
    subscriptionPlan: 'FREE',
    monthlyTokenBudget: 100000,
    qpsLimit: 5,
  })
}

function showDialog(row: any) {
  formRef.value?.clearValidate?.()
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      tenantCode: row.tenantCode || '',
      storeName: row.storeName || '',
      platform: row.platform || 'shopify',
      externalStoreId: row.externalStoreId || '',
      ownerEmail: row.ownerEmail || '',
      ownerName: row.ownerName || '',
      defaultLang: row.defaultLang || 'zh',
      subscriptionPlan: row.subscriptionPlan || 'FREE',
      monthlyTokenBudget: row.monthlyTokenBudget ?? 100000,
      qpsLimit: row.qpsLimit ?? 5,
    })
  } else {
    editingId.value = null
    resetForm()
  }
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await api.put(`/tenants/${editingId.value}`, form)
      message.success('租户已更新')
    } else {
      await api.post('/tenants', form)
      message.success('租户已创建')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

function handleDelete(row: any) {
  Modal.confirm({
    title: '确认删除租户？',
    content: `租户“${row.storeName}”删除后将无法在后台继续管理。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      await api.delete(`/tenants/${row.id}`)
      message.success('租户已删除')
      await loadData()
    },
  })
}

onMounted(loadData)
</script>
