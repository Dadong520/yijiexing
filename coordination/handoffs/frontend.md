# frontend短交接

ACK v2。UI-20261025-03 最终Android UI delta独立验收 **PASS_SCOPE / IDLE / READY_FOR_COORDINATOR**。只改证据/handoff；没有改产品源码、构建、部署或唤醒额外角色。唯一设备操作权已交还coordinator，不再自行ADB/窗口操作。

工作根 `/Users/dongge/.codex/tmp/yijiexing-ui03-integration`；源 **b9bd90a58f01a4e6f09e37366ed8f8b3feaaa51b**。最终报告 `coordination/evidence/UI-20261025-03/after-final/design-qa.json`；截图PNG/XML、source-delta、installed hash、恢复记录在同目录。19:05:21开始，全部设备操作19:10:49完成，未超过19:13:21的8min上限。最后07/08也已逐图确认真实道路、两标记和来源弹窗。

独立install-r和base.apk回读匹配：driver **0.3.9-cloud/code12** SHA256 `52dae8eebe44851be37de1a55df096cbbe07286f5c88b7a3d3a3bf22338bf13f`；passenger **1.1.9-experience/code11** SHA256 `1f86150d465997dcf72052a278c44ffff3601cec4525beecdd985a819f23d7e5`。同签名升级保留原003/0102会话；root55/20测试构建证据继承，本人未重复运行。

两P2已关闭：driver正常1080x2400和临时1024x2216/420dpi首抢单CTA完整可见、不用滚动（02/03）；passenger初始fit与实际平移回位两点避开badge/recenter（02/03/04）。两01有系统heads-up横幅，已剔除完整页面验收依据。实际参考对比继承原8499全轮，地图+底部面板层级保持；profile分组行与参考图标网格差异为既有非阻塞设计差异。

关键精确retry已补齐PASS：唯一可逆SDK cache备份 `files/.ui03-map-cache-final-b9-20261025-1909`，原files/mbgl-offline.db 765952B，其他账户文件不读。offline wifi/data0/0显示地图暂未加载和真实重试按钮（05）；**19:10:11实际在离线点击重新加载地图，06进入地图加载中**；随后恢复网络，07真实道路瓦片/两点恢复、08来源为MapLibre/OpenFreeMap/OpenMapTiles/OpenStreetMap。没有第二次在线地图重试tap；云连接重试单独记录，无呼叫提交。`map-retry-final.json`证明originalSdkCacheRestored=true、networkRestored1/1、font1.0。

恢复完成：driver临时wm已reset回physical1080x2400；两端font1.0不变；driver网络未改；passenger wifi/data1/1恢复；原SDK DB已rename回原路径。本轮生成DB保留在上述唯一backup/generated/files/mbgl-offline.db，不删除。旧1855备份仍保留，不再动。两App均force-stop/HOME；原两AVD留给root，默认ADB5037正常。无prefs/token读取、pm clear、清数据、切在线、接单/下单/取消/支付/投诉业务写入。

范围边界：精确diff8499→b9仅CoreScreens.kt mapheight和PassengerMap.kt密度/相机padding；原after-unlocked全UI证据可限定继承（订单/个人、确认Return、地址键盘Cancel、48dp抽样、缓存离线、司机地图平移/回位/署名），不假称最终包全套重跑。旧两个P2与retry NOT_RUN被本轮明确证据关闭。**NOT_RUN**：新登录/OTP、真机/横屏/TalkBack/专门大字、外部高德终点、投诉、新真实交易/三端全生命周期、任意超长文本。不是整个业务发布验收。

下一步coordinator读取本报告/PNG，维护task最终acceptedSha和gate并合并UI03源码；按最新派单039/119不单独发布，后续含UI的ACCESS无码版本独立验收后统一发布回读；本人未commit或发布，不进行无变化轮询/自动回信。待合并产品SHA为b9；报告/handoff由root统一收尾提交。
