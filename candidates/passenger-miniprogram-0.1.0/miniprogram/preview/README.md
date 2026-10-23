# 浏览器离线视觉预览

此适配器直接读取 `pages/*/index.js`、`index.wxml`、`index.wxss` 与 `lib/`、`config/` 源码；没有另外编写一套业务页面。

重新生成源包：`python3 preview/build.py`。

双击 `启动预览.command`，或从小程序目录运行 `python3 preview/start_preview.py`。启动器自动重新生成源码包，以独立后台进程仅监听 `127.0.0.1:8788`；重复启动会核验本目录身份并复用。若端口被其他服务占用，明确报错且不会终止或替换该服务。不创建开机常驻项。服务日志保存在 `preview-server.log`。

访问 http://127.0.0.1:8788/ 。可使用 `#orders`、`#profile`、`#places`、`#order-detail?id=PORD_VISUAL_DONE`、`#settings?section=invoices` 等地址直达页面。

适配范围为布局、模板条件/循环、表单、按钮和页面导航。原生地图显示明确占位；扫码、权限、地图插件、图片上传、真实微信控件和运行性能必须在微信开发工具或真机中验证。预览不能替代微信编译或真机验收。

浏览器仅使用固定脱敏离线 state fixture。适配器不提供业务状态机，不发送云请求，不读取浏览器原有凭据，也不允许模拟创建、支付等写入成功。所有写入被拒绝并提示“请在微信开发工具中验证”。
