# 核心链路 E2E 结果

- 时间: 2026-11-20T06:17:13.438Z
- 环境: http://localhost:5173

| 用例 | 结果 | 说明 |
|---|---|---|
| 1.创建任务 | ✅ 通过 | POST /issues=200 卡片可见=true 4xx=0 |
| 2.拖拽进入进行中 | ✅ 通过 | PUT move=200 列=进行中 status=IN_PROGRESS 计时中=true 4xx=0 |
| 3.发表评论 | ✅ 通过 | POST /comments=200 评论可见=true 4xx=0 |
| 4.上传附件 | ✅ 通过 | POST /attachments=200 文件名可见=true 4xx=0 |
| 5.提交验收 | ✅ 通过 | POST reviews=200 4xx=0 |
| 6.新建 Sprint | ✅ 通过 | POST /sprints=200 4xx=0 |

通过 6/6

## 失败请求

（无）