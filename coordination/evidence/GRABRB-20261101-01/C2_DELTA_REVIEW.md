# GRABRB C2 非作者差量复核

**PASS，当前P1=0/P2=0，GRABRB-P2-01 CLOSED。** 精确SHA `36fdf2c1bddd859f3dff22fbeb2d7e70aa94ec7c`，仅审 `9f9ee9b188d47211872d836cbe73ead88d10135a` 到此SHA的3文件差量（CompetitionRules/MainActivity/CompetitionRulesTest）。前轮未变更部分复用9f9ee9b限定审查，不扩大为完整运行验收。

MainActivity:93由只存orderId改存CancelScope编码；447/458两入口捕获最新store账号与row；539–546按loggedIn及stillValid控制显示并失效关闭；581–597确认按钮直接重读store账号/row/loggedIn再校验，发送CancelScopes.body(captured,reason)，不补用最新round。CompetitionRules:313–367保存account/orderId/intent/round/stage，以open(latest)==captured全字段比较。WITHDRAW只限AVAILABLE/server-PENDING；ASSIGNED_CANCEL需原canCancel及driverId归属；无competition但required时拒绝；LEGACY沿原canCancel且body不含round。

因此原事件路径round1弹窗→round2刷新/另一设备参与或获派被scope比较拒绝；同round候选→获派也失效，不把退出候选变成取消真实行程。账号变化/登出同样不提交；即便Compose尚未重组，点击时校验仍覆盖竞态。相同合法scope继续提交捕获轮次；BID/NEG/private保留旧请求形状，阶段改变时要求重新打开是保守校验。

13项源码结构断言通过；源码事件追踪5类见C2_DELTA_REVIEW.json。新增两项单测覆盖跨轮/账号/退出/候选转获派、已归属阶段/司机改变、TRIP禁取消、缺competition拒绝及legacy缺round；只阅读测试，**本角色未执行JUnit或Android**。编译/native/后端307/云均NOT_RUN，按派单由root负责。完整不可变diff及3个对象hash已保存；先前C1 FAIL证据原样保留。

这是一项客户端P2修复的独立源码验收，非对自己所写后端的自验，也非发布批准。root完成构建/native及整体验收后统一安排合并发布。
