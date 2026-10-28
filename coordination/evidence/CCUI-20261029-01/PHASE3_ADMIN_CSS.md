# CCUI-20261029-01 阶段3：仅两份admin CSS（claude，session 413b9b2c-f346-4a81-8891-87bc3c962394）
本阶段只改了两份admin CSS；Android和小程序（ad674已冻结）没动，HTML/JS/API也没动。测试、截图、对比度实测全部NOT_RUN（无Bash）。

## apps/admin-console/web/admin.css（只在文件末尾追加一块CSS，原结构不变）
选择器都核对过admin.js真实输出：.stat/.stat-tag/.stat-num/.na（l.687+）、nav#nav button[data-page=…]和.nav-icon（l.829-830的navGroups键）、.badge.good/.warn/.bad（l.7）。
- 小字：:root覆盖--muted→var(--color-muted)#4B5B6B（原首行#788698对比度不够）。.muted/small/.stat span/.stat-tag/th/.details dt/.empty/.breadcrumb/#identity/.pagehead p/summary/.eyebrow/分页/筛选摘要都改用#4B5B6B；footer用#5B6B7A；表头底#EEF3F4、字重600。
- 数据概览卡：顶边按位置4色循环（青绿/天蓝/暖橙/紫）。
- 导航图标按模块上色（深色侧栏上用浅色调）：工作台→天蓝#7DD3FC；支付/退款/资质到期→暖橙#FDBA74；平台运维与配置治理→紫#C4B5FD；其余→青绿#5EEAD4；选中项图标白色，并加左侧亮条。导航文字#B6C6C2。
- 状态标签：默认天蓝；good→成功绿；warn→暖橙；bad→危险红。
- 按钮：主按钮青绿；普通按钮青绿字；危险按钮红字红边，hover浅红底。错误提示（.error/#error/.query-error/登录错误）统一危险色+浅红底。
- 焦点：输入框、下拉框、文本框统一2px青绿外框；已有*:focus-visible保留。
- 其他：.notice暖橙左条；filter-chip、时间线用青绿，进度条用天蓝；链接天蓝；在线点成功绿。

## apps/cloud-service/static/admin.css（单行结构保留，末尾追加）
- 阶段1已用同一套palette：统计卡4色顶边、badge成功/暖橙/天蓝、sandbox暖橙、侧栏选中亮边。
- 本轮追加：small/.muted/.crumb/.empty改#4B5B6B；.error加浅红底（:empty时不显示底）；button/a/summary的focus-visible青绿外框，导航焦点亮青。
- 云端JS把FAILED/REJECTED输出为warn类，CSS没法区分出bad，按原有语义保留暖橙；如需改红要改JS，超出范围。

## 未通过/风险
- 截图、对比度实测NOT_RUN。浅色导航图标在#0E2F2A深底上预期对比度>7:1，属估算。
- .stat:nth-child按卡片位置循环上色，不是按业务模块；JS没有输出模块类，不能新增无效选择器。
- 云端.error若用作行内文字，会多一层浅红底，需截图确认。
