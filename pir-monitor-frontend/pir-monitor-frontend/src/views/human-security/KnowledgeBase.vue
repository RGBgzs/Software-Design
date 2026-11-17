<template>
  <div class="kb-page">
    <div class="kb-header">
      <div>
        <h2 class="kb-title">知识库管理</h2>
        <p class="kb-desc">管理 AI 知识库条目，用于智能问答</p>
      </div>
      <div class="kb-actions">
        <el-button :icon="RefreshRight" @click="loadList" :loading="loading">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openAdd">新增知识</el-button>
      </div>
    </div>

    <div class="kb-toolbar">
      <el-input v-model="keyword" placeholder="搜索标题..." clearable @clear="loadList" @keyup.enter="loadList" style="width: 300px" :prefix-icon="Search" />
    </div>

    <div v-if="list.length" v-loading="loading" class="kb-table-card">
      <el-table :data="list" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column label="内容" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">{{ truncate(row.content, 100) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ fmt(row.updateTime || row.updatedAt || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id!)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-else-if="!loading" class="kb-empty-wrap">
      <div class="kb-empty-card">
        <el-icon :size="64" color="#cbd5e1"><Collection /></el-icon>
        <h3>暂无知识条目</h3>
        <p>点击「新增知识」添加第一条知识，用于 AI 智能问答</p>
        <el-button type="primary" :icon="Plus" @click="openAdd">新增知识</el-button>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑知识' : '新增知识'" width="560px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="标题" required><el-input v-model="form.title" placeholder="知识标题" /></el-form-item>
        <el-form-item label="分类" required><el-input v-model="form.category" placeholder="如：安防、设备、告警" /></el-form-item>
        <el-form-item label="内容" required><el-input v-model="form.content" type="textarea" :rows="6" placeholder="知识内容..." /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, RefreshRight, Search, Collection } from '@element-plus/icons-vue'
import { getKnowledgeList, addKnowledge, updateKnowledge, deleteKnowledge } from '@/api/humanSecurity'

const loading = ref(false); const saving = ref(false); const keyword = ref('')
const list = ref<any[]>([]); const dialogVisible = ref(false); const editingId = ref<number | null>(null)
const form = reactive({ title: '', category: '', content: '' })

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const res = await getKnowledgeList(keyword.value || undefined)
    const data = res.data?.data || res.data
    list.value = Array.isArray(data) ? data : (data?.records || data || [])
  } catch { list.value = [] } finally { loading.value = false }
}

function openAdd() { editingId.value = null; form.title = ''; form.category = ''; form.content = ''; dialogVisible.value = true }
function openEdit(row: any) { editingId.value = row.id; form.title = row.title || ''; form.category = row.category || ''; form.content = row.content || ''; dialogVisible.value = true }

async function handleSave() {
  if (!form.title || !form.category || !form.content) { ElMessage.warning('请填写完整'); return }
  saving.value = true
  try {
    editingId.value ? await updateKnowledge({ id: editingId.value, title: form.title, category: form.category, content: form.content }) : await addKnowledge({ title: form.title, category: form.category, content: form.content })
    ElMessage.success(editingId.value ? '更新成功' : '添加成功')
    dialogVisible.value = false; loadList()
  } catch { ElMessage.error('操作失败') } finally { saving.value = false }
}

async function handleDelete(id: number) { try { await deleteKnowledge(id); ElMessage.success('已删除'); loadList() } catch { ElMessage.error('删除失败') } }

function truncate(t: string, max: number) { return t?.length > max ? t.slice(0, max) + '...' : (t || '-') }
function fmt(t?: string) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }
</script>

<style scoped>
.kb-page { width: 100%; }
.kb-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.kb-title { font-size: 18px; font-weight: 700; color: #0f172a; margin: 0; }
.kb-desc { font-size: 13px; color: #64748b; margin: 4px 0 0; }
.kb-actions { display: flex; gap: 10px; }
.kb-toolbar { margin-bottom: 16px; }
.kb-table-card { background: #fff; border-radius: 14px; box-shadow: 0 2px 12px rgba(0,0,0,.04); overflow: hidden; }
.kb-empty-wrap { display: flex; justify-content: center; padding: 40px 0; }
.kb-empty-card { background: #fff; border-radius: 16px; padding: 80px 60px; text-align: center; box-shadow: 0 2px 12px rgba(0,0,0,.04); width: 100%; }
.kb-empty-card h3 { font-size: 16px; color: #0f172a; margin: 16px 0 8px; }
.kb-empty-card p { font-size: 13px; color: #94a3b8; margin-bottom: 20px; }
</style>
