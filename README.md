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
- **导入预检与重复识别**：相同医保编号、就诊日期、项目编码和金额的记录自动标记为疑似重复；与历史批次已成功上送记录重复的数据单独提示；用户确认后才正式导入（重复判断全部在后端完成，前端仅展示）
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

- `POST /api/excel/precheck` - 导入预检（解析文件并检测重复，结果暂存30分钟）
- `POST /api/excel/import/confirm` - 确认导入（用户确认预检结果后正式入库）
- `GET /api/excel/records` - 获取导入记录
- `GET /api/excel/data/{batchNo}` - 获取批次数据
- `GET /api/excel/template` - 下载导入模板
- `POST /api/excel/report/{batchNo}` - 上报数据到国家平台
- `GET /api/excel/report/failed/{batchNo}` - 获取上报失败数据
- `POST /api/excel/report/retry/{batchNo}` - 重试上报
- `GET /api/excel/export/errors/{batchNo}` - 导出错误数据

## 重复识别规则

重复判断维度：**医保编号 + 就诊日期 + 项目编码 + 金额**（四者完全相同视为重复，判断全部在后端完成）

| 重复类型 | 说明 | 标记 |
| -------- | ---- | ---- |
| 文件内疑似重复 | 同一文件内相同记录出现多次（同组首次出现视为正常，后续标记为重复） | duplicateType=1 |
| 历史批次已上送 | 相同记录在历史批次中已成功上送（report_status=1），提示已上送批次号 | duplicateType=2 |

导入流程：

1. 上传文件 → `POST /api/excel/precheck` 预检，返回疑似重复与历史已上送明细
2. 前端展示预检结果，用户选择重复数据处理方式：
   - `EXCLUDE`：排除疑似重复数据，仅导入正常数据（默认）
   - `INCLUDE`：全部导入（疑似重复数据入库后保留重复标记，便于追溯）
3. `POST /api/excel/import/confirm` 确认导入，数据正式入库

归一化处理：就诊日期统一归一化为 `yyyy-MM-dd`（兼容 yyyy/M/d、日期型单元格等写法）；金额去除末尾多余的零（100.00 与 100.0 视为相同）。

## 数据导入模板

| 字段     | 说明                         | 是否必填 |
| -------- | ---------------------------- | -------- |
| 数据编号 | 唯一标识                     | 是       |
| 姓名     | 姓名（最多50字符）           | 是       |
| 身份证号 | 18位身份证号                 | 否       |
| 手机号   | 11位手机号                   | 否       |
| 金额     | 数值，不能为负               | 否       |
| 地址     | 地址（最多200字符）          | 否       |
| 备注     | 备注信息                     | 否       |
| 医保编号 | 医保编号（重复判断维度）     | 是       |
| 就诊日期 | 就诊日期（重复判断维度）     | 是       |
| 项目编码 | 项目编码（重复判断维度）     | 是       |

## 注意事项

1. 系统使用EasyExcel的SAX模式解析Excel，内存占用低，支持大文件
2. 数据每1000条批量入库，保证性能
3. 上报国家平台为模拟功能，会随机产生5%的失败率用于测试异常处理
4. 密码使用BCrypt加密存储，与数据库密码加密方式一致
