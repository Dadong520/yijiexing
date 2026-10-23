# 微信小程序开发版独立只读复核

## 最终稳定源码增量复核（2026-10-24）

收到稳定信号后仅本地复跑受影响 ui-flow/map-picker 11/11 与原独立探针 4/4，共 15/15 通过；项目静态检查通过（8页面、18 JS）。最终绑定 56 文件，执行前后 SHA256 一致。未重跑无关全量或后端45项。

本轮覆盖中文状态/北京时间显示、删除途经点即时清除地图marker、插件返回异常toast、支付界面隐藏版本字样后仍保留冻结与刷新比对、选点权限拒绝恢复、503请求跨账号切换及重启恢复。详情已不展示“技术订单编号”和“费用版本”文字。空起终点安全显示由源码阅读/前轮检查确认；预览 textarea/CSS 的Cua结果由根会话提供，本独立检查不重复宣称已做设备验收。

最终证据优先于下方早期基线：
- miniprogram-61-final-source-hash.json
- miniprogram-61-final-affected.log
- miniprogram-61-final-independent.log
- miniprogram-61-final-project-check.log

未操作云、设备、部署或启停。云暂停状态未改动。仍未运行微信原生编译或真实权限；无 AppID 的定位资格、合法域名及正式发布均未验收。此次本地任务已完成并停止。

---


结论：本次绑定源码的 Node 测试 34/34 通过，额外独立边界探针 4/4 通过；项目静态检查通过（8 页面、18 个 JS 解析）。本次未发现需阻止可导入开发版交付的已证实业务问题。微信开发者工具编译、真实微信 API/权限、域名配置及真机运行均未执行，不宣称微信运行验收通过。

证据：miniprogram-61-source-hash.json、miniprogram-61-tests.log、miniprogram-61-independent.log、miniprogram-61-project-check.log。源哈希绑定 55 文件，测试前后一致；后续变动不自动继承结论。

## 复核范围与结果

- app.json 8 个页面四件套存在，静态 require 路径可解析；JS 可以 Node VM 解析。WXML 标签平衡，当前绑定未直接调用 JS 方法，事件绑定对应页面或共享方法。该检查不是原生 WXML 编译器，不能证明底层库兼容或真机 setData/组件行为。
- project.config.json 为 touristappid；默认 native 选点，没有声明未经授权的插件。permission 与 requiredPrivateInfos 声明 chooseLocation。启动不定位，用户主动地图选点才调用 API；取消返回 null，拒绝权限返回可见错误，未伪造当前位置。独立探针验证拒绝后下一次选点仍可用。真实平台隐私授权与接口资格需要正式账号验证。
- 固定体验地点明确标注，地图也声明不是当前位置。地图 GCJ02 转 WGS84 存入订单；显示时转回 GCJ02。额外独立探针使用真实转换函数穿过 home.applyPoint/drawMap，回显与原地图坐标误差小于 1e-7 度。已有/历史 WGS84 点不会重复反转换。
- token、访问码没有硬编码到运行源码；访问码由用户填写，仅允许固定 HTTPS 体验服务域名，API 只允许乘客端路径。storage 为普通本地持久化，不等于硬件加密存储。
- 503、超时、无效成功响应保留原 requestId/payload；重启同请求重试。额外探针验证付款 503 后切 P2 不见 P1 pending，切回 P1 再重启仍恢复精确请求；旧会话响应不能覆盖新会话。mutation barrier 防止刷新先于未确认写操作。
- 支付冻结 id/expectedFare/fareVersion，议价额外冻结 amount/quoteVersion，确认后刷新重新比较，金额 A→B→A 的版本变化仍被区分；重试保持旧请求供后端明确判定。
- 小程序首页将 WAIT_PAY 作为乘客未结束订单，禁止新叫车；议价新认领由后端欠费门禁兜底。此目录只调用乘客接口，不修改司机占用策略。司机 WAIT_PAY 不占用与欠费乘客不能再叫车已在独立冻结 v8 后端 45/45 报告验证，本轮不将 mock 当作新增后端或云端验收。

## 过程事项

最初读到 package.json 引用的 scripts/check-project.js 尚未存在，已通知根协调。owner 集成完成后该文件已出现，并在本次哈希绑定后运行通过，因此该事项已解除。

## 必须保留的交付说明

无 AppID 的开发版可以作为开发者工具导入源交付；不能据此声称已获得微信定位接口权限、插件授权、request 合法域名或正式发布资格。项目保留 urlCheck=true，正式账号后需要配置服务域名。无真实微信运行结论。

参考原生模板基础约束：腾讯官方 WXML 模板文档 https://intl.cloud.tencent.com/zh/document/product/1219/60345 。本复核仅使用模板数据与简单逻辑表达式原则，未用第三方文章作为编译结论依据。
