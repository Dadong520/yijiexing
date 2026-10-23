# 两端 UI 最终交付检查表

2026-10-24，UTC+8。此前准备清单保存在 final-ui-delivery-checklist-pre-release.md，只代表当时状态；本表为发布后的核对结果。

| 项目 | 结论与证据 |
| --- | --- |
| 冻结包与公开发布 | 司机035r3/code8、乘客115/code7；两者公开全量GET200、TLS开启、哈希/版本/签名一致。见 delivery-components.json 与两份 public-release 证据 |
| 同单三端联动 | STANDARD PORD97596bf0cc68 完成29.26元模拟付款失败恢复、行程DONE、五星及双方投诉回复回读；BID PORD2a42ed9559c3 36元预付取消退款并最终结束 |
| 账务状态 | 司机标准单余额仅增加2926分，BID退款3600分且收入不增加；后台只读61断言PASS |
| 视觉复核 | 关键默认/大字体/键盘图通过，独立视觉子agent审阅19图；见 ui-redesign-visual-review.md，不外推全页面可访问性 |
| 后台权限 | 本轮UI不改后台与共享v7；已有运营/只读权限403与会话测试证据保留原日期，未称本轮重跑 |
| 独立测试 | AG115离线强制16项PASS；AG035权限阻断0个UI动作；完整新版独立三端UI未执行 |
| 发布与设备 | 乘客00:52:09、司机00:54:08公开核验；00:51:46司机下线、两设备字体1.0返回桌面；后台入口保持可用 |
| 源码与清单 | 最终路径/哈希见delivery-components.json，后台压缩包和逐文件清单由ops/package_delivery.py生成，排除实际凭据 |
| 启停与低费用 | IAM保留、按需运行；关闭本机窗口仅停入口，云暂停恢复见README；本轮未实际执行全环境启停 |
| 剩余问题 | 司机“今日”仍是累计演示计数；真实外部服务未接通；BID完整行程DONE、继续重匹配、多轮退款UI、全场景故障注入、全部可访问性未覆盖 |

实际联测步骤、截图、缺陷复测与边界以 docs/UI_035_115_THREE_PARTY.md、docs/UI_REDESIGN_VISUAL_AUDIT.md、docs/INDEPENDENT_TEST_REPORT.md、docs/ISSUES.md 为准。历史版本和旧订单证据未覆盖、未改写。
