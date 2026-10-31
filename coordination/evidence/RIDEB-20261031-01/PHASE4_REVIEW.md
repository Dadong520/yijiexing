# RIDEB Phase4 金额布局差量源审

**PASS（仅单文件布局差量源审），P1=0/P2=0。** 精确候选 `0ef2a8d3982dfca0e85a3b756a6004c1825ba798`，基线 `b3bc07dbecbd49d3b5213d96cb11aef53e9ad644`。releaseReady=false，frontend新头编译/金额与取消确认smoke待汇总。

OrderDetail.kt:165–187：金额从与两个按钮共用的Row移到独占全宽Text；辅助按钮置于下一FlowRow。原条件`o.optBoolean("destinationPending")`、待定文案、`¥`前缀、`money(o.optLong("fare"))`表达式完全一致；未改金额字段、计算或formatter。原字号/粗体/颜色保留，增加28.sp行高与fillMaxWidth。IdentifierButton(id,"订单信息")及contactPage=true/“联系与帮助”保持原内容、顺序和回调。

已对旧Row之前及“行程路线”Panel之后执行精确文本比较，**前后代码字节一致**：阶段、报价、所有确认谓词/回调/请求字段、返回保护、底栏取消支付及弹窗均未变。产品差量只OrderDetail.kt；其余业务源继承phase3，未扩审/重测。

全宽消除了原按钮挤压金额的布局原因，但没有增加maxLines/softWrap限制，不能从源码保证任意巨大金额/字体都不换行。正常屏与360dp/字体1.3的实际显示及取消确认由frontend实测；不把代码注释的“never split”当无限场景保证。

实际：中央门禁PASS；HEAD准确且clean；immutable diff与前后文本比较PASS；git diff --check exit0。build/JUnit/native/API/DB/云本轮NOT_RUN，不重复frontend。原Phase2 FAIL、Phase3 PASS与其他旧证据保持，hash在JSON。root结合准确头69单测/原包QA/两个尺寸金额与取消确认smoke，明确继承B3其余原生证据后最终验收、合并发布。
