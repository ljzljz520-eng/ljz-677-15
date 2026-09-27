# Excel数据导入系统

基于 Spring Boot + Vue 3 的Excel大数据导入系统，支持5万条数据导入及上报国家平台功能。

## 技术栈

- **Frontend**: Vue 3 + Element Plus + Tailwind CSS + Pinia
- **Backend**: Spring Boot 3.2 + MyBatis Plus + EasyExcel
- **Database**: MySQL 8.0
- **Security**: Spring Security + JWT + BCrypt加密

## 核心功能

- Excel文件上传与解析（支持5万条数据，使用EasyExcel SAX模式避免OOM）
- 数据校验与批量导入
- **重复识别（后端完成）**：按「医保编号 + 就诊日期 + 项目编码 + 金额」四要素判重
  - 文件内疑似重复：同一文件内四要素完全相同的记录
  - 历史已上送重复：历史批次中 `report_status=1`（已成功上送）的相同记录
  - 预检阶段数据只落暂存表，疑似重复必须用户在前端勾选确认后才导入，未勾选的跳过
- 数据上报国家平台（模拟）
- 异常数据处理与导出
- 用户登录认证（密码BCrypt加密）

## 启动指南

### 1. 确保 Docker Desktop 已启动

### 2. 在根目录执行

```bash
docker compose up -d --build
```

### 3. 等待容器启动完成（首次构建约3-5分钟）

查看日志：

```bash
docker compose logs -f
```

## 服务地址

| 服务        | 地址                                  |
| ----------- | ------------------------------------- |
| Frontend    | http://localhost:3000                 |
| Backend API | http://localhost:8080                 |
| Swagger文档 | http://localhost:8080/swagger-ui.html |
| Database    | localhost:3306                        |

## 测试账号

| 用户名 | 密码     |
| ------ | -------- |
| admin  | admin123 |

## 项目结构

```
677/
├── backend/                    # Spring Boot后端
│   ├── src/main/java/com/excel/
│   │   ├── config/            # 配置类
│   │   ├── controller/        # 控制器
│   │   ├── dto/               # 数据传输对象
│   │   ├── entity/            # 实体类
│   │   ├── listener/          # EasyExcel监听器
│   │   ├── mapper/            # MyBatis Mapper
│   │   ├── service/           # 服务层
│   │   └── utils/             # 工具类
│   └── Dockerfile
├── frontend/                   # Vue 3前端
│   ├── src/
│   │   ├── api/               # API接口
│   │   ├── assets/            # 静态资源
│   │   ├── components/        # 组件
│   │   ├── router/            # 路由
│   │   ├── stores/            # Pinia状态管理
│   │   └── views/             # 页面
│   └── Dockerfile
└── docker-compose.yml          # 容器编排
```

## API接口

### 认证接口

- `POST /api/auth/login` - 用户登录

### Excel接口

- `POST /api/excel/precheck` - 上传Excel预检（后端校验+识别文件内重复与历史已上送重复，结果落暂存表）
- `GET /api/excel/staging/{checkNo}` - 分页获取预检清单（category: invalid/file/history/clean）
- `POST /api/excel/confirm/{checkNo}` - 确认导入（无重复自动导入；body.confirmedDuplicateIds 为用户确认的疑似重复行ID）
- `DELETE /api/excel/precheck/{checkNo}` - 取消预检并清理暂存数据
- `GET /api/excel/records` - 获取导入记录
- `GET /api/excel/data/{batchNo}` - 获取批次数据（支持 reportStatus 筛选）
- `GET /api/excel/template` - 下载导入模板
- `POST /api/excel/report/{batchNo}` - 上报数据到国家平台
- `GET /api/excel/report/failed/{batchNo}` - 获取上报失败数据
- `POST /api/excel/report/retry/{batchNo}` - 重试上报
- `GET /api/excel/export/errors/{batchNo}` - 导出错误数据

## 重复识别规则

- 判重四要素（规范化后比较，全部在后端计算）：**医保编号 + 就诊日期 + 项目编码 + 金额**
  - 医保编号、项目编码：去除空白并统一转大写
  - 金额：统一保留两位小数（`100` 与 `100.00` 视为相同）
  - 就诊日期：支持 `yyyy-MM-dd`、`yyyy/M/d`、`yyyy.M.d`、`yyyyMMdd`、Excel日期单元格等
- 两类重复：
  1. **文件内疑似重复**：同一上传文件中四要素相同（重复组中第2行起标记）
  2. **历史已上送重复**：与历史批次中已成功上送（`report_status=1`）的记录四要素相同，并回显对应历史批次号/上送时间
- 导入策略（后端强约束，前端仅展示与勾选）：
  - 校验失败行：不导入
  - 无重复行：自动导入
  - 疑似重复行：必须用户显式勾选确认才导入，否则跳过

## 数据导入模板

| 字段     | 说明                              | 是否必填 |
| -------- | --------------------------------- | -------- |
| 数据编号 | 唯一标识                          | 是       |
| 姓名     | 姓名（最多50字符）                | 是       |
| 身份证号 | 18位身份证号                      | 否       |
| 手机号   | 11位手机号                        | 否       |
| 金额     | 数值，不能为负（判重要素）        | 是       |
| 地址     | 地址（最多200字符）               | 否       |
| 备注     | 备注信息                          | 否       |
| 医保编号 | 参保人医保编号（判重要素）        | 是       |
| 就诊日期 | 如 2026-09-27（判重要素）         | 是       |
| 项目编码 | 诊疗/收费项目编码（判重要素）     | 是       |

## 注意事项

1. 系统使用EasyExcel的SAX模式解析Excel，内存占用低，支持大文件
2. 数据每1000条批量入库，保证性能
3. 上报国家平台为模拟功能，会随机产生5%的失败率用于测试异常处理
4. 密码使用BCrypt加密存储，与数据库密码加密方式一致
