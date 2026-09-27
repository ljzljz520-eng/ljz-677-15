<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="card">
      <h1 class="text-2xl font-bold text-gray-800 mb-2">数据导入</h1>
      <p class="text-gray-500">上传Excel文件，系统将自动识别文件内重复及历史已上送记录，确认后才会导入</p>
    </div>

    <!-- 上传区域 -->
    <div class="card">
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

      <!-- 上传按钮 -->
      <div class="mt-6 flex justify-end">
        <el-button
          type="primary"
          size="large"
          :loading="checking"
          :disabled="!selectedFile"
          @click="handlePrecheck"
        >
          <svg v-if="!checking" class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {{ checking ? '正在预检识别重复...' : '上传并识别重复' }}
        </el-button>
      </div>

      <!-- 上传进度 -->
      <div v-if="checking" class="mt-4">
        <el-progress :percentage="uploadProgress" :status="uploadProgress === 100 ? 'success' : ''" />
        <p class="text-sm text-gray-500 mt-2 text-center">正在解析并比对重复数据，请稍候...</p>
      </div>
    </div>

    <!-- 预检结果 -->
    <div v-if="precheckResult" class="card">
      <div class="flex items-center mb-4">
        <div :class="[
          'w-10 h-10 rounded-full flex items-center justify-center mr-3',
          precheckResult.hasDuplicate ? 'bg-yellow-100' : 'bg-green-100'
        ]">
          <svg v-if="precheckResult.hasDuplicate" class="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <svg v-else class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <div>
          <h3 class="text-lg font-semibold text-gray-800">
            {{ precheckResult.hasDuplicate ? '发现疑似重复记录，需确认' : '预检通过' }}
          </h3>
          <p class="text-gray-500 text-sm">{{ precheckResult.message }}</p>
        </div>
      </div>

      <!-- 统计数据 -->
      <div class="grid grid-cols-5 gap-3 mb-4">
        <div class="bg-blue-50 rounded-lg p-3 text-center">
          <p class="text-2xl font-bold text-blue-600">{{ precheckResult.totalCount }}</p>
          <p class="text-gray-500 text-xs mt-1">总行数</p>
        </div>
        <div class="bg-green-50 rounded-lg p-3 text-center">
          <p class="text-2xl font-bold text-green-600">{{ precheckResult.cleanCount }}</p>
          <p class="text-gray-500 text-xs mt-1">无重复可导入</p>
        </div>
        <div class="bg-orange-50 rounded-lg p-3 text-center">
          <p class="text-2xl font-bold text-orange-600">{{ precheckResult.duplicateInFileCount }}</p>
          <p class="text-gray-500 text-xs mt-1">文件内疑似重复</p>
        </div>
        <div class="bg-red-50 rounded-lg p-3 text-center">
          <p class="text-2xl font-bold text-red-600">{{ precheckResult.duplicateInHistoryCount }}</p>
          <p class="text-gray-500 text-xs mt-1">历史已上送重复</p>
        </div>
        <div class="bg-gray-100 rounded-lg p-3 text-center">
          <p class="text-2xl font-bold text-gray-600">{{ precheckResult.invalidCount }}</p>
          <p class="text-gray-500 text-xs mt-1">校验失败(排除)</p>
        </div>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="mb-4"
        title="重复判断规则（由后端完成）：医保编号 + 就诊日期 + 项目编码 + 金额 完全一致。文件内重复、以及历史批次中已成功上送的记录都会被标记；疑似重复记录必须勾选确认后才会导入，未勾选将被跳过。"
      />

      <!-- 重复清单分类Tab -->
      <div class="flex items-center justify-between mb-3">
        <h4 class="font-medium text-gray-700">数据清单</h4>
        <el-radio-group v-model="category" size="small" @change="handleCategoryChange">
          <el-radio-button label="file">文件内重复 ({{ precheckResult.duplicateInFileCount }})</el-radio-button>
          <el-radio-button label="history">历史已上送 ({{ precheckResult.duplicateInHistoryCount }})</el-radio-button>
          <el-radio-button label="invalid">校验失败 ({{ precheckResult.invalidCount }})</el-radio-button>
          <el-radio-button label="clean">无重复 ({{ precheckResult.cleanCount }})</el-radio-button>
        </el-radio-group>
      </div>

      <el-table
        ref="stagingTableRef"
        v-loading="stagingLoading"
        :data="stagingRows"
        stripe
        max-height="420"
        row-key="id"
        @select="handleSelect"
        @select-all="handleSelectAll"
      >
        <el-table-column
          v-if="category === 'file' || category === 'history'"
          type="selection"
          width="48"
          :selectable="canSelect"
        />
        <el-table-column prop="rowIndex" label="Excel行号" width="90" />
        <el-table-column prop="medicalInsuranceNo" label="医保编号" width="170" show-overflow-tooltip />
        <el-table-column prop="visitDate" label="就诊日期" width="110" />
        <el-table-column prop="itemCode" label="项目编码" width="120" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
        <el-table-column prop="dataCode" label="数据编号" width="110" show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column label="重复/校验说明" min-width="260">
          <template #default="{ row }">
            <el-tag :type="getDupTagType(row.duplicateType)" size="small" class="mr-2">
              {{ getDupTypeText(row.duplicateType) }}
            </el-tag>
            <span class="text-gray-600 text-xs">{{ row.duplicateTip }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="stagingPage.pageNum"
          v-model:page-size="stagingPage.pageSize"
          :total="stagingPage.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchStaging"
          @current-change="fetchStaging"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="mt-6 flex justify-end space-x-3">
        <el-button @click="cancelCheck" :disabled="importing">放弃导入</el-button>
        <el-button type="primary" :loading="importing" @click="handleConfirm">
          确认导入（无重复{{ precheckResult.cleanCount }}条自动导入，已勾选{{ selectedIds.length }}条疑似重复）
        </el-button>
      </div>
    </div>

    <!-- 导入结果 -->
    <div v-if="importResult" class="card">
      <div class="flex items-center mb-4">
        <div class="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center mr-3">
          <svg class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <div>
          <h3 class="text-lg font-semibold text-gray-800">导入完成</h3>
          <p class="text-gray-500 text-sm">批次号：{{ importResult.batchNo }}</p>
        </div>
      </div>

      <div class="grid grid-cols-3 gap-4 mb-6">
        <div class="bg-green-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-green-600">{{ importResult.successCount }}</p>
          <p class="text-gray-500 text-sm">成功导入</p>
        </div>
        <div class="bg-gray-100 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-gray-600">{{ importResult.invalidCount || 0 }}</p>
          <p class="text-gray-500 text-sm">校验失败排除</p>
        </div>
        <div class="bg-orange-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-orange-600">{{ importResult.skippedDuplicateCount || 0 }}</p>
          <p class="text-gray-500 text-sm">未确认疑似重复跳过</p>
        </div>
      </div>

      <p class="text-gray-600 text-sm mb-4">{{ importResult.message }}</p>

      <div class="flex justify-end space-x-3">
        <el-button @click="resetImport">继续导入</el-button>
        <el-button type="primary" @click="goToDetail">查看详情</el-button>
      </div>
    </div>

    <!-- 使用说明 -->
    <div class="card">
      <h2 class="text-lg font-semibold text-gray-700 mb-4">使用说明</h2>
      <div class="space-y-3 text-gray-600">
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">1</span>
          <p>下载导入模板，按模板填写数据（医保编号、就诊日期、项目编码、金额为重复判断四要素）</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">2</span>
          <p>上传Excel后，系统在后端自动校验并识别：文件内重复 + 历史批次已成功上送重复</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">3</span>
          <p>在重复清单中勾选确认需要继续导入的疑似记录（跨页选择保留），未勾选的疑似重复将被跳过</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">4</span>
          <p>导入完成后，可在“导入记录”中查看详情并上报数据到国家平台</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { excelApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const uploadRef = ref(null)
const stagingTableRef = ref(null)
const selectedFile = ref(null)
const checking = ref(false)
const importing = ref(false)
const uploadProgress = ref(0)

const precheckResult = ref(null)
const category = ref('file')
const stagingLoading = ref(false)
const stagingRows = ref([])
const stagingPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
// 跨页保留的勾选行ID（仅疑似重复页可勾选）
const selectedRowsMap = reactive({})
const selectedIds = ref([])

const importResult = ref(null)

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
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

// 第一步：预检（后端完成校验与重复识别）
const handlePrecheck = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  checking.value = true
  uploadProgress.value = 0
  precheckResult.value = null
  importResult.value = null
  clearSelectionState()

  try {
    const res = await excelApi.precheck(selectedFile.value, (progressEvent) => {
      if (progressEvent.lengthComputable) {
        uploadProgress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
      }
    })

    uploadProgress.value = 100
    precheckResult.value = res.data

    // 默认展示最需要用户处理的类别
    if (precheckResult.value.duplicateInFileCount > 0) {
      category.value = 'file'
    } else if (precheckResult.value.duplicateInHistoryCount > 0) {
      category.value = 'history'
    } else if (precheckResult.value.invalidCount > 0) {
      category.value = 'invalid'
    } else {
      category.value = 'clean'
    }

    ElMessage.success(res.message)
    await nextTick()
    fetchStaging()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    checking.value = false
  }
}

const clearSelectionState = () => {
  Object.keys(selectedRowsMap).forEach((k) => delete selectedRowsMap[k])
  selectedIds.value = []
  stagingPage.pageNum = 1
}

// 获取某类别清单
const fetchStaging = async () => {
  if (!precheckResult.value?.checkNo) return
  stagingLoading.value = true
  try {
    const res = await excelApi.getStaging(precheckResult.value.checkNo, {
      category: category.value,
      pageNum: stagingPage.pageNum,
      pageSize: stagingPage.pageSize
    })
    stagingRows.value = res.data.records || []
    stagingPage.total = res.data.total || 0
    await nextTick()
    restoreSelection()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    stagingLoading.value = false
  }
}

const handleCategoryChange = () => {
  stagingPage.pageNum = 1
  fetchStaging()
}

// 只有有效且为疑似重复的行可被勾选（invalid/clean不可勾选）
const canSelect = (row) => row.validFlag === 1 &&
  (row.duplicateInFile === 1 || row.duplicateInHistory === 1)

const syncSelectedIds = () => {
  selectedIds.value = Object.keys(selectedRowsMap).map(Number)
}

// 单行勾选/取消
const handleSelect = (selection, row) => {
  const checked = selection.some((r) => r.id === row.id)
  if (checked) {
    selectedRowsMap[row.id] = true
  } else {
    delete selectedRowsMap[row.id]
  }
  syncSelectedIds()
}

// 全选当前页（只影响当前页可勾选行）
const handleSelectAll = (selection) => {
  const selectableRows = stagingRows.value.filter(canSelect)
  if (selection.length > 0) {
    selectableRows.forEach((r) => { selectedRowsMap[r.id] = true })
  } else {
    selectableRows.forEach((r) => delete selectedRowsMap[r.id])
  }
  syncSelectedIds()
}

// 数据加载后回填跨页勾选状态
const restoreSelection = () => {
  stagingRows.value.forEach((row) => {
    if (selectedRowsMap[row.id]) {
      stagingTableRef.value?.toggleRowSelection(row, true)
    }
  })
}

const getDupTagType = (type) => {
  const map = { both: 'danger', file: 'warning', history: 'danger', invalid: 'info', clean: 'success' }
  return map[type] || 'info'
}

const getDupTypeText = (type) => {
  const map = {
    both: '双重重复',
    file: '文件内重复',
    history: '历史已上送',
    invalid: '校验失败',
    clean: '无重复'
  }
  return map[type] || '-'
}

// 第二步：确认导入
const handleConfirm = async () => {
  const cleanCount = precheckResult.value.cleanCount || 0
  const confirmedCount = selectedIds.value.length
  try {
    await ElMessageBox.confirm(
      `无重复的 ${cleanCount} 条将自动导入；已勾选确认 ${confirmedCount} 条疑似重复记录将一并导入，未勾选的疑似重复记录将被跳过。是否继续？`,
      '确认导入',
      {
        confirmButtonText: '确定导入',
        cancelButtonText: '再看看',
        type: 'warning'
      }
    )
  } catch (action) {
    return // 用户取消
  }

  importing.value = true
  try {
    const res = await excelApi.confirmImport(
      precheckResult.value.checkNo,
      selectedIds.value
    )
    importResult.value = res.data
    ElMessage.success(res.message)
    resetPrecheckPanel()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    importing.value = false
  }
}

const resetPrecheckPanel = () => {
  precheckResult.value = null
  stagingRows.value = []
  clearSelectionState()
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  uploadProgress.value = 0
}

// 放弃导入：通知后端清理暂存数据
const cancelCheck = async () => {
  if (!precheckResult.value?.checkNo) return
  try {
    await excelApi.cancelPrecheck(precheckResult.value.checkNo)
    ElMessage.success('已放弃本次导入')
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    resetPrecheckPanel()
  }
}

const downloadTemplate = () => {
  const token = userStore.token
  const url = excelApi.downloadTemplate()
  window.open(`${url}?token=${token}`, '_blank')
}

const resetImport = () => {
  importResult.value = null
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
