# 宜捷行 0.3.2 修复版独立复测报告

- **测试执行方**：Antigravity（独立QA执行成员）
- **测试环境**：QA 专用模拟器 `emulator-5582`（非真机）
- **测试应用**：`cn.hbzjhs.dc.driver.cloud`（版本：`0.3.2-cloud`，`versionCode=5`）
- **测试账号/场景**：合成体验账号002 / 已完成订单 `HISTORY02`（第二卡片“滨江公园→三峡机场”）
- **测试报告日期**：2026-10-23

---

## 一、 执行摘要

本轮针对宜捷行 `0.3.2-cloud` 进行了严谨的自动化断言复测。
在测试过程中，严格限制在指定订单 `HISTORY02`，成功执行了**空表校验**、**点击文字整行选中**、**软键盘展开与提交按钮可达性**、**确认弹窗取消与草稿保留**、以及**最终单次提交与服务端刷新核验**。
所有已执行步骤均通过严谨断言与现场截图/XML证据固化。本轮测试已全部停止，未对服务端造成多余重复提交。

---

## 二、 实际执行测试项与验证结论

### 1. 订单详情定位与断言（前置条件）
- **操作与事实**：在订单列表中定位第二张卡片“滨江公园→三峡机场”，点击其对应的“查看详情”按钮（坐标 `(850, 1667)`）进入详情页，通过断言核实订单编号明确包含 `HISTORY02`，随后点击“投诉与订单申诉”入口进入投诉表单。
- **证据文件**：
  - [run032_02_orders_list.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_02_orders_list.png) / [run032_02_orders_list.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_02_orders_list.xml)
  - [run032_03_order_detail.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_03_order_detail.png) / [run032_03_order_detail.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_03_order_detail.xml)
  - [run032_04_complaint_page.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_04_complaint_page.png) / [run032_04_complaint_page.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_04_complaint_page.xml)

### 2. 空表点击“确认提交投诉”校验提示测试（通过）
- **操作与事实**：在未选择任何投诉类型且未填入描述内容时，页面底部的“确认提交投诉”按钮保持可点击（新架构预期）。点击该按钮后，界面呈现明确的校验拦截与错误反馈。
- **证据文件**：
  - [run032_05_complaint_empty_submit_visible.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_05_complaint_empty_submit_visible.png) / [run032_05_complaint_empty_submit_visible.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_05_complaint_empty_submit_visible.xml)
  - [run032_06_empty_submit_feedback.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_06_empty_submit_feedback.png) / [run032_06_empty_submit_feedback.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_06_empty_submit_feedback.xml)

### 3. 点击投诉类型文字（远离小圆圈）整行选中核验（通过）
- **操作与事实**：定位“乘客取消或爽约”文本区域，执行远离左侧圆圈的文字偏右区域点击（坐标 `(776, 1311)`，原文字边界 `[210,1280][965,1343]`）。点击后，外部可选择容器（`android.view.View bounds=[94,1243][986,1380]`）成功变更为 `checked="true"`，界面呈现整行选中与背景高亮反馈。0.3.1 中“点击文字无法选中”的缺陷已修复。
- **证据文件**：
  - [run032_07_complaint_top_options.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_07_complaint_top_options.png) / [run032_07_complaint_top_options.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_07_complaint_top_options.xml)
  - [run032_08_after_tap_text.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_08_after_tap_text.png) / [run032_08_after_tap_text.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_08_after_tap_text.xml)

### 4. 描述输入与软键盘展开及固定提交按钮可达性（通过）
- **操作与事实**：点击描述输入框并输入测试内容 `AG-QA-032 complaint keyboard test`。系统服务 dumpsys input_method 明确返回 `mInputShown=True`，软键盘真正在屏幕展开。在软键盘展开状态下，“确认提交投诉”按钮被固定吸附于键盘上方可见区域（边界为 `top=1382, bottom=1446`），未被键盘遮挡，具备良好的可触达性。
- **证据文件**：
  - [run032_09_keyboard_visible.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_09_keyboard_visible.png) / [run032_09_keyboard_visible.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_09_keyboard_visible.xml)
  - [run032_10_keyboard_closed.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_10_keyboard_closed.png) / [run032_10_keyboard_closed.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_10_keyboard_closed.xml)

### 5. 确认弹窗取消与草稿保留核验（通过）
- **操作与事实**：保持未勾选黑名单状态下点击“确认提交投诉”，系统弹出确认对话框。首先点击“取消”按钮，弹窗关闭；将页面滑回视口后断言检查，单选项状态依然为 `checked="true"`，且输入框内的草稿文本 `AG-QA-032 complaint keyboard test` 完整保留，未发生重置。
- **证据文件**：
  - [run032_11_ready_to_submit.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_11_ready_to_submit.png)
  - [run032_12_confirm_dialog.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_12_confirm_dialog.png) / [run032_12_confirm_dialog.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_12_confirm_dialog.xml)
  - [run032_13_dialog_cancelled.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_13_dialog_cancelled.png) / [run032_13_dialog_cancelled.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_13_dialog_cancelled.xml)

### 6. 单次最终提交与服务端记录刷新核验（通过）
- **操作与事实**：再次点击提交按钮并在确认弹窗中点击“提交”（仅执行单次最终提交）。等待服务端响应后点击页面右上角“刷新”按钮，在下方“本订单的投诉记录”列表中成功断言核实到带有唯一测试标记的记录：`AG-QA-032 complaint keyboard test`。
- **证据文件**：
  - [run032_15_confirm_dialog_final.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_15_confirm_dialog_final.png)
  - [run032_16_complaint_submitted_result.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_16_complaint_submitted_result.png) / [run032_16_complaint_submitted_result.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_16_complaint_submitted_result.xml)
  - [run032_17_complaint_refreshed.png](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_17_complaint_refreshed.png) / [run032_17_complaint_refreshed.xml](file:///Users/dongge/Documents/AI-Team-Link/work/yijiexing-android-review-20261023/antigravity-evidence/run032_17_complaint_refreshed.xml)

---

## 三、 测试桩假阳性说明（Harness False Alarms）

在执行自动化脚本过程中，排查并纠正了两处测试桩逻辑引起的假阳性，明确其均为测试代码实现层面的断言差异，非 App 缺陷：
1. **ElementTree 叶子节点布尔误判**：
   - *现象*：初始脚本使用 `if not target_detail_btn:` 判断查找到的节点，在 Python `xml.etree.ElementTree` 中，无子节点的叶子元素在布尔上下文中估值为 `False`，导致误报未找到按钮。
   - *纠正*：修正为显式比较 `if target_detail_btn is None:`，成功准确定位第二张卡片。
2. **0.3.2 单选项 Compose 结构变更与断言对象差异**：
   - *现象*：0.3.2 优化了单选行交互，将整行包裹在具备 `checkable="true"` 的容器 `android.view.View` 中，点击后该外层容器变为 `checked="true"`，而内部的圆形 `RadioButton` 变为纯装饰性控件（未独立承载 checkable）。初始脚本仅检查 `class="android.widget.RadioButton"` 导致误报修复未生效。
   - *纠正*：调整断言规则，检查包含 `checked="true"` 的可选择容器，断言顺利通过并与截图中绿色选中背景完全吻合。

---

## 四、 未验证项说明（由 Codex 单独完成）

按用户指令，Antigravity 已立即停止所有模拟器交互，以下两项明确未由 Antigravity 验证，交由 Codex 负责后续测试：
1. **高德地图外部导航与终点显示**：已完成状态的订单 `HISTORY02` 按产品设计故意不展示导航按钮（导航仅在进行中订单可用），该测试场景不适用，Antigravity 未在进行中订单拉起高德地图验证。
2. **意见反馈页面流转与提交**：未执行意见反馈的空表校验与单次提交，留待后续单独验证。
