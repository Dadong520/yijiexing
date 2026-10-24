# AD 最终受影响项复核：PASS（限定静态与截图范围）

时间：2026-10-25T02:07:00.185511+08:00。此段替代下方历史FAIL结论；历史问题保留作修复证据。

- 最终 CSS SHA256 `6cb70683dfda202e140e7346a289688e97a6fa6f48d574152d797bc564f27567`；JS SHA256 `cc6540f3ceccfa8667b7223362a15a506741e2c8b07f46253001bc8e6f4b7bce`。
- 已确认末尾 `.workspace{margin-left:0}` 及断点重置覆盖旧226/185px；aside移除旧padding/inset、nav改纵向且min-height:0、asidefoot改为正常文档流；`.stat-sub b` 改inline/margin:0。原阻断与拆行建议已解除。
- 已独立读取 `local-synthetic-overview-fixed.png` 像素，桌面侧栏/内容衔接、统计卡片、导航与footer无此前重复空白；这是本机合成数据截图。1425宽sidebar.right=workspace.left=250、无横溢出与nav.bottom=footer.top是coordinator提供的DOM测量，本角色未重新操控浏览器测量。
- 新导航仅将现有9个pages按固定3组输出，标题esc，data-page和现有navigate保留；图标path均常量。登录新增scrollTo不改变鉴权/接口；未引入产品写动作。当前 `node --check .../web/admin.js` 退出0。
- 之前overview 6组隔离检查结论见下文；本次未重复其完整运行。当前未发现新的代码阻断；真实账号/RBAC、窄屏浏览器、云部署/IAP回读仍需coordinator的实际验证证据。
- **可发布限定范围**：在coordinator完成受影响浏览器/权限回读后，可作为当前admin-browser的静态美化覆盖层发布；本审查不批准改IAP/后端/ENV/IAM/数据或替换成旧原型。未由本角色部署，最终合并SHA待协调者记录。idle。

---

# AD-20261025-01 独立只读复核

时间：2026-10-25T01:57:14.792230+08:00。审查者 backend（非作者），ACK 规则 v2。结论：**FAIL / CHANGES_REQUESTED**，原因是下列 CSS 布局回归；overview 逻辑限定检查 **6/6 PASS**。未改产品、未部署、未派代理，当前 idle。

## 绑定范围

工作目录 `.coordination/integration-ag-final`；读取时 HEAD `097e3e47ea05f545705a588a9da7beb23bb9ec4b`；检查工作树的 `candidates/admin-v8/yijiexing-admin-20261023/web/admin.js`、`admin.css`、`admin.html`。本轮工作树未提交，最终合并 SHA：NOT_AVAILABLE，由协调者安排。其他并行客户端改动未纳入。

- `admin.js` SHA256 `e92c392869318d243bfc747401c5b04e110e080dc65f6d6c6e2d72a83f2dbc89`
- `admin.css` SHA256 `8da6fffcc3a986d2e2fd0fe83855fe12fc1a3e96c466e91c4399e064c7cba555`
- `admin.html` SHA256 `dee55e9960080855c720b4d63e1611084f2f58b79fbaa13744670861dfb14649`

## 必须修复：P2 内容区重复左偏移

`admin.css:1` 保留旧规则 `.workspace{margin-left:226px}`，原1000px断点还有185px；新 `#app:not([hidden])`（167–170）已是 flex，`aside`（173–186）是占位250px的 sticky侧栏，`.workspace`（305–311）改为 flex:1 却未清零 margin-left。桌面内容左起点会包含“侧栏250 + 旧margin226”，重复空白、压缩所有概览/表格/表单可用宽度。在701–768px段，新布局又切成上下排列，但旧185px偏移仍在；只有旧700px断点才有margin:0。

建议在新 `.workspace` 明确 `margin-left:0`（并检查断点继承）；旧 `.stat b{display:block;margin:12px 0}` 也继续影响新 `.stat-sub b`，当前后者只改字体，完成/取消补充数字会拆行并留下大间距，可一并明确 inline/margin:0。后者属排版建议，前者是本轮阻止PASS的确定CSS冲突。

这是由当前 CSS 级联和占位规则直接核实的静态回归；本角色未操作浏览器，不将其说成像素实测。负责人可结合正在进行的浏览器截图修复、复查桌面和701–768px窗口后再判最终PASS。

## 已通过的限定逻辑检查

运行方式：本机 Node 内联 vm/assert 隔离执行实际 overview 源码与其原有帮助函数，最小document只接收HTML和onclick；不连接服务，不执行整个登录启动逻辑。退出码0；6组断言全部通过：

1. state缺orders/drivers/passengers/support时，四核心指标都为—，没有把无权限/未加载当¥0.00；真正空数组时显示0、0、0、¥0.00。
2. 11条合成记录覆盖PICKUP/ARRIVED/TRIP/WAIT_PAY/DONE/CANCELLED/RESERVED/PREPAY/AVAILABLE/WAIT_CONTINUE：总数11，运输中3，待收款1；仅DONE且paid=true的1200分记¥12.00，已完成未付和已取消已付均不计。页面明确“全部时间”和“不是总账净额”。这是传入state全量数组的统计，不以查询筛选/分页或最新5条替代。
3. 未知stage含script、id含img、起点含HTML标签时均被转义；状态label使用esc(label(s))，路线/id/time/badge走现有转义。分布百分比由数组计数生成数值，不拼接原始stage到style。
4. 四个快捷按钮实际onclick只导航orders/drivers/support/payments；spy确认未调用api/act，无写入。
5. 删除overview函数区段后，新旧JS的前缀与后缀逐字节一致，现有接口、动作、确认框、requestId、会话/登录/退出和applyPermissions没有改变。
6. 上述缺失/空/混合数据、转义、导航和JS差异核对组合共6组PASS；无借用旧后端或浏览器测试数量。

其他静态检查：HTML与当前HEAD字节一致，ID/静态资源路径保持原样；CSS保留[hidden] display:none!important，未将权限隐藏元素显示出来；无新增远程@import或HTTP字体/脚本依赖。table()仍产生.tablewrap且旧overflow:auto保留，新概览外层.table-wrap也有水平滚动。dialog仍使用原showModal/close/按钮容器，modalBody有滚动；不存在新增自动提交、绕过确认或权限判断的JS。

## 结果边界与后续

- 这是一轮源码/隔离渲染审查，**浏览器视觉、键盘/窄屏、真实账号权限、云部署/IAP回读：NOT_RUN（本角色）**，由coordinator正在进行的独立浏览器/部署记录承担。
- 后台只读角色仍由原can/applyPermissions和服务端鉴权约束；快捷导航不授予权限。未宣称本轮登录过qa_viewer或验证过真实接口403。
- 统计只解释DONE&&paid的当前fare合计，不等于支付流水累计或退款后的总账净额；以后接入优惠/补贴必须重新定义金额统计，不能继承本结论。
- 本轮报告不核验镜像构建/IAP代码/ENV/IAM是否保留，部署应仅按任务由负责人覆盖当前admin-browser静态文件并回读。
- 下一步：协调者修复布局继承，做受影响CSS/浏览器复查并绑定最终hash；无需重复未变化的后端测试。本角色保持idle。
