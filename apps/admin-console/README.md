# 宜捷行后台当前开发源

web/ 是运营后台唯一活跃前端。backend/ 包含管理网页入口与验证测试。默认IAP模式保持原保护；仅服务端显式ADMIN_AUTH_MODE=PASSWORD时改用后台账号密码，会话、角色、Origin及审计仍生效。用户2026-10-26明确授权driver-admin取消Google登录，其他服务IAM保护不变；全部共享业务后端由STATE指定的 apps/cloud-service 统一维护，禁止在此复制业务服务。

本轮ADMIN-20261025-04：登录布局/对比度，专属筛选、分页与同筛选导出，经营及取消/退款等持久数据报表。原源码依据及缺少真实清分/发票等外部能力记录在 coordination/evidence/ADMIN-20261025-04/。原完整后台前端未找到，以原Java与用户截图核对适用范围。
