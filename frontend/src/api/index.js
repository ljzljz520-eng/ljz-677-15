import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const request = axios.create({
  baseURL,
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      } else {
        ElMessage.error(data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  login: (data) => request.post('/auth/login', data)
}

export const excelApi = {
  // 预检：解析+后端重复识别（文件内重复 + 历史已上送重复），结果落暂存表
  precheck: (file, onProgress) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/excel/precheck', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 300000,
      onUploadProgress: onProgress
    })
  },

  // 分页获取预检清单（重复标记由后端给出）。category: invalid/file/history/clean
  getStaging: (checkNo, params) =>
    request.get(`/excel/staging/${checkNo}`, { params }),

  // 确认导入：无重复自动导入，疑似重复需传勾选的暂存行ID
  confirmImport: (checkNo, confirmedDuplicateIds = []) =>
    request.post(`/excel/confirm/${checkNo}`, { confirmedDuplicateIds }),

  // 取消预检并清理暂存数据
  cancelPrecheck: (checkNo) =>
    request.delete(`/excel/precheck/${checkNo}`),

  getRecords: (params) => request.get('/excel/records', { params }),

  getDataByBatch: (batchNo, params) => request.get(`/excel/data/${batchNo}`, { params }),

  reportData: (batchNo) => request.post(`/excel/report/${batchNo}`),

  getFailedData: (batchNo) => request.get(`/excel/report/failed/${batchNo}`),

  retryReport: (batchNo) => request.post(`/excel/report/retry/${batchNo}`),

  downloadTemplate: () => {
    return `${baseURL}/excel/template`
  },

  exportErrors: (batchNo) => {
    return `${baseURL}/excel/export/errors/${batchNo}`
  }
}

export default request
