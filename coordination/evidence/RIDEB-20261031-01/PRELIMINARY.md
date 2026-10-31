# RIDEB-20261031-01 基线初诊（非最终验收）

状态：PRELIMINARY_SOURCE_DIAGNOSIS；最终验收 NOT_RUN；releaseReady=false。
只读 Git 对象 c261d78c0108067b6057ff655d1396ec135d815e，来源 /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031，branch codex/ride-flow-polish-20261031。未读取并行产品改动作为冻结成果，未改产品/Store/后端、未访问网络或数据库。待合并 SHA 未提供。

## 根因与数据契约
- apps/cloud-service/passenger.py:190–209：历史来自本乘客订单的 origin/destination，未定终点跳过，按(address,lat,lng)去重最多20；输出 address/latitude/longitude/coordinateMetadata。订单先按进行中优先排序，所以不保证严格按最近时间排序；本轮不扩展历史排序需求。
- BookingScreens.kt:475–481、627–636：调用方把 historicalLocations 复制后标 label=历史地点；读取字段与服务端一致。没有证据支持“字段不一致导致历史全部失效”。
- BookingScreens.kt:748–755→618–620：有坐标的历史行点击只 selectPlace 更新局部 name/lat/lng/source/metadata，取消旧检索；不会触发 save 或退出。650–657 的“使用此地点”才回填。因此基线两步流程可解释用户所见点击未回填；并未实机复现，不能断言不存在别的触摸/布局原因。
- 无坐标行 744–751 只 searchFor(name)：清除旧选择后按名检索；无有效结果不能回填。直接保存分支必须以名称及有限/范围合法坐标为门槛，不能仅用字符串非空判断（当前hasPoint只判断非空）。保留同名不同坐标与coordinateMetadata绑定，不以名称独自去重/匹配元数据。

## 三个调用方及直接保存边界
1. BookingScreens.kt:475–513：四种目标 origin/destination/waypoint1/waypoint2，回调只改当前账号草稿并关页，不创建订单。新的一击选择适合在这里执行；起点仍置 originTouched，元数据与坐标成对替换。下单仍由独立 order.create 按钮触发。
2. ProfileScreens.kt:231–269：回调仅改常用地址编辑缓存并回编辑页，随后“保存”才 favorite.save；一击选址可用，但不能替代标签/默认地址的原保存确认，持有 PassengerModalSlot 不能丢。
3. OrderDetail.kt:415–432：回调直接 store.action(order.update)，不是草稿。passenger.py:341–360 限制阶段/已报价与司机接单条件，重算路线、fare/estimatedFare/mileage 并清除 destinationPending；此操作有既有订单与估价影响。共享组件改成选择即save会把“点结果”变成网络修改，必须保留这个调用方的明确提交确认（可配置选址策略或先回填局部待提交值）。取消/返回不得发请求；后端阶段/报价/同起终点/300km限制不可绕过。回调432立即关页，不能把“已选择”文案等同接口成功。

## 途经点迁移约束
- BookingScreens.kt:52–83：草稿按owner隔离，两个途经点各name/lat/lng与showWaypoints为rememberSaveable。当前入口438–447在行程选项页；删除会清空六字段并showWaypoints=false。
- 迁移到目的地页时必须保留父级同一草稿、账号key及配置重建保存；不要在打开/关闭目的地页时重建或清空途经点。选择途经点后仍有可继续选择最终目的地/返回目的地页的明确路径，不能因通用 editor="" 意外回主行程页。
- 最多2个，原顺序提交；删除第1个应连同第2个清除或明确前移，避免隐藏第2个仍随payload提交；删除第2个不应清空第1个或最终目的地。关闭/返回不应隐式保存尚未选定的地点，也不能丢已经明确选中的草稿点。
- BookingScreens.kt:166–182 仅showWaypoints时提交非空项，每项用coordinate校验。后端passenger.py:267–280再次max2、有效坐标、按数组sortNo归一，路线原点→点1→点2→终点影响估价；不要只改显示而漏发/乱序/保留隐藏点。

## 必须保留的安全与集成条件
- 所有有效选择路径（搜索/历史/收藏/地图逆地理）共用同一验证/回调入口；只在明确用户选择后回填，初始化地图中心不是选择。
- query/city变化清空旧点；选择/关闭/地图新点 invalidate gate 并取消旧任务。仍保留400ms防抖、新请求世代、迟到结果不可覆盖；地图只在逆地理成功且token仍有效后选定。
- 不能从选择地址触发叫车、付款或无条件修改既有订单。保留GPS人工选择优先、账号owner边界、全局弹窗优先级与地图scope恢复。

## 最终冻结后的针对性验收场景
1. 有效历史/收藏/搜索结果一击准确回填且关闭（草稿/Profile），名称及坐标/元数据对应，无order.create/payment；同名不同点不混淆。
2. 无坐标/NaN/越界历史转检索或明确错误，不产生空/0/旧坐标；检索失败/取消/返回不提交。
3. 连续查询、选A后输入B、快速切城市/地图、关闭后迟到回包、账号切换：旧结果不保存不覆盖。
4. 目的地页0→1→2个途经点、拒绝第3个、改/删/返回重开/配置重建/换账号，最终payload与顺序一致；仅明确叫车才POST。
5. Profile选择只回编辑缓存，取消不favorite.save；订单详情选址后明确确认才order.update，取消零请求，失败不声称保存；后端阶段与报价限制继续生效。

## 实际检查与未运行
实际执行：git ls-tree列举passenger Kotlin；git show基线逐个核对AddressDialog三调用方、Booking草稿/行提交、passenger.py历史/创建/更新处理；只读源诊断完成。
NOT_RUN：新实现差异、构建/unit/native交互、后端集成/数据库/云、真实provider/OEM、最终独立验收。基线源码检查不等于业务PASS，也不是发布许可。
下一步：协调者交Claude实现者采用以上边界；提供冻结准确SHA/自测证据后再独立审查。作者自测不能代替独立验收。
