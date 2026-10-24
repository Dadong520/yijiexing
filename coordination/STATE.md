# 宜捷行当前状态
更新时间2026-10-25 02:25 Asia/Singapore。协调者唯一维护；恢复读STATE/BOARD/当前任务/角色handoff。

当前发布：司机0.3.8/code11，乘客Android1.1.8/code10；driver-demo原v8镜像7d71539...未变。后台新UI driver-admin-ui20261025，image35726abba...，100%流量，IAP/Google dadonggev587@gmail.com+admin登录实测18订单，完成7取消9待接1待付1，完成已付16124分。admin https://driver-admin-491829048305.us-west1.run.app/。独立项目yijiexing-driver-demo-1023/us-west1；两服务automatic/min0/max1/请求计费，不保活、不自动暂停。发布无需再审批，唯一发布者coordinator。

源码：私库Dadong520/yijiexing，main已合并PR#9=f0f0920995dcf89dfc5f6125542e26af86cf110d。当前clone .coordination/integration-ag-final，UI源码分支codex/gemini-native-ui-20261025已合并；收尾文档分支codex/ui-release-receipt-20261025。原工作区无统一Git。反重力独立目录/Users/dongge/Documents/yijiexing-antigravity不切分支/覆盖。司机源码candidates/driver-0.3.6-r2/android；乘客apps/passenger-android；mini candidates/passenger-miniprogram-0.1.0/miniprogram；backend candidates/shared-backend-v8/cloud-service冻结参考。原Java仅参考，禁止连接旧环境或导入旧账户/密钥。

UI-20261025-02已完成：Gemini真实生成29个Compose函数及主题；root集成，frontend两端独立限定UI验收PASS(正常/1.3字体、司机首屏抢单/确认取消、工具状态/公司全换行、乘客导航及地址弹窗键盘可达)，backend13核心文件/下单参数/筛选保护PASS。最终55司机/20乘客单测和隔离构建PASS，固定云源/同签名。司机038/passenger118两APK已直连上传并完整公开GET验版本/签名/哈希PASS，分别08732bf8...和5ee70fd1...。回滚备份记录evidence/UI-20261025-02/。产品source55dce79f58a780a1de1ea88c448a774bac03fc9f，PR10合并d8c34cac4e260233f32cbc12db4b900c31d5a9eb。无后端/admin/历史业务数据变更；不是新三端完整流程验收。所有角色和Gemini均idle，无自动续跑。构建缓存已隔离至/Users/dongge/.codex/tmp/yijiexing-native-ui-20261025，避开Documents旧dex/文件读取问题；临时目录不入Git。

保留PORD9dac969cecf1 WAIT_PAY2926与PORDaa584245c6e6 AVAILABLE及18历史单。模拟器5586司机0003/5584乘客0102；frontend当前只读现状UI检查，不接单/支付/取消/清数据。无新完整三端流程验收。

Gemini入口固定 /Applications/Gemini.app/Contents/Helpers/agy（内置CLI），旧.local/bin/agy不是同一二进制，实际hub属Gemini。禁止skip-permissions/盲latest/不明确续跑；每任务短上下文和时限，无产出需精确停止并验证。PA异常任务7de1d11c已STOPPED无代码，不再续跑。CLI不依赖桌面窗口可见，非关机离线承诺。

微信无真实AppID；开发ZIP更新，58测试PASS、preview已构建；无工具编译/真机/体验上传，不等于上线。采用wx.chooseLocation原生选择位置，待AppID/权限/隐私/合法域名实际核验。WECHAT_TRIAL_SETUP.md说明条件。营销积分/卡券/邀请为明确零资产演示，无后台账务。安全联系人按账号本机保存；无短信/录音/警务联动。Android系统Geocoder模拟器不可用，失败/常用地点兜底PASS；不能称成功地图搜索。

交付历史delivery/2026-10-25；driver public/download，passenger public/download-passenger；APK不入Git。大传输直连优先，不改全局代理。GitHub私库套餐403不支持强制保护，不升级公开；CI有记录并不等于强制保护。角色：frontend01a167c8-a5eb-77e3-b77e-214927a029da；backend01a168e2-22f7-70a2-af7f-4fb6be0ea695；Codex用6.1，Gemini沿用现有模型。无任务idle，无常驻AI。
