<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="card">
      <h1 class="text-2xl font-bold text-gray-800 mb-2">数据导入</h1>
      <p class="text-gray-500">上传Excel文件，系统将自动检测疑似重复数据，确认后再导入</p>
    </div>

    <!-- 上传区域 -->
    <div class="card" v-if="!precheckResult">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-lg font-semibold text-gray-700">上传文件</h2>
        <el-button type="primary" link @click="downloadTemplate">
          <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          下载导入模板
        </el-button>
      </div>

      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        :before-upload="beforeUpload"
        accept=".xlsx,.xls"
      >
        <div class="upload-content py-8">
          <div class="upload-icon mb-4">
            <svg class="w-16 h-16 mx-auto text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
          </div>
          <p class="text-gray-600 mb-2">将Excel文件拖到此处，或<em class="text-blue-500 not-italic">点击上传</em></p>
          <p class="text-gray-400 text-sm">支持 .xlsx、.xls 格式，单个文件最大100MB</p>
        </div>
      </el-upload>

      <!-- 已选文件 -->
      <div v-if="selectedFile" class="mt-4 p-4 bg-gray-50 rounded-lg flex items-center justify-between">
        <div class="flex items-center">
          <div class="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center mr-3">
            <svg class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <div>
            <p class="text-gray-700 font-medium">{{ selectedFile.name }}</p>
            <p class="text-gray-400 text-sm">{{ formatFileSize(selectedFile.size) }}</p>
          </div>
        </div>
        <el-button type="danger" link @click="removeFile">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </el-button>
      </div>

      <!-- 预检按钮 -->
      <div class="mt-6 flex justify-end">
        <el-button
          type="primary"
          size="large"
          :loading="prechecking"
          :disabled="!selectedFile"
          @click="handlePrecheck"
        >
          {{ prechecking ? '预检中...' : '开始预检' }}
        </el-button>
      </div>

      <!-- 上传进度 -->
      <div v-if="prechecking" class="mt-4">
        <el-progress :percentage="uploadProgress" :status="uploadProgress === 100 ? 'success' : ''" />
        <p class="text-sm text-gray-500 mt-2 text-center">正在解析并检测重复数据，请稍候...</p>
      </div>
    </div>

    <!-- 预检结果 -->
    <div v-if="precheckResult && !importResult" class="space-y-6">
      <div class="card">
        <div class="flex items-center mb-4">
          <div :class="[
            'w-10 h-10 rounded-full flex items-center justify-center mr-3',
            precheckResult.hasDuplicates ? 'bg-yellow-100' : 'bg-green-100'
          ]">
            <svg v-if="!precheckResult.hasDuplicates" class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <svg v-else class="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <div>
            <h3 class="text-lg font-semibold text-gray-800">预检完成</h3>
            <p class="text-gray-500 text-sm">{{ precheckResult.message }}</p>
          </div>
        </div>

        <!-- 重复提示 -->
        <el-alert
          v-if="precheckResult.hasDuplicates"
          type="warning"
          :closable="false"
          class="mb-4"
          show-icon
        >
          <template #title>
            检测到疑似重复数据（判断维度：医保编号 + 就诊日期 + 项目编码 + 金额），请核对下方明细后选择处理方式并确认导入
          </template>
        </el-alert>

        <!-- 统计数据 -->
        <div class="grid grid-cols-5 gap-4 mb-6">
          <div class="bg-blue-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-blue-600">{{ precheckResult.totalCount }}</p>
            <p class="text-gray-500 text-sm">总记录数</p>
          </div>
          <div class="bg-green-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-green-600">{{ precheckResult.normalCount }}</p>
            <p class="text-gray-500 text-sm">正常数据</p>
          </div>
          <div class="bg-yellow-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-yellow-600">{{ precheckResult.fileDuplicateCount }}</p>
            <p class="text-gray-500 text-sm">文件内疑似重复</p>
          </div>
          <div class="bg-orange-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-orange-600">{{ precheckResult.historyDuplicateCount }}</p>
            <p class="text-gray-500 text-sm">历史批次已上送</p>
          </div>
          <div class="bg-red-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-red-600">{{ precheckResult.errorCount }}</p>
            <p class="text-gray-500 text-sm">校验失败</p>
          </div>
        </div>

        <!-- 明细标签页 -->
        <el-tabs v-if="hasAnyDetail" v-model="activeTab">
          <el-tab-pane v-if="precheckResult.fileDuplicateList.length > 0" name="file">
            <template #label>
              文件内疑似重复
              <el-badge :value="precheckResult.fileDuplicateCount" type="warning" class="ml-1" />
            </template>
            <el-table :data="precheckResult.fileDuplicateList" stripe max-height="320" size="small">
              <el-table-column prop="rowIndex" label="行号" width="70" />
              <el-table-column prop="dataCode" label="数据编号" width="110" />
              <el-table-column prop="name" label="姓名" width="90" />
              <el-table-column prop="medicalInsuranceNo" label="医保编号" width="130" />
              <el-table-column prop="visitDate" label="就诊日期" width="110" />
              <el-table-column prop="itemCode" label="项目编码" width="110" />
              <el-table-column prop="amount" label="金额" width="110" align="right">
                <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="duplicateRemark" label="重复说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="precheckResult.historyDuplicateList.length > 0" name="history">
            <template #label>
              历史批次已上送
              <el-badge :value="precheckResult.historyDuplicateCount" type="danger" class="ml-1" />
            </template>
            <el-table :data="precheckResult.historyDuplicateList" stripe max-height="320" size="small">
              <el-table-column prop="rowIndex" label="行号" width="70" />
              <el-table-column prop="dataCode" label="数据编号" width="110" />
              <el-table-column prop="name" label="姓名" width="90" />
              <el-table-column prop="medicalInsuranceNo" label="医保编号" width="130" />
              <el-table-column prop="visitDate" label="就诊日期" width="110" />
              <el-table-column prop="itemCode" label="项目编码" width="110" />
              <el-table-column prop="amount" label="金额" width="110" align="right">
                <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="relatedBatchNo" label="已上送批次号" width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag size="small" type="danger">{{ row.relatedBatchNo }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="duplicateRemark" label="重复说明" min-width="180" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="precheckResult.errorList.length > 0" name="error">
            <template #label>
              校验失败
              <el-badge :value="precheckResult.errorCount" type="info" class="ml-1" />
            </template>
            <el-table :data="precheckResult.errorList" stripe max-height="320" size="small">
              <el-table-column prop="rowIndex" label="行号" width="70" />
              <el-table-column prop="dataCode" label="数据编号" width="110" />
              <el-table-column prop="name" label="姓名" width="90" />
              <el-table-column prop="errorMsg" label="错误原因" min-width="240" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>

        <!-- 重复数据处理策略 -->
        <div v-if="precheckResult.hasDuplicates" class="mt-6 p-4 bg-gray-50 rounded-lg">
          <p class="font-medium text-gray-700 mb-3">疑似重复数据处理方式</p>
          <el-radio-group v-model="duplicateStrategy">
            <el-radio value="EXCLUDE">
              <span class="text-gray-700">排除疑似重复数据</span>
              <span class="text-gray-400 text-sm ml-1">（仅导入{{ precheckResult.normalCount }}条正常数据，推荐）</span>
            </el-radio>
            <el-radio value="INCLUDE">
              <span class="text-gray-700">全部导入</span>
              <span class="text-gray-400 text-sm ml-1">（含{{ precheckResult.fileDuplicateCount + precheckResult.historyDuplicateCount }}条疑似重复数据，导入后将被标记）</span>
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 操作按钮 -->
        <div class="mt-6 flex justify-end space-x-3">
          <el-button @click="resetImport">取消，重新上传</el-button>
          <el-button
            type="primary"
            :loading="importing"
            :disabled="precheckResult.normalCount === 0 && duplicateStrategy === 'EXCLUDE'"
            @click="handleConfirmImport"
          >
            {{ importing ? '导入中...' : '确认导入' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 导入结果 -->
    <div v-if="importResult" class="card">
      <div class="flex items-center mb-4">
        <div :class="[
          'w-10 h-10 rounded-full flex items-center justify-center mr-3',
          importResult.failCount === 0 ? 'bg-green-100' : 'bg-yellow-100'
        ]">
          <svg v-if="importResult.failCount === 0" class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          <svg v-else class="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <div>
          <h3 class="text-lg font-semibold text-gray-800">导入完成</h3>
          <p class="text-gray-500 text-sm">批次号：{{ importResult.batchNo }}</p>
        </div>
      </div>

      <!-- 统计数据 -->
      <div class="grid grid-cols-4 gap-4 mb-6">
        <div class="bg-blue-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-blue-600">{{ importResult.totalCount }}</p>
          <p class="text-gray-500 text-sm">总记录数</p>
        </div>
        <div class="bg-green-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-green-600">{{ importResult.successCount }}</p>
          <p class="text-gray-500 text-sm">成功导入</p>
        </div>
        <div class="bg-yellow-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-yellow-600">{{ importResult.excludedDuplicateCount || 0 }}</p>
          <p class="text-gray-500 text-sm">排除疑似重复</p>
        </div>
        <div class="bg-red-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-red-600">{{ importResult.failCount }}</p>
          <p class="text-gray-500 text-sm">导入失败</p>
        </div>
      </div>

      <!-- 错误列表 -->
      <div v-if="importResult.errorList && importResult.errorList.length > 0">
        <h4 class="font-medium text-gray-700 mb-3">错误数据详情</h4>
        <el-table :data="importResult.errorList" stripe max-height="300">
          <el-table-column prop="rowIndex" label="行号" width="80" />
          <el-table-column prop="dataCode" label="数据编号" width="120" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="errorMsg" label="错误原因" />
        </el-table>
      </div>

      <!-- 操作按钮 -->
      <div class="mt-6 flex justify-end space-x-3">
        <el-button @click="resetImport">继续导入</el-button>
        <el-button type="primary" @click="goToDetail">查看详情</el-button>
      </div>
    </div>

    <!-- 使用说明 -->
    <div class="card" v-if="!precheckResult">
      <h2 class="text-lg font-semibold text-gray-700 mb-4">使用说明</h2>
      <div class="space-y-3 text-gray-600">
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">1</span>
          <p>下载导入模板，按照模板格式填写数据（医保编号、就诊日期、项目编码为必填项）</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">2</span>
          <p>上传文件后系统先进行预检：相同医保编号、就诊日期、项目编码和金额的记录将被标记为疑似重复</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">3</span>
          <p>与历史批次中已成功上送记录重复的数据会单独提示，请重点核对</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">4</span>
          <p>确认预检结果后数据才会正式导入，可在"导入记录"中查看详情并上报数据到国家平台</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { excelApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const uploadRef = ref(null)
const selectedFile = ref(null)
const prechecking = ref(false)
const importing = ref(false)
const uploadProgress = ref(0)
const precheckResult = ref(null)
const importResult = ref(null)
const duplicateStrategy = ref('EXCLUDE')
const activeTab = ref('file')

const hasAnyDetail = computed(() => {
  if (!precheckResult.value) return false
  return precheckResult.value.fileDuplicateList.length > 0
    || precheckResult.value.historyDuplicateList.length > 0
    || precheckResult.value.errorList.length > 0
})

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatAmount = (amount) => {
  if (amount === null || amount === undefined) return '-'
  return '¥' + Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const beforeUpload = (file) => {
  const isExcel = file.name.endsWith('.xlsx') || file.name.endsWith('.xls')
  if (!isExcel) {
    ElMessage.error('只能上传Excel文件')
    return false
  }

  const isLt100M = file.size / 1024 / 1024 < 100
  if (!isLt100M) {
    ElMessage.error('文件大小不能超过100MB')
    return false
  }

  return true
}

const removeFile = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

// 第一步：预检（后端检测重复，前端仅展示结果）
const handlePrecheck = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  prechecking.value = true
  uploadProgress.value = 0
  precheckResult.value = null
  importResult.value = null

  try {
    const res = await excelApi.precheck(selectedFile.value, (progressEvent) => {
      if (progressEvent.lengthComputable) {
        uploadProgress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
      }
    })

    uploadProgress.value = 100
    precheckResult.value = res.data

    // 默认展示第一个有数据的标签页
    if (res.data.fileDuplicateList.length > 0) {
      activeTab.value = 'file'
    } else if (res.data.historyDuplicateList.length > 0) {
      activeTab.value = 'history'
    } else if (res.data.errorList.length > 0) {
      activeTab.value = 'error'
    }

    if (res.data.hasDuplicates) {
      ElMessage.warning(res.data.message)
    } else {
      ElMessage.success(res.data.message)
    }
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    prechecking.value = false
  }
}

// 第二步：用户确认后正式导入
const handleConfirmImport = async () => {
  if (!precheckResult.value) return

  importing.value = true
  try {
    const res = await excelApi.confirmImport({
      checkNo: precheckResult.value.checkNo,
      duplicateStrategy: duplicateStrategy.value
    })
    importResult.value = res.data
    ElMessage.success(res.message)
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    importing.value = false
  }
}

const downloadTemplate = () => {
  const token = userStore.token
  const url = excelApi.downloadTemplate()
  window.open(`${url}?token=${token}`, '_blank')
}

const resetImport = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  precheckResult.value = null
  importResult.value = null
  uploadProgress.value = 0
  duplicateStrategy.value = 'EXCLUDE'
}

const goToDetail = () => {
  if (importResult.value?.batchNo) {
    router.push(`/data/${importResult.value.batchNo}`)
  }
}
</script>

<style scoped>
.upload-area :deep(.el-upload-dragger) {
  @apply border-2 border-dashed border-gray-200 rounded-xl transition-all duration-200;
}

.upload-area :deep(.el-upload-dragger:hover) {
  @apply border-blue-400 bg-blue-50;
}

.upload-area :deep(.el-upload-dragger.is-dragover) {
  @apply border-blue-500 bg-blue-100;
}
</style>
