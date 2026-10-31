# backend 当前短卡
ACK2026-10-26-v3；真实ID 01a182c6-c058-76e1-9584-38abfe813799；2026-10-31T18:04:52+08:00。context UNKNOWN/not_exposed；NORMAL。root 01a17cc7-e0a2-7791-bf1d-4611c09bab22 唯一协调合并发布。

当前 RIDEB-20261031-01 初诊完成/WAITING_FINAL_SHA，最终验收 NOT_RUN/releaseReady=false。base c261d78c0108067b6057ff655d1396ec135d815e；源 /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031，branch codex/ride-flow-polish-20261031。仅写本任务/本卡/evidence，未改产品、Store、后端或云，未把Claude并行修改当冻结成果。待合并SHA未提供。

## 初诊要点
历史服务输出address/latitude/longitude/coordinateMetadata，与Android读取一致。有坐标历史点击只selectPlace局部更新，第二个“使用此地点”才save，可解释未回填；无坐标仅按名搜索。不能把字符串非空当有效坐标。未实机复现，不能排除其他触摸问题。
三个AddressDialog调用方：Booking四目标只改账号草稿；Profile只改编辑缓存，原favorite.save确认须保留；OrderDetail回调立即order.update且后端会改fare/estimatedFare/mileage/destinationPending，必须保留既有订单明确提交确认，不能统一选择即网络保存。后端接单/报价/阶段/300km门槛不变。
途经点由父级owner草稿rememberSaveable持有最多2个；迁移入口勿新建局部丢草稿、勿因editor清空返回错误层。删除点1连清点2或明确前移；显示、showWaypoints与payload一致，按点1→点2→终点影响估价。保留有效坐标/元数据、query/city清旧选择、世代取消、GPS手动优先、弹窗与地图scope。

证据 coordination/evidence/RIDEB-20261031-01/PRELIMINARY.md，含源行与冻结后针对验收场景。实际仅git ls-tree/git show只读源检查；新实现差异/build/unit/native/后端集成/DB/网络/provider/OEM/最终验收全部NOT_RUN。待root给最终SHA与作者证据再审，不轮询，不直接向其他chat自动回信。

上轮MAPSR窄审PASS，证据原路径保留；线上乘客1.1.20/code22、司机0.3.19/code22，产品d6d5206与PR45merge49bc669；本轮没有发布授权执行。Claude主实现/Gemini暂停，BIZ04–09既定边界保持。
