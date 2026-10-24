# UI-20261025-02 司机 UI 非作者业务复核

2026-10-25（UTC+8）；backend，非作者；ACK v2。结论：**PASS_CODE_REVIEW，未发现本轮司机变更的业务阻断问题**。仅源码与基线核对；构建、运行、系统大字和首屏视觉验收 NOT_RUN（本角色），由coordinator/frontend分别完成后决定发布。未改产品、未构建、未部署、未派代理或调用Gemini；已idle。

## 绑定范围

目录 `.coordination/integration-ag-final`，分支任务基线及读取时HEAD为 `f0f0920995dcf89dfc5f6125542e26af86cf110d`。检查工作树三文件，最终合并SHA待coordinator记录：

| 文件（司机android目录内） | SHA256 |
| --- | --- |
| app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt | `bfe62e9ff70b6eefc324b9b50f17449e85bf78d464f0b4e661abf613eb689fad` |
| app/src/main/java/cn/hbzjhs/driver/preview/UiKit.kt | `870800db238e5e12976164c64efb7c1e9080f5077df9f5f8dd6ffb0a164bb687` |
| app/build.gradle.kts | `2fad7c48287162816fe3e125356548b0d7d450f0bc5dac794ab5b68a486fdcfa` |

版本仅由0.3.7/code10升至 **0.3.8-cloud/code11**；applicationId、独立云源构建字段及测试入口未改变。未审查本任务后续乘客UI生成物。

## 已覆盖业务保护

- **接单/占车筛选未改变**：CoreScreens:418–421仍从服务端features.canAcceptOrders、state.activeOrder、state.pendingCollections和AVAILABLE集合取值，共享真实单仍排在私有演示单前。596–608的附近单仍要求 `active==null && canAccept && state.online`；竞价/抢单仍调用同一个 `onGrab(order)`，没有将WAIT_PAY当active重新锁车。
- **待收款单独展示**：pendingCollections仅控制待收款提示和 `orders/unpaid` 跳转，明确不影响继续接单；私有演示单数量标识仍在。DriverDomain未变，activeOrder和pendingCollections计算不被UI重写。
- **回调/确认链保留**：继续行程仍 `detail:<id>`；上线/休息使用原onToggle；刷新仍store.refresh；司机资料、消息、接单设置、订单、钱包、资质、服务分、积分、拉新、排行等原导航保留。MainActivity未修改，接单/报价的原确认处理未被UI直接写动作取代。UiKit的PrimaryButton、OrderCard、PageHeader、MenuRow继续转交原onClick/onAction/onBack及enabled参数。
- **折叠只影响展示**：出车工具折叠中保留NativeMap、positionStatus、speechStatus、安全行车提示；线下议价和热力入口仍受原active/canAccept/state.online及canNegotiate/canHeatmap约束；不可用说明和会员权益入口存在。MembershipSummaryCard仍在首页与个人页，不因折叠删除。
- **金额与累计口径未改变**：OrderCard仍展示order.fare、无终点显示费用待定、竞价显示参考报价；StatsRow仍来自state.todayIncome/todayAccepted/todayCompleted，明确标累计及含演示数据；个人页仍是累计接单/完单与state.balance钱包。未新增“今日净收入”或硬编码营收/驾驶时长、完成数、权益开放结论。
- **状态来自原权威字段**：网络标记来自store.connected，听单/休息来自state.online；个人服务分/企业仍读profile并保留缺失回退，没有强制打开会员/接单资格或虚构已认证。新样式不改变共享后端、支付、权限或账户状态。

## 实际检查与边界

本机实际执行 `git diff` 阅读上述三文件、DriverDomain相关定义；Python对 `critical-baseline.json` 的13项逐文件SHA校验，并通过 `git show <baseSha>:<path>` 与基线逐字节比较，**13/13一致**（含司机Store/领域/导航/议价/状态协调及乘客关键文件）。这是静态保护核对，不宣称新增13个业务用例通过。

无源码阻断，不需要改动金额/后端来完成本轮UI。新版排版涉及横向头部/金额、折叠工具及全局字号，是否窄屏/系统大字不截关键动作、触达原确认入口，仍以frontend独立设备验收为准；本报告不自动覆盖该验收、签名覆盖安装或公开APK回读。只有通过这些门禁后，coordinator可按原权限发布独立体验APK；本角色未自行批准或执行部署。
