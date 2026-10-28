# coordinator 当前短卡
ACK2026-10-26-v3；真实会话01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6；当前PMVR-20261028-01。context UNKNOWN/not_exposed；本次可见压缩已按START_HERE定向恢复，NORMAL。中央coordination唯一权威；旧总管退休，root唯一发布。

## 当前发布与隔离
线上仍旧司机0.3.13/code16、乘客1.1.13/code15；PR28 main1d04ff7、PR29 mainc3c876e。仅yijiexing-driver-demo-1023/us-west1，禁原环境。API/admin旧revision/digest与入口见STATE；本次fresh私有pmvr-before-{api,admin}.json及IAM已读，旧流量100%未变。保留PASSWORD原账号、IAM/ENV/secret/SA、automatic/min0max1/请求CPU计费、API900秒，不保活。发布前重新备份当前共享SQLite，不假定仍35单。
高德1008/10008仍FAIL；用户设置与APK身份一致勿重索Key；真实SDK同单NOT_RUN。微信缺AppID未编译/真机/发布；无真实支付、分账、积分到账、正式发票。32类旧草稿仍草稿。

## 源码与角色
root /Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027，branch codex/operations-v2-integration-20261028，basec3c876e，产品HEAD817ed22bce503620d692e097a5f8ea996edf5fb2，clean。PMVO0d9+523/33fa/f1、PMVUdf9+b450/519+87已全cherry；不要重复。
可见backend01a168e2-22f7-70a2-af7f-4fb6be0ea695，pmv-backend树，最终f1f9128；frontend01a167c8-a5eb-77e3-b77e-214927a029da，pmv-clients树，最终87aaffc；均IDLE。root不改作者未提交文件。旧admin-drafts/PMPOST83c7c2a clean只读未审未集成。原内部复核子代理不再派单。
Gemini PMVG小候选26秒成功/根11校验PASS未进产品；PMVO大任务超时无代码，精确context4528dabc已核STOPPED、lease释放，沿用户模型未改全局，现IDLE。

## 首批与规则
修改意见2.0.doc下载副本提取28组，证据PMV/SOURCE_TEXT/manifest/矩阵。首批公司客服/司机公司关联、公告、地区乘客功能发布门禁、三证60天/审核替换；后续热力/资金/积分/发票提供方另批。BIZ07单次差额/余分平台仅模拟；BIZ08旧证有效待审可接、过期阻新增在途可完成、审核通过替换；BIZ09每用户每公告已发布版本一次可关。均确认无需重问。固定合同contracts/PMV-OPERATIONS-API.md v1.3。

## 证据及验收
PMV/FINAL_INDEPENDENT_HTTP.json根非作者7组PASS、backend-results.json兼容13PASS，精确817/byteunchanged。覆盖公告同rid/跨实例重启去重、地区草稿发布/API/历史、公司订单快照、有效旧证/过期/在途/审核可选note留审计、RBAC/CAS、跨城逐单invoice投影。root实际BrowserHandler PASSWORD+SQLite/CAS和CUA DOM8PASS：四菜单/草稿发布撤回/权限/409/pending图与计数1→0。ADMIN_DOM_REVIEW.json及截图；晚期后端delta由最终HTTP补盖，不冒称全链真机。
Q1驳回更新、Q2待审图标签、Q3总览计数、Q4跨城invoice、Q5公告busy释放、Q6通过说明留审计均作者修并已独立复核。root本地fixture93383/session87844已stop exit0，IAB tab已close且inventory[]。原Chrome未动。
新APK均build/原签名/aapt/Key4模式0PASS；APK_PROOF.json及PMVA artifact-review/result.json独立打包复核PASS。乘客1.1.14-experience/code16 hash3130dd4e…20de48，107529637字节，源770组件同817，24单测；司机0.3.14-cloud/code17 hashb2166efa…a6445f，108636805字节，源d5组件同817，63单测。build路径外部pmv-release-build-20261028/{passenger,driver}/_app/outputs/apk/debug/app-debug.apk。mini72/admin4 frozen日志PASS。无需重复全套。

## 下一步/资源
PMVR验收PASS/releaseReadytrue待中央exact gate。尚无本轮PR、未上传/部署。新stage外部pmv-release-stage-20261028，root准备API继承旧runtime镜像946602…/admin prepare_browser_build helper按817校验。先备份当前snapshot/旧APK，再候选上传（大文件先direct后代理）、cloudbuild异步精确镜像→no-traffic健康→PR合并→切100%及user-test快链→旧单字段比较/新版整包HTTPS hash回读。私有/Key/账号/DB/APK/cache绝不进Git。所有最新回执写PMV/release/。部署后单独docs回执提交。无常驻、无自动化。
