# PMVA-20261028-01 打包与回执独立复核

结论 **PASS_SCOPE**：只验收coordinator制作的新版本打包、公开包身份/签名、整包hash与构建/测试回执绑定。本角色为PMVU前端作者，不将此报告当作自己前端业务实现的独立验收；该业务独立验收由coordinator负责。

固定源树 `/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027`，branch `codex/operations-v2-integration-20261028`，HEAD `817ed22bce503620d692e097a5f8ea996edf5fb2`。审查前后相同，git工作区干净，无源码修改。

|包|版本|包名|重新计算SHA256|
|---|---|---|---|
|乘客|1.1.14-experience / 16|cn.hbzjhs.dc.passenger.cloud|3130dd4e7ec7aa8e90f89f93d2b6979f1ec7569afe0db2a8d6e654a32720de48|
|司机|0.3.14-cloud / 17|cn.hbzjhs.dc.driver.cloud|b2166efab8b8f7ba2a0a7ff1a51237699eeb9d7000f5c8301546279b54a6445f|

两包实际字节数分别107529637 / 108636805，与APK_PROOF.json一致。独立运行aapt dump badging和apksigner verify --print-certs，全部退出0；实际包名/版本与表中一致，签名验证成功，两包公开证书SHA256均为 `9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f`，与原证书及proof相符。没有读取私有签名材料或凭据。

Git完整组件目录树独立比较：司机 `candidates/driver-0.3.6-r2/android` 在d5afe915eab8d0ad3cfc638a26740954eb86acf0与817一致；乘客 `apps/passenger-android` 在770781cc5c5d5ac58e046db7c70e7389c995f6b2与817一致。树ID保存在result.json。这里证明回执声明的构建源与固定组件一致，不将其夸大为可复现构建或运行功能证明。

回执核对：FINAL_INDEPENDENT_HTTP.json记录固定817的7项、backend-results.json记录固定817的13项，均无failure/error并声明测试期间源码未变；本角色分别重算其19 / 41个文件散列，均与当前文件及817提交对象一致。没有重复执行这些业务测试，不冒称为本角色实测。

ADMIN_DOM_REVIEW.json明确来自真实隔离BrowserHandler、SQLite/MemoryCAS与CUA DOM，8项均记PASS，含公司保存/绑定、公告草稿发布撤回、地区发布、VIEWER只读、待审图片、待办1→审核后0、旧表单409。其webFinalSource770与817的整个admin-console/web目录树相同；实际加载backend为83f8944，后续后端开票投影/审核说明改动的界限已在原记录说明，最终后端由上述817 HTTP回执补齐绑定。本角色仅审核证据来源、树和限制，未重做GUI或自验本人前端业务。

NOT_RUN：安装/设备GUI、微信运行、成功SDK鉴权、云端发布/网络下载回读、重复Key扫描、前端业务自验。没有启动设备/浏览器/服务/云命令，没有读取Key或账号，不提交/发布。APK和缓存均外置，不复制入仓库。coordinator统一决定合并发布，不能据本报告称三端全部业务最终完成。

实际方法：Python流式SHA256/字节数；SDK34 aapt/apksigner；git rev-parse <sha>:<component>；对回执sourceHashes同时核工作文件和git show固定提交字节；审查前后git status/HEAD。详result.json。本轮无待合并产品SHA，无资源常驻。
