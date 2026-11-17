<template>
  <div class="face-manage">
    <h3>人脸管理</h3>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <span>已注册人员 ({{ users.length }})</span>
            <el-button size="small" style="float:right" @click="fetchUsers">刷新</el-button>
          </template>
          <el-table :data="users" stripe empty-text="暂无注册人员">
            <el-table-column label="姓名" prop="name" />
            <el-table-column label="注册时间" width="180">
              <template #default="{ row }">{{ row.registeredAt ? new Date(row.registeredAt).toLocaleString('zh-CN') : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-popconfirm title="确认删除？" @confirm="handleDelete(row.name)">
                  <template #reference>
                    <el-button size="small" type="danger" link>删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>注册新人员</template>
          <el-form label-width="80px">
            <el-form-item label="姓名">
              <el-input v-model="newName" placeholder="输入人员姓名" :disabled="uploading" />
            </el-form-item>
            <el-form-item label="照片">
              <div class="upload-area">
                <el-upload :show-file-list="false" :before-upload="handleUpload" accept="image/*">
                  <el-button><el-icon><Picture /></el-icon>选择照片</el-button>
                </el-upload>
                <el-tag v-if="previewUrl" type="success">已选择</el-tag>
                <el-tag v-else type="info">未选择</el-tag>
                <img v-if="previewUrl" :src="previewUrl" class="thumb" />
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRegister" :loading="uploading" :disabled="!previewUrl || !newName.trim()">
                {{ uploading ? '注册中...' : '提交注册' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top:16px" v-loading="recordsLoading">
          <template #header>识别记录</template>
          <el-table :data="records" stripe size="small" empty-text="暂无记录">
            <el-table-column label="姓名" prop="personName" width="80" />
            <el-table-column label="设备" prop="deviceCode" width="100" />
            <el-table-column label="位置" prop="location" width="100" />
            <el-table-column label="置信度" width="80">
              <template #default="{ row }">{{ (row.confidence * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString('zh-CN') : '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { getFaceUsersApi, deleteFaceUserApi, registerFaceApi, getFaceRecordsApi } from '@/api/face'

const loading = ref(false)
const recordsLoading = ref(false)
const users = ref<{ name: string; registeredAt?: string }[]>([])
const records = ref<any[]>([])
const newName = ref('')
const previewUrl = ref('')
const uploading = ref(false)
let selectedFile: File | null = null

onMounted(() => { fetchUsers(); fetchRecords() })

async function fetchUsers() {
  loading.value = true
  try {
    const res = await getFaceUsersApi()
    const body = res.data as { code: number; data: { name: string; registeredAt?: string }[] }
    users.value = body.data || []
  } catch { /* */ }
  finally { loading.value = false }
}

async function fetchRecords() {
  recordsLoading.value = true
  try {
    const res = await getFaceRecordsApi()
    const body = res.data as { code: number; data: any[] }
    records.value = body.data || []
  } catch { /* */ }
  finally { recordsLoading.value = false }
}

async function handleDelete(name: string) {
  try {
    await deleteFaceUserApi(name)
    ElMessage.success('已删除')
    await fetchUsers()
  } catch {
    ElMessage.error('删除失败')
  }
}

function handleUpload(file: File) {
  previewUrl.value = URL.createObjectURL(file)
  selectedFile = file
  return false
}

async function handleRegister() {
  const name = newName.value.trim()
  if (!name || !selectedFile) return
  uploading.value = true
  try {
    await registerFaceApi(name, selectedFile)
    ElMessage.success('注册成功')
    newName.value = ''
    previewUrl.value = ''
    selectedFile = null
    await fetchUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '注册失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.face-manage h3 { margin-bottom: 16px; }
.upload-area { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.thumb { max-width: 120px; max-height: 120px; border-radius: 6px; border: 1px solid #e4e7ed; }
</style>
