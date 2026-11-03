# WBB 冻结候选独立审查

2026-11-03；backend，真实session 01a182c6-c058-76e1-9584-38abfe813799。
精确候选 `0ee42cf18ebbf4afb3ea09b41dbe420d9c1f5601`，base `22dfb6a636f6dde1a73c2906252df3d5deffdd24`；source `/Users/dongge/.codex/tmp/yijiexing-workbuddy-20261103`，branch `codex/workbuddy-fixes-20261103`，接单HEAD精确/clean。已读中央当前任务、BOARD、STATE、registry本人条目、本人短卡和作者WBC/PHASE1.md。范围为15个文件（13个src/test/manifest + root两Gradle），完整diff逐项复核，依赖代码定向读取。

**结论：PASS（独立源码差量审查），未发现P1/P2；不等于构建、原生运行或发布验收PASS。** 作者不自验：本审查由backend完成，root正在负责实际构建和原生验证，本轮不并行重复全套旧后端测试。

## 13项复核

1. **业务边界保持**：`git diff --name-only base candidate -- apps/cloud-service .../PassengerStore.kt .../ConnectedStore.kt`无差异。后端API、计费/支付/取消/竞抢规则和3秒/5秒窗口均未修改，原scope/round/contact相关执行代码无差异。仅提前拒绝确定远超服务范围的草稿，不生成订单或变更金额。
2. **漂移恢复**：PassengerPickupDefaults.kt:39 `shouldReplace`仍先校验候选有限/范围/精度≤100m/时效，旧于当前fix不覆盖；新增`:56 moved`要求严格更新且距离大于200m+两精度半径。等精度的跨城新fix可及时替换，近距离抖动不会触发该新增条件。不能由此承诺系统GPS一定准确。
3. **手选保护**：BookingScreens.kt:121位置effect在originTouched或非GPS已有起点时返回；`:333`交换、`:463`历史重走、`:532`地址选择都设置originTouched。自动更新不会覆写这些手选点。手动“重新定位”属于用户显式重置例外。
4. **异步结果边界**：原lookupToken(owner/generation/fix)、acceptLookup手选/automatic校验保留。relocate清空fix并递增generation，旧逆地理结果无法回填；跨账号draft key和完成时currentOwner检查保留。手选effect同样会退役查址。
5. **重新定位**：BookingScreens.kt:166 relocate把floor设为已用fix/当前bus fix的最大时间，清空起点名、经纬度、metadata，重置automatic状态。freshAfter严格`>`floor，再经shouldReplace usable校验。不会复用被重试同一fix，也不填默认坐标；目的地不变。按钮依赖现有前台监听的下一fix（PassengerPermissions.kt:189 GPS/network 10000ms），并未增加立即采样请求/超时承诺；新fix不来时可点起点行手选。这是明确行为限制，不按即时定位宣称通过。
6. **超距恢复**：straightTripKm仅在两点坐标有效时算Haversine；tripTooFar是>301km，300–301边界和途经点全路线仍交服务器≤300km校验。BookingScreens.kt:306明确显示原因及重新定位/手选入口；提交gate在payload发送前；调用受returnShield保护。无起点时原完整性校验阻止提交。用户手选远地点亦可直接改起点，提示未假称已恢复定位。
7. **测量/滚动反馈**：BookingScreens.kt:363对路线Surface相对父布局的底边测量，非window/screen坐标；内容在同一个纵向Column，测量不读scrollState。minHalfContent限制160–360dp；MapPanel.kt:236先让地图让位，halfTop限制合法范围；主按钮仍在独立bottomBar，内容仍verticalScroll。源码未发现直接随滚动偏移累计或无界反馈。路线多途经点、大字体、异常提示/欠款/预约导致路线位置超过360dp时仍可能需要滚动，不能承诺所有尺寸下起终点同时完整显示；稳定性/拖动/返回半展开需root实机观察。
8. **历史去重**：PlaceSelection.kt:79起trim+空白合并名称；同名且有效点≤30m合并，保留列表优先项，常用优于历史；两无坐标同名合并；已知/未知、异地同名、不同名称分开。没有修改数据库/删除历史，也没有改变选点提交gate。名称不同的同一POI仍可能两行，符合保守规则。新增扫描为O(n²)，当前有界地点列表无已证实性能缺陷。
9. **订单号**：OrderDetail.kt:409使用详情现有`id`，放在“订单动态”真实编号KV，未截断/伪造/采用另一个订单；无全局或跨账号检索。原司机查询编号保留。可展开次级区域有助核对，不宣称主屏一定可见。
10. **权限/顶栏**：已登录权限入口由MainActivity转到ProfileScreen“应用与连接”，未登录仍有入口；PassengerPermissions仍全局挂载/原生命周期不变。连接错误横幅、刷新、忙碌提示仍在；“服务城市”仅文案消歧，未把GPS坐标改成服务城市坐标。指南按钮改min48dp允许增高。
11. **同意页/方向**：两端地图同意页仍scroll，FlowRow可换行，明确同意按钮仍是唯一AmapRuntime.agree调用；阅读政策保持外链。两端主Activity仅新增portrait，不改变exported/intent/权限。未承诺大屏/新系统必定锁向。
12. **体验构建配置**：两端experience从release初始化，明确debuggable/JNIdebuggable=false，无applicationIdSuffix；driver27/0.3.24-cloud、passenger25/1.1.23-experience高于原26/24。matchingFallbacks为release，driver ui-tooling仍仅debugImplementation；experience双ARM过滤在buildType范围，debug QA不因此失去x86。MapLibre fallback/高德SDK和min26/target34保持；乘客JNI压缩与司机已有模式一致。实际DEX/manifest/native libs必须验成品，源码不能代替。
13. **升级签名边界**：experience显式用debug signingConfig，与原debug体验分发意图一致，但引用配置本身不证明同一密钥；换机器可能生成别的debug keystore。root须以实际APK apksigner证书与现网基线对比（原SHA256 9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f），并验证原包覆盖安装、数据保留、地图正常。该项本轮仅配置审查，签名实际验证NOT_RUN_BY_BACKEND。

## 实际验证与限制

- `git diff --check 22dfb6a... 0ee42cf...`：PASS/退出0。
- 15文件精确diff和上述依赖源码逐项静态审查完成；HEAD及clean状态写入CANDIDATE_REVIEW.json。
- 记忆结构gate接单/结束PASS；context UNKNOWN，不是业务验收。
- 本轮未运行Gradle/JUnit/AVD/native/APK检查/云请求/部署，避免与root构建验收重复和争用。作者PHASE1编译JUnit也NOT_RUN，不据其回执宣称已测。新增纯单测为8个@Test（Pickup4+Place4）；实际运行结果由root报告。
- 第一阶段已独立跑过基线4个本地HTTP回归+3组同单检查并核实只读快照的不同订单/账号，证据FIRST_STAGE_REVIEW.md/same_order_result.json。该事实与本轮源码边界检查合并支持“不因本差量更改业务规则”，不能替代新APK双端同单原生验证。
- 根会话需重点完成：漂移→新fix恢复、retry后相同fix拒绝/新fix接受、手选不覆盖及旧逆查不回写；Pixel7/大字体起终点与远距提示滚动/拖动稳定；两端原包升级签名/非debug/PreviewActivity无暴露/ABI地图native正常。

本轮只写WBB证据和本人短卡；未改产品/任务/中央控制，不提交/部署。待合并产品SHA为本候选，是否合并/发布由coordinator结合构建、原生验收与准确CI统一决定。审查完成后待命。
