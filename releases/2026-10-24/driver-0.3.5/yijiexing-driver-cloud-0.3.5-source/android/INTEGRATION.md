# 云端 Android 接口边界

## 演示 API

沿用独立服务 JSON 信封：成功 `code=0,data={...}`；失败 HTTP 非 2xx，返回 `message` 与可选 `details.reason`。

所有请求（包括获取验证码和登录）均带 `X-Demo-Access`。已登录请求另外带 `Authorization: Bearer <driver session>`，不得使用管理令牌。客户端不跟随 HTTP 重定向。

| 状态 | details.reason | 客户端处理 |
|---|---|---|
| 403 | DEMO_ACCESS_REQUIRED / DEMO_ACCESS_INVALID | 提示在连接设置更新访问码；停止自动轮询 |
| 503 | DEMO_PAUSED | 提示云端已暂停；停止自动轮询和位置服务，等待手动恢复 |
| 401 | 其他 | 清除失效司机会话，返回登录 |
| 网络超时 | 无 | 保留原会话与服务器已读数据，提示稍后重试；变更操作不自动重发 |

服务公开 origin 通过 `YIJ_CLOUD_ORIGIN` 构建属性注入。非秘密 origin 精确匹配；空值禁请求。客户端不提供任意 URL 编辑功能。

演示手机白名单由服务端执行，客户端仅作输入提示；验证码直接显示不构成真实短信验证。访问码必须另行交付且不进入 APK。

## 继续沿用的业务边界

后端最终裁定司机状态、试用到期、权益继承、报价金额、乘客支付结果、提现及审核；客户端读取结果，不把本地按钮点击当作成功。真实支付、签约、银行转账、实名认证、发票和推送服务仍需正式渠道接入。

演示环境独立部署、独立存储，原 Java 微服务源码已用于接口和业务分析；此交互版本不代表其所有外部依赖已启动。

## 035冻结r3构建及验证边界

版本0.3.5-cloud/code8，YIJ_CLOUD_ORIGIN=https://driver-demo-491829048305.us-west1.run.app。origin与冻结r3一致，构建默认仍空以防误连。访问码及有效会话不入包，管理员沿用IAM与角色限制。

<!-- RELEASE_FACTS_START -->
035最终业务与发布核验：主任务已确认联合及公网证据。

联合验证：司机035r3/code8与乘客1.1.5/code7完成本轮联合新单。STANDARD PORD97596bf0cc68：错误尾号被拒、模拟支付失败未入账，成功后DONE仅增加2926分，余额52848→55774分，评价5星；司机投诉CP59d6e38495d3与乘客投诉PCa6f2b8b98513的后台回复已在对应App实际可见。BID PORD2a42ed9559c3：模拟预付3600分，00:49:46司机取消即时模拟退款3600分，乘客结束后最终CANCELLED；余额55774分不增，DRIVER_CANCEL与PASSENGER_CONTINUE各一次。后台权威/台账61断言passed，属于数据读回，不替代APK实际UI。新BID司机费用页未另截，早先只读退款费用页不算本新单证据。乘客owner的cleanup记录确认司机下线、两设备字号1.0并退桌面；组包代理未操作设备。以上均为沙箱模拟，未做真实资金或道路行驶验证。

公网发布：主任务已于2026-10-24 00:54:08.950881+08:00完成公开下载地址全量GET：HTTP200、系统TLS证书验证通过、54,757,323字节，SHA256 6b9620a237286002662a966a31ba110f50d3a13c2ca4c113961792b32ee0b4ea与冻结r3一致；包名cn.hbzjhs.dc.driver.cloud、0.3.5-cloud/code8、签名SHA256 9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f兼容性核对通过。后端未重新部署、原测试环境未改变；本次源码组包代理只读取主任务证据，未下载/上传/操作云端。

Antigravity本轮仍为BLOCKED_PERMISSION_NOT_TESTED、0次UI操作，不计作通过。详细限定和证据hash见provenance/final-release-facts.json。
<!-- RELEASE_FACTS_END -->

035调整大字/触控/布局，034取消退款/未计收入及精确v7后端冻结。r3既有40项JUnit通过；Codex界面有范围验证见顶层说明，Antigravity权限拒绝且0次UI操作，不计为通过。API合同原文保留编写时点参考，资金结果均为模拟。

首页todayIncome/todayAccepted/todayCompleted含seed.py:22的11000分/2/2合成初值；domain.data不按日期重算，credit继续累加，属于演示累计值，不能作为按日经营报表。本次只更新说明，不改冻结代码，也不做设备/API/云操作。
