# 根集成审查要点（尚非验收结论）
1. 现前台10s/后台15s基础轮询，远长于5s候选窗口。仅给自己已报名candidate短轮询不足以让其他听单司机及时看见新单/OPEN截止；Phase2后审适应为仅真实上线听单期间较短间隔（如2s），已OPEN/PENDING约1s，非听单/在途沿原较长间隔与退避。前台store与后台Service+candidate tracker须单一轮询owner/去重，避免三套重复请求；无用户使用不保活，停止听单/退出后不轮询。正式验收测首抢后其他司机的UI可参与/截止关闭，标网络更新延迟边界，不承诺断网也实时。
2. 同步发布API时旧历史QA tag若仍指旧代码且共享同DB，旧tag的order.accept可绕过新5s规则。此轮必须将**driver-demo所有现存tag/默认100**统一指向已验新版（保留tag名/公开URL，不保留旧业务writer），移除本轮临时staging tag；不是沿上轮NAVI仅default+user-test留旧QA target。admin服务整体与tags/IAM完全保持，原env/SA/scaling/API900/concurrency1保持。先新revision no-traffic+暂存tag smoke/备份，再一次明确路由切换并对所有旧API tag做只读新contract识别回读，避免旧写规则继续触达同库。不访问原测试/生产，不扩IAM。
3. Exact product/metadata SHA与独立验收后唯一root发布；旧46可能新增用户订单，发布前后原子完整快照按当前实际行比较，不还原旧46。现仅driver25+API，passenger23源/安装包预期不改，后台产品/运行不变。
4. 作者自测不算root独立验收；backend实际HTTP/CAS/截止错误rollback、driver纯规则+原/QA构建+实际本轮隔离GUI。3s提醒与5s候选独立，真实归属+round+阶段才导航，旧WON不授权；本机下载若慢保留失败，可同独立项目一次性公开完整下载SHA绑定原包验签，方法清楚。
