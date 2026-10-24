# 云后台浏览器入口实测（2026-10-24）

测试执行：Codex；这是本次入口变更测试，不替代历史反重力独立报告。

- 新增签名登录与真实 HTTP 专项测试：8/8 通过。包含正确 ES256、伪造/篡改/过期/错误受众/错误签发者/未授权邮箱、证书不可用、跨站与无来源 POST、独立管理会话、只读权限与旧管理入口封禁。
- 页面 JavaScript 语法检查通过；云镜像构建成功。
- Chrome 实际访问 HTTPS 云网址，Google 登录选择确认账号，随后 admin 登录成功。界面显示原有 18 条共享订单，统计为全部时间共享订单快照，不代表真实营收。
- 搜索 PORD9dac969cecf1，详情正确显示 WAIT_PAY / ¥29.26、关联司机乘客、原操作时间线和不占司机新接单资格的提示。保留订单未付款、未调价、未取消。
- 实际点击导出，浏览器下载 CSV 为 18 条数据，2657 字节，SHA-256 ec0041d5f9bb712216b60201c527e9c0fc8dde072b184d58589da4ac0260e93e。自动化下载事件观察超时，但下载目录中已找到实际文件并逐行计数，不以成功提示代替文件证据。
- qa_viewer 真实登录成功，导出按钮禁用；进入账号与权限页面显示“权限不足”；退出成功。本地 HTTP 测试另验证调整费用/管理账号返回403。
- 无 Cookie/无令牌请求首页与 state API 均302到 accounts.google.com，未返回管理数据。
- 云端读回两服务 automatic / min0 / max1 / 请求计费；driver-admin IAP开启、无公开调用者，新修订 driver-admin-browser1 为100%流量，driver-demo镜像未改变。

证据：qa/cloud-browser-overview-20261024.png、cloud-browser-waitpay-20261024.png、cloud-browser-viewer-denied-20261024.png、cloud-browser-anonymous-20261024.json、cloud-browser-export-20261024.json、iap-browser-tests-20261024.log；最终配置 delivery/cloud-on-demand-20261024.json。

限制：本轮在当前 Mac Chrome 访问独立云网址，未使用其他实体设备实测；部署与入口不再依赖本机代理。未重做司机/小程序三端全流程，也未声称反重力测试过本次新入口。未接真实支付或更改任何业务订单与钱包金额，登录/退出/导出会产生正常审计及会话记录。
