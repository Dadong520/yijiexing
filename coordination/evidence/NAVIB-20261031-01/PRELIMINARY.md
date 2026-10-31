# NAVIB-20261031-01 只读规则与DTO初诊

基线 d5d878e2e56ca11fabc576545c46a277d8e7b985；source /Users/dongge/.codex/tmp/yijiexing-in-app-navigation-20261031，branch codex/in-app-navigation-20261031。检查HEAD准确、clean；backend tree033e173314e7339e46c2c78c178b870039609fa9。只读immutable Git对象，无产品编辑/提交/测试执行/网络/部署。业务验收NOT_RUN，未修复。共享后端未来唯一获准写目录仍UIO树apps/cloud-service，未触碰；root须另派唯一作者及allow_paths。

## 同起终点根因与最小后端方案
- passenger.py:259–262 创建在读取waypoints之前无条件拒绝casefold同名、坐标各轴差都≤1e-7。267–272之后才校验max2/字典/名称2–100/有限范围坐标并规范化；因此有效 O→W→O 根本到不了路线计算。
- passenger.py:341–360 修改目的地仍先受归属/阶段/报价/接单条件限制，350–351再拒绝同名/同坐标；352起使用既有waypoints。新规则必须create/update同改，不能只放开Android。
- 最小建议：在passenger.py内部共用“点相同/有效往返”小helper。先验证所有拟生效端点及途经点，再以规范化实际坐标判断：至少一个有效途经点与起点按既有1e-7各轴容差不同，才允许绕过两条同名/同坐标限制；否则维持原限制。不要仅凭waypoints非空或点名不同放行。命名别名但同坐标、全点同坐标、点名不同而位置相同均不可绕过；同名但真实不同位置且有有效离开起点的途经点可放行。无途经点普通订单的旧同名行为不额外扩改。
- create把路线形状约束移到waypoint校验后；update用同helper校验完整拟生效路线，仍禁止通过update新增/删除途经点（343白名单无waypoints）。复用不代表扩大可改阶段，尾号/定位/金额确认不能省略。
- 输入严格保留validate_point:250–251：仅int/float，拒bool、字符串、null、NaN/Infinity、越界；现后端允许合法0轴/0,0，不偷偷以真假值当缺失。UI地点选择的0哨兵与此不同，导航缺坐标应明确不可导航，不能造0。有效离开点与总路程都按坐标，不按名称。
- 不新增DB字段/迁移、不重估旧单、不清历史、保留坐标metadata验证及字段绑定。legacy missing destination仍只可原同ID补齐，且新单destinationPending仍禁止；未编辑的legacy地点数据不因新规则被批量拒绝/重写。若旧waypoint兼容字符串/缺坐标，只在实际进入新路线校验时明确报错，不能默默丢点而变成直达。

## 距离、金额与全部路径
- create passenger.py:274–280 已以 [O]+规范化WP+[D] 的每个相邻leg累计haversine公里，再要求≤300，estimate=max(800,round((8+max(0,distance*1.3-2)*2.4)*100))；mileage=distance*1.3保留。update:352–358也全legs，系数12742与6371*2等价。往返不能用端点距离0替代，更不能拿SDK道路路线/预计到达距离改计价。
- 可最小共用同一全legs距离计算，避免create/update边界漂移，但保留原800分最低价、round、1.3、2.4、300km和体验估价标记。至少一个离开点后总路程>0；不要以最终mileage一位小数==0.0误拒很短但非零行程；容差边界以既有point比较明确测试。
- passenger_route:468–470已输出O/WP/D全坐标，source=synthetic且“坐标连线不能替代道路导航”；不是可用导航规划接口。fees:471–473只回已保存金额；continue:370重用estimatedFare并清arrivedWaypoints，不重估；negotiation.py:78是司机议价金额，不能改成路线估价。domain.py:167–189私有合成新单使用固定distance/mileage，属于另一路演示数据，不得宣称与共享真实订单同计价。
- 删除最后WP发生在乘客本地草稿：没有存量后端删WP API；再次create若O=D必须拒，不能靠最低价兜底。更新终点回起点且已有有效WP允许，否则拒绝。大回环终点=起点也必须累计去返全程后判300，而不是零距离绕过上限。
- 幂等 passenger.py:293–302 passenger requestId必填，相同账户requestId/相同hash回旧结果，不同payload409；新增逻辑不得移到幂等之前导致成功重放重估/重插。shared_driver_action:414–419/466同driver请求记录，stage expected在domain.py:111–113；原requestId/事务/CAS边界保持。

## 司机途经点DTO与UI真实现状
- passenger.py:213–244 sync_shared_orders从canonical clone/trip_order_view投影，未剥waypoints/arrivedWaypoints。共享单只对接单司机，或同城AVAILABLE且无司机并未屏蔽的司机可见；project_driver_order:99–119再保护敏感字段。
- trip_distance.py:239–252 clone保留途经字段，去passengerLocation/consent内部context；不需要新增后端DTO字段。ConnectedStore.kt:552–608解析waypointName/name/address/title（含legacy字符串名字），坐标支持waypointLat/latitude等，保留原数组index；arrivedWaypoints解析为有效索引set。
- 服务端create规范化sortNo为1-based、原数组次序；arrivedWaypoints/action index为0-based，**不是sortNo或id**。不要按新sortNo重新排序后继续用新列表位置确认旧索引；无坐标legacy点仍应显示名字，不能因不可导航就删除/压缩数组。
- UiKit.kt:493–525 RouteStops当前只画起/终，529–570列表卡复用，因此“司机列表不展示途经”是展示缺口，非服务没传。CoreScreens.kt:867详情已有TripWaypoints；TripWaypoints.kt:35–75逐点显示且仅TRIP未到达时可人工确认，115–125从store最新订单核阶段后发原index。
- NavigationContract.kt:33–53：行程前目标上车点，TRIP取pendingWaypoints.firstOrNull，再终点；现CoreScreens:903实际调用外部导航，需frontend另行SDK改造。不要把后端synthetic路线或“画全路线”当真实SDK道路导航成功。

## 阶段、归属、尾号、距离、乘客同意：必须保留
- shared_driver_action:434只有已分配司机可推进/确认WP（取消报价有特例）。435–444先expectedStage检查，再允许RESERVED/PICKUP/ARRIVED/TRIP；RESERVED开始接驾复核资质和双方冲突；PICKUP→ARRIVED本身无GPS距离同意gate。不能泛称所有“到达”都做距离校验。
- ARRIVED→TRIP先拒destinationPending、校验乘客手机尾号、再gate_shared_advance；phoneTail投影仅canonical已分配司机且ARRIVED时可见，其他stage空（passenger.py:109–113），不要为导航页放宽尾号或泄漏乘客手机号。
- TRIP→DONE/WAIT_PAY前必须全部WP已确认，再终点距离gate。trip_distance.py:84–100有效GPS为WGS84/精度≤100m/设备与接收时间-30..120s（非密码学位置认证）；140–173接乘客时比较driver与passengerLocation、送达时driver与最终终点，默认300m，normalized目标保守2m边界；不是整条行程公里数。
- gate:186–209司机不能传distance/approved绕过；超距/缺定位等产生绑定order/passenger/driver/stage/目的地context的180秒请求，重复点击复用pending，不能自动同意。passenger_distance_consent:211–235仅所属乘客可明确布尔同意/拒绝，校验时效/context，TRIP重新查WP齐全；同决定幂等、反向决定409。
- **现有边界** passenger.py:445–447及domain.py:246–248仅TRIP+合法index+去重，没有强制WP按序，没有WP GPS/乘客同意gate。可先确认index1再0；导航first pending按原数组顺序，但不等于后端强制顺序。最小往返放开不必顺带改此业务；若root要求强制顺序/距离须独立明确规则与兼容策略，不能只改UI声称安全，也不可SDK“到达”自动POST order.waypoint/order.advance。
- 电话现仅OperationsSupport.kt:85–105公司supportPhone经人工确认打开ACTION_DIAL，由用户决定拨出；driver DTO去真实乘客mobile/phone，只有ARRIVED尾号。未发现乘客电话/中继服务，不得在导航中虚构“拨打乘客成功”。可复用现公司电话动作/反馈，无阶段推进，不自动拨出。

## 有意义的后续测试（建议，全部NOT_RUN）
1. create STANDARD/RESERVED/BID同名同坐标O→W→O、两WP回环成功，route DTO保序；create/update估价一致，精确全legs金额/里程；选足够长的回环排除端点零距离退化，短行程仍可合法命中800分最低价。
2. 无WP、空WP、全部WP与O相同（含不同名/别名）拒绝；一个同点WP+一个离开WP允许；最多2/第3拒；bool/string/null/NaN/Infinity/超界WP拒，失败无插单/无金额改写。
3. 有效WP加终点改回O允许；无有效WP改回O拒；司机接单/有报价/其他阶段、越权用户继续拒。只能remark不触发新route变更；metadata不匹配仍拒；旧无终点同ID补齐/未改历史可读。
4. legs合计300km内/恰边界/超出；O=D但往返总长>300拒，端点近但绕远拒；容差±1e-7边界、极短非零与round/mileage不误判。可使用确定坐标按现haversine算预期，不连真实provider。
5. 最后WP从草稿删掉后O=D调用create拒；同requestId重复有效create/update结果稳定无重插，同ID不同body409；已有订单/资金额度/原资料不改。
6. 实际共享单列表/详情/driver action result均含WP原顺序/index及arrived；legacy混合名字/坐标缺失保序展示、不偷偷导航0；重启/重读完成集一致。
7. 非司机/非所属乘客/过期stage拒；尾号错误拒、到达前不泄漏尾号、ARRIVED未补终点拒；预约资质/冲突不绕；未确认所有WP不能送达。
8. GPS有效/过期/差精度/缺失、内外300m及metadata2m边界，乘客同意/拒绝/过期/context变化/重复决定；SDK route成功/SDK到达不得自动推进，导航内操作仍走这些原handler。
9. 记录现“WP可乱序确认”的兼容测试或经root决定独立加序规则；不要假定已有保护。电话仅公司确认ACTION_DIAL；无配置明确反馈，无真实乘客通话承诺。

优先定向现tests/test_passenger.py、tests/test_trip_distance.py及必要tests/test_original_integration.py新增/复用相关用例；原套件只在实际实现后由不同会话运行适用回归，诊断不全套重跑。所有上列场景待作者实现自测+非作者验收，待合并SHA未有，deploy_owner=root。
