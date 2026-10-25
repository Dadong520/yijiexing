# frontend 交接卡

ACK 2026-10-25-v2。UI-20261025-03 before基线已完成 → WAIT_NEW_APK/IDLE；尚未新UI验收。root独占源码，Gemini主设计；本人仅前后截图与独立视觉比较，不启动agent/Gemini、不构建/部署/改产品。任务base ff9f5a787fede48bb2ac1035c38d45ff543323b2。

证据 coordination/evidence/UI-20261025-03/before/before-audit.json，7张本轮实际查看的无横幅PNG/XML；driver15首页/14订单/13司机之家/12地图，passenger01首页/09订单/08我的。前面中间图被Google Play服务系统横幅遮挡或被替代，NOT_ACCEPTED_BASELINE，不给Gemini冒充干净依据。设备原关闭已恢复原AVD无wipe，系统横幅等待/横划仍不消失，后续上划后无横幅，补拍完成；没有改Google/通知设置。
设备5586司机003/5584乘客0102，1080×2400、420dpi、font1.0始终未改。已装回读driver0.3.8/code11 hash08732bf8b372e30024711e404e4bf7161a6dc95cf05d4b8094efda895ccda6b7；passenger1.1.8/code10 5ee70fd105e529ed5fdef9f422bbf7bcecf8e09402b64fda6aa14df2ae021f23。元数据各role-metadata.json，操作日志及fresh截图helper同目录。

症结：司机首页状态/收款/订单大卡纵向堆叠，地图藏下方工具；订单5筛选占2行、高卡密度低；司机之家大身份/统计/会员卡留白重。乘客首页纯路线表单+底部呼叫，没有地图与底部叫车面板；我的菜单高留白与重复通用图标。当前乘客进行中空态，未造订单补画面。
地图现状：司机MapLibre真实道路瓦片、定位点/回中图标及来源署名可见，App显示精度5米（未实测实地精度），缺中文离线语音明确标出；乘客首页无地图。未测试交通/路线/地图异常/高德。
两App均force-stop/HOME，AVD保留运行待新包；登录和数据保留。未抢单/下单/取消/支付/投诉/上下线/拨号/退出/清数据；无凭据读取/输出，无源码/构建/部署。
下一步等root给新APK/hash和同屏参考，独立比较正常字体、真实地图+底部面板、>=48dp、长字换行、原登录与主要弹窗。此次暂停专门适老大字布局，不重跑旧全套；UI-02历史PASS仅原目录，不冒充本轮通过。根任务验收/合并/发布由coordinator统一。
