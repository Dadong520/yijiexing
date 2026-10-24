# 当前项目状态

更新时间：2026-10-25（Asia/Singapore）。协调者唯一维护；历史聊天仅作证据。

## 当前发布
- 私有仓库 Dadong520/yijiexing；业务发布基线 e89f90b0a63b72dc04fca5b3690a8897de1543c7；协作入口已通过PR #2合并，当前任务以BOARD为准。
- 后台 https://driver-admin-491829048305.us-west1.run.app/ ，Google账号 dadonggev587@gmail.com + 独立管理账号；凭据仅本机.private。
- driver-demo: v8镜像7d71539... / driver-demo-00005-2w2。
- driver-admin: admin-browser镜像037b157... / driver-admin-browser1，保留IAP/IAM。后端升级必须保留 iap_browser.py 和完整管理网页，不可覆盖成旧管理原型。
- 独立项目 yijiexing-driver-demo-1023/us-west1，两服务 automatic/min0/max1/请求计费，不保活、不工作结束自动暂停。最新允许测试后由协调者统一部署。

## 文件与所有权
- 原工作区：/Users/dongge/Documents/New project 2（未初始化为统一Git仓库），所有Codex主会话先看此文件；原客户端目录不得互相覆盖。
- 反重力：/Users/dongge/Documents/yijiexing-antigravity，独立Git工作区；当前特性分支 antigravity/passenger-driver-enhancement。
- 仓库内当前司机目录 candidates/driver-0.3.6-r2/android；小程序 candidates/passenger-miniprogram-0.1.0/miniprogram；后台 candidates/admin-v8/yijiexing-admin-20261023。
- 共享后端当前参考 candidates/shared-backend-v8/cloud-service；它目前仍是冻结参考。任何新后端任务先明确新的可写目录（建议 services/backend，复制后校验基线哈希），未建契约前不准改多个副本或部署新账务。
- 原Java仅参考，不是部署后端。乘客原生App暂停，继续微信小程序；没有正式AppID，未真机验收。

## 当前任务与验收
- AG-20261025-01: 反重力 fd44ec08...（29个文件）进入验收，50项现有小程序测试通过不等于新业务通过。
- 独立审查阻塞：客户端券/积分非服务端账务；首页固定28元与优惠加价不兑现；WAIT_CONTINUE操作丢失；取消失败退券；预付发完单积分；虚假录音/资质/警方联动；本地存储跨账号；投诉附件退化。
- 先修复回归与误导展示，再验收；不将该批当作已上线，不覆盖当前正常云服务。
- 本轮分工：antigravity主修前端；backend主会话给营销接口与账务契约；frontend主会话负责司机Kotlin复核及后续UI验收；coordinator负责独立验收与发布。
- 每个角色只写自己的交接；BOARD与STATE由协调者统一更新；任务证据按SHA留档。

## 未验收与保留数据
- 微信编译/真机/腾讯地图插件未配置；本机预览不等于微信真机。
- 保留PORD9dac969cecf1 WAIT_PAY ¥29.26与PORDaa584245c6e6 AVAILABLE；不为测试清空用户保留数据。
- 云后台已实际Chrome登录、18单查询/CSV与只读权限；未声明其他实体设备实测。本轮无新三端全流程证明。

## 角色会话
- coordinator: 开发宜捷行后台管理端 / 01a168e6-d76c-7ef0-8c87-706cc6e32d57
- frontend: 开发宜捷行司机端 / 01a167c8-a5eb-77e3-b77e-214927a029da
- backend: 开发宜捷行乘客端 Android App / 01a168e2-22f7-70a2-af7f-4fb6be0ea695
- antigravity: Yijiexing Project Collaboration Workflow / 559ad4bb-7b06-4120-93a6-f437888ab1ef

## 执行边界
- 已安装项目规则与CI；当前GitHub私库套餐的分支保护API返回403（需Pro或公开），故保留私库、不升级收费。CI可检查但不能声称服务器已强制禁止绕过；协调者是唯一验收/合并/发布责任人。
- 不安排常驻AI或定时空转；只唤醒有明确任务的角色，任务结束保存交接后idle。

## 本轮交接进度
- frontend已ACK并完成只读复核，原司机新增统计/服务卡仍FAIL，已idle。
- backend已ACK并交付营销持久化最小契约及4组源码核对；仅文档完成，业务实现/验收/部署NOT_RUN，已idle。
- 反重力桌面实际读取AGENTS/STATE/任务/问题单后开始修复；等待新SHA独立验收。
