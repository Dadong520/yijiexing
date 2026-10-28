# CCUI-20261029-01 设计token与覆盖清单（作者：claude，session 413b9b2c-f346-4a81-8891-87bc3c962394）

## Tokens（全端统一；对比度为白底估算，未用工具实测→NOT_RUN）
| 角色 | 值 | 用途 |
|---|---|---|
| brand 青绿 | #00796E（≈5.3:1 白底） | 主按钮、选中导航、主链接 |
| brandStrong | #005F56 | 按下/强调标题 |
| brandSoft | #E3F4F1 | 选中态底、品牌浅底 |
| sky 天蓝 | #0369A1 / soft #E0F2FE | 行程/地点/导航类模块图标、信息提示 |
| warm 暖橙 | #B45309 / soft #FFF4E5 | 钱包/优惠/会员/收入类模块图标（非警告） |
| violet 少量紫 | #6D28D9 / soft #F1EBFE | 设置/客服/社区类模块图标，仅图标底 |
| ink 正文 | #0F1B2A | 正文/标题（≈16:1） |
| muted 次要 | #4B5B6B（≈6.9:1） | 说明文字（原#6B7280≈4.8，提升清晰度） |
| page 页面底 | #F2F6F7 | 页面背景（带青灰，与白卡区分） |
| line 卡片边 | #DCE5E8 | 卡片边框/分隔线 |
| success | #1B7A3E / soft #E6F4EA | 成功/已完成/已通过 |
| warning | #B45309 / soft #FFF4E5 | 警告/待处理/即将到期/模拟提示 |
| danger | #B42318 / soft #FDECEA | 错误/取消/删除/拒绝 |

规则：语义色只按业务状态使用；模块色只用于图标与浅底，不用于正文；主按钮唯一青绿实底，次按钮白底青绿边，危险按钮红字/红边。

## 覆盖清单（本轮实施）
- 司机Android：UiKit.kt token+Theme+MenuRow模块图标分色+卡片边/页面底；新增DriverPalette.kt。
- 乘客Android：MainActivity.kt 顶层token；新增PassengerPalette.kt（模块/语义token）。
- 小程序：pages/shared.wxss（页面底、卡片边、tabs、按钮、notice/error/danger/badge语义、输入焦点）。
- 后台：apps/admin-console/web/admin.css 与 apps/cloud-service/static/admin.css :root token（两份同改）。
- 未覆盖/逐页硬编码：见SELFCHECK.md。
