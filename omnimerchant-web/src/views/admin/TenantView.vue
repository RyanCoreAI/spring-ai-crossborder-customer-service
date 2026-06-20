<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">租户管理</h2>
      <el-button type="primary" @click="showDialog(null)" :icon="Plus">新建租户</el-button>
    </div>

    <el-card>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="tenantCode" label="租户编码" width="140" />
        <el-table-column prop="storeName" label="店铺名称" min-width="160" />
        <el-table-column prop="platform" label="平台" width="100" />
        <el-table-column prop="ownerEmail" label="店主邮箱" min-width="180" />
        <el-table-column prop="subscriptionPlan" label="订阅计划" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'info'" size="small">
              {{ statusMap[row.status] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="monthlyTokenBudget" label="月Token预算" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="prev, pager, next, total" @current-change="loadData" style="margin-top:16px;justify-content:flex-end" />
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑租户' : '新建租户'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="租户编码" prop="tenantCode">
          <el-input v-model="form.tenantCode" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="店铺名称" prop="storeName">
          <el-input v-model="form.storeName" />
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" style="width:100%">
            <el-option label="Shopify" value="shopify" />
            <el-option label="Amazon" value="amazon" />
            <el-option label="WooCommerce" value="woocommerce" />
            <el-option label="TikTok Shop" value="tiktok_shop" />
            <el-option label="Custom" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台店铺ID" prop="externalStoreId">
          <el-input v-model="form.externalStoreId" />
        </el-form-item>
        <el-form-item label="店主邮箱" prop="ownerEmail">
          <el-input v-model="form.ownerEmail" />
        </el-form-item>
        <el-form-item label="店主姓名">
          <el-input v-model="form.ownerName" />
        </el-form-item>
        <el-form-item label="默认语言">
          <el-select v-model="form.defaultLang" style="width:100%">
            <el-option label="English" value="en" />
            <el-option label="Español" value="es" />
            <el-option label="日本語" value="ja" />
            <el-option label="简体中文" value="zh" />
            <el-option label="Deutsch" value="de" />
            <el-option label="Français" value="fr" />
          </el-select>
        </el-form-item>
        <el-form-item label="订阅计划">
          <el-select v-model="form.subscriptionPlan" style="width:100%">
            <el-option label="Free" value="FREE" />
            <el-option label="Basic" value="BASIC" />
            <el-option label="Pro" value="PRO" />
            <el-option label="Enterprise" value="ENTERPRISE" />
          </el-select>
        </el-form-item>
        <el-form-item label="月Token预算">
          <el-input-number v-model="form.monthlyTokenBudget" :min="10000" :step="100000" style="width:100%" />
        </el-form-item>
        <el-form-item label="QPS限制">
          <el-input-number v-model="form.qpsLimit" :min="1" :max="200" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          {{ editingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
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

const form = reactive({
  tenantCode: '', storeName: '', platform: 'shopify', externalStoreId: '',
  ownerEmail: '', ownerName: '', ownerPhone: '', ownerCountry: '',
  defaultLang: 'en', subscriptionPlan: 'FREE', monthlyTokenBudget: 100000, qpsLimit: 5,
})

const rules = {
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  storeName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  externalStoreId: [{ required: true, message: '请输入平台店铺ID', trigger: 'blur' }],
  ownerEmail: [{ required: true, message: '请输入店主邮箱', trigger: 'blur' }],
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

function showDialog(row: any) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      tenantCode: row.tenantCode || '',
      storeName: row.storeName || '',
      platform: row.platform || 'shopify',
      externalStoreId: row.externalStoreId || '',
      ownerEmail: row.ownerEmail || '',
      ownerName: row.ownerName || '',
      defaultLang: row.defaultLang || 'en',
      subscriptionPlan: row.subscriptionPlan || 'FREE',
      monthlyTokenBudget: row.monthlyTokenBudget || 100000,
      qpsLimit: row.qpsLimit || 5,
    })
  } else {
    editingId.value = null
    Object.assign(form, {
      tenantCode: '', storeName: '', platform: 'shopify', externalStoreId: '',
      ownerEmail: '', ownerName: '', ownerPhone: '', ownerCountry: '',
      defaultLang: 'en', subscriptionPlan: 'FREE', monthlyTokenBudget: 100000, qpsLimit: 5,
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await api.put(`/tenants/${editingId.value}`, form)
      ElMessage.success('更新成功')
    } else {
      await api.post('/tenants', form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除租户 "${row.storeName}" 吗？`, '确认删除', { type: 'warning' })
  await api.delete(`/tenants/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-title { font-size: 22px; color: #303133; }
</style>
