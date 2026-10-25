# backend · TRIP-20261025-01 · 2026-10-26 收尾

目标：服务端接客/送达300米限制、乘客同意、后台继承配置；不改真实金额规则、身份权限或发布环境。负责人backend，基线a5bb882fed045ac62fe8d8398cdc8e483015cc19，唯一树 `/Users/dongge/.codex/tmp/yijiexing-trip-api-20261025`，分支 `codex/trip-distance-api-20261025`。

待合并产品SHA：8ec8419d30955c216d7e4fe90451584d2a9622cf。作者已停止产品编辑，等待root非作者关键审核。唯一归属apps/cloud-service/*.py/tests +契约+本证据；旧API树完全未动，root的未提交STATE/BOARD/task原样保持，未暂存。角色入口卡不在allow_paths，本HANDOFF为短卡供root同步。

契约：coordination/contracts/TRIP-20261025-01.md。司机现有location.report(time Unix毫秒/ISO)，乘客同名action带本订单id。ARRIVED→TRIP先尾号/目的地校验，TRIP→DONE或WAIT_PAY先途经点；服务端按新鲜乘客GPS/真实目的地和司机GPS计算直线米数。未知/旧/精度差/null绝不当0。pending订单保持原阶段；乘客order.distanceConsent同意原子推进一次，拒绝不推进，180秒期限，阶段/司机/送达目标变更失效，订单取消/续叫清楚失效。重复请求复用pending，已处理同意幂等，同id反向决定409。

默认接客/送达各300m，config.draft/publish新增tripDistance字段，各50..5000整数，全国→大区→城市逐字段继承；旧配置内建默认300。固定采样和接收双新鲜度120s、精度100m，原生已有time实际为Unix毫秒。order.distanceRules/state.tripDistanceRules及后台cities给version/sources；确认保存阈值、目标/司机位置、原因、期限快照。原始passengerLocation和内部授权context不公开到driver或乘客订单视图/旧缓存；后台证据与审计保留。

关键补洞：共享议价预付原来直接TRIP，现同其他预付到PICKUP，再正常到达/尾号/距离检查。议价配对真实共享synthetic=false并去私有免确认notice，coordinatesSynthetic仍保持坐标来源边界。司机私有合成演示保持原流程，distanceEnforcement=SYNTHETIC_DEMO_EXEMPT+明确notice；共享为PASSENGER_CONSENT_REQUIRED。

实际测试：最终 `env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s apps/cloud-service/tests -v` 208 PASS/57.769s（185原+23新）；专项23 PASS，阶段专项3 PASS。包含300临界/缺旧/精度/坐标/未来时间、越权/伪造同意、重复/反向/拒绝/过期/换司机/目标变化/取消续叫、尾号/途经点、WAIT_PAY允许后续订单、单次入账、后台角色/继承配置、真实HTTP/冷恢复、同服务并发以及双实例CAS竞争200/503后重试。全部在临时SQLite+内存对象快照；无现网账号/环境。原金额与策略回归fixture通过双方认证API补新鲜虚拟GPS，不跳过规则；距离新测单独raw请求不走该helper。初次旧fixture失败22项日志保留，均已解决；最终无作者失败。

检查：python3 tools/check_coordination.py PASS；git diff --check PASS；8保护文件（含cloud guard/server/snapshot）与基线字节一致，hash及日志SHA见results.json。未部署/启停服务、未碰原测试/生产、未改IAM/IAP/私库/APK。

未运行：root独立业务审核、Native/后台浏览器UI、双AVD联调、部署回读均NOT_RUN。GPS无防伪认证；代叫GPS是下单账号设备，不是联系人；仅直线距离；真实支付/推送仍未接。下一步root非作者审核、合并ALERT原生及Web，再统一安排发布回读。作者不自行常驻或多派agent，待明确缺陷单。
