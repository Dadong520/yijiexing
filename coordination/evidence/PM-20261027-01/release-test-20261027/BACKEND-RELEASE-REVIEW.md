# RELTEST backend 限定复核

结论：**PASS_SCOPE_TEST_RELEASE**。可由唯一coordinator将本次独立体验测试版交给用户验收；不是全项目业务完成结论。未发现当前合并范围新增的后端发布阻断项。

指定基线 `35aa7bbf3d68d458270e26818f75132c15b77536`；实际测试版本 `54e6d3134db821a01554f2d0ed5aefecc381a632`。两者 `apps/cloud-service` Git差量为空，后者仅两Android版本号与协调文档变更。测试前后全部后端Python SHA256一致，列表见backend-results.json。PMPOST83的trip_presentations.py不存在于发布树，真实排行/新增接驾距离/登记电话投影不属于此版。

审核者backend_review，真实threadId `01a17ccb-1aa7-7773-a300-46743666876d`，协议ACK2026-10-26-v3；中央门禁PASS。产品只读，未合并/提交/部署，未读取私有Key或访问线上API/数据库。

## 新执行的合并回归

实际命令（中央根执行）：`python3 coordination/evidence/PM-20261027-01/release-test-20261027/backend_merge_regression.py`。13测试、0失败、0错误，3.282s，均用本地临时HTTP/SQLite/CAS夹具。完整日志backend-merge-regression.log，具体13名称及源码哈希backend-results.json。

- 待接/已接预约不阻即时单，旧客户端执行中顺序优先；同乘客双司机及同司机双乘客真实HTTP并发均恰好一个200、一个409，失败方无半写。
- 乘客WAIT_PAY欠款仍阻预约开始；取消后的canonical归属避免陈旧driver投影误阻。
- 尾号只在本人绑定且ARRIVED显示，其他阶段/旧幂等缓存/取消改派隐藏，原尾号校验仍有效。
- 付款只入账一次，重复退款只冲正一次，原余额/credit语义保持；旧缓存按当前退款状态投影。
- 角色不匹配和匿名state拒绝；两AndroidKey按角色读取（测试合成值），Key与JSsecurity不写DB。源码仅返回Android字段白名单，无JS安全码出站路径；未读取真实凭据。
- 新无终点订单三kind均400且无副作用；注入旧历史待补单仍能补终点、确认、完成付款。
- PMAD草稿持久/版本冲突/归档、人工记录关联查询和归档兼容，相关操作不改业务账本。

## 复用与独立性

PMBE566与预约bda的此前非作者范围报告继续有效。domain/negotiation/trip_distance/server与已验4a7ca46同字节；passenger/admin_console/operations_drafts与已验018ca49同字节，证据backend-source-binding.json。PMAD ea41及PMDEST018由本审核者实现，故此次只称合并兼容回归；其实现独立验收复用coordinator已记录的6组/4组HTTP/CAS PASS，不自称作者自验收。未重跑208全套。

新增PMAD建表仅自身ops表，未添加旧订单/余额批量迁移；PMDEST仅拒绝新创建，不自动补写/删除历史订单。没有主动重写旧35单的新增代码。本轮未访问实际云端数据，**旧35单部署前后实际指纹核对NOT_RUN，由coordinator发布回读负责**。

## 必须随测试版明示的限制

1. 乘客高德POI1008／地图10008仍为已知失败，公共点归一化与真实SDK成功校准NOT_RUN；不能称地图鉴权已通过，也不应阻塞用户测试无关功能。
2. PMPOST83未集成，相关排行/新接驾距离/登记电话功能不包含本版。草稿32类仅DRAFT/人工记录，无生效、资金分账、罚款、服务分、积分券计算，人工记录不代表真实到账/开票。
3. 微信缺AppID，微信真机/发布NOT_RUN。声音、后台/OEM与部分Android GUI仍待实际验证；支付/退款/认证沿现有演示边界。
4. APK构建/签名/下载hash、IAM/auth/环境/min0max1、部署健康与35单指纹回读不由本报告代验，唯一发布者coordinator负责。不得借此报告访问原环境。

本角色已退出所有本地测试服务器，无新增云端订单或资源。产品未改，证据与短卡已交回，等待协调者唯一发布。
