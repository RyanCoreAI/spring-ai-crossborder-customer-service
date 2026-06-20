<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">知识库管理</h2>
      <el-button type="primary" @click="showDialog(null)" :icon="Plus">添加文档</el-button>
    </div>

    <el-card>
      <div class="filter-bar">
        <el-select v-model="filterTenantId" placeholder="按租户筛选" style="width:200px" @change="onTenantChange">
          <el-option v-for="t in tenants" :key="t.id" :label="t.storeName" :value="t.id" />
        </el-select>
        <el-select v-model="filterDocType" placeholder="按类型筛选" clearable style="width:160px;margin-left:12px" @change="loadData">
          <el-option label="退款政策" value="REFUND_POLICY" />
          <el-option label="物流政策" value="SHIPPING_POLICY" />
          <el-option label="FAQ" value="FAQ" />
          <el-option label="产品指南" value="PRODUCT_GUIDE" />
          <el-option label="隐私政策" value="PRIVACY_POLICY" />
        </el-select>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="margin-top:16px">
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="docType" label="类型" width="120" />
        <el-table-column prop="language" label="语言" width="80" />
        <el-table-column prop="chunkCount" label="分块数" width="80" />
        <el-table-column prop="vectorSynced" label="向量化" width="80">
          <template #default="{ row }">
            <el-tag :type="row.vectorSynced ? 'success' : 'warning'" size="small">
              {{ row.vectorSynced ? '已同步' : '未同步' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retrievalCount" label="检索次数" width="90" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已发布' : row.status === 0 ? '草稿' : '归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="prev, pager, next, total" @current-change="loadData" style="margin-top:16px;justify-content:flex-end" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingUuid ? '编辑文档' : '添加文档'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属租户" prop="tenantId">
          <el-select v-model="form.tenantId" style="width:100%" :disabled="!!editingUuid">
            <el-option v-for="t in tenants" :key="t.id" :label="`${t.storeName} (${t.tenantCode})`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档类型" prop="docType">
          <el-select v-model="form.docType" style="width:100%">
            <el-option label="退款政策" value="REFUND_POLICY" />
            <el-option label="物流政策" value="SHIPPING_POLICY" />
            <el-option label="FAQ" value="FAQ" />
            <el-option label="产品指南" value="PRODUCT_GUIDE" />
            <el-option label="隐私政策" value="PRIVACY_POLICY" />
            <el-option label="服务条款" value="TERMS_OF_SERVICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="语言" prop="language">
          <el-select v-model="form.language" style="width:100%">
            <el-option label="English" value="en" />
            <el-option label="Español" value="es" />
            <el-option label="日本語" value="ja" />
            <el-option label="简体中文" value="zh" />
            <el-option label="Deutsch" value="de" />
            <el-option label="Français" value="fr" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="rawContent">
          <el-input v-model="form.rawContent" type="textarea" :rows="8" placeholder="政策文档或FAQ内容..." />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">发布</el-radio>
            <el-radio :value="0">草稿</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          {{ editingUuid ? '保存' : '创建' }}
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
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

const loading = ref(false)
const saving = ref(false)
const tableData = ref<any[]>([])
const tenants = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterTenantId = ref<number | null>(null)
const filterDocType = ref('')
const dialogVisible = ref(false)
const editingUuid = ref<string | null>(null)
const formRef = ref()

const form = reactive({
  tenantId: null as number | null,
  docType: 'FAQ',
  title: '',
  summary: '',
  language: 'en',
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

function showDialog(row: any) {
  if (row) {
    editingUuid.value = row.docUuid
    form.tenantId = row.tenantId
    form.docType = row.docType
    form.title = row.title
    form.summary = row.summary || ''
    form.language = row.language
    form.rawContent = ''
    form.priority = row.priority || 0
    form.status = row.status || 1
  } else {
    editingUuid.value = null
    form.tenantId = filterTenantId.value || tenants.value[0]?.id || null
    form.docType = 'FAQ'
    form.title = ''
    form.summary = ''
    form.language = 'en'
    form.rawContent = ''
    form.priority = 0
    form.status = 1
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingUuid.value) {
      await api.put(`/knowledge/docs/${editingUuid.value}`, form)
      ElMessage.success('更新成功')
    } else {
      await api.post('/knowledge/docs', form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除文档 "${row.title}" 吗？`, '确认删除', { type: 'warning' })
  await api.delete(`/knowledge/docs/${row.docUuid}`)
  ElMessage.success('已删除')
  loadData()
}

onMounted(async () => {
  await loadTenants()
  loadData()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-title { font-size: 22px; color: #303133; }
.filter-bar { display: flex; align-items: center; }
</style>
