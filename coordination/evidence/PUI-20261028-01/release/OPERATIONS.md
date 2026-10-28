# 本次运行与回退

仅独立项目yijiexing-driver-demo-1023/us-west1；API driver-demo-pui-7d6dc60-test1、admin driver-admin-pui-7d6dc60-test1各100%，user-test tag同新revision。保留旧pmv-817ed22-test1 revisions、各自pmv-qa tags。

若需回退由coordinator统一安排：先让API/admin流量及user-test tag指向原pmv-817ed22-test1；不改ENV、IAM、持久数据或伸缩计费。新defaults只追加已有extras/profile内容，旧客户端可忽略。APK旧包位于本回执apk-promotion.json记录的generation固定归档对象，恢复时对当前固定对象代次做条件写入，避免覆盖并发发布。不要还原数据库来回退界面，否则会抹除后续用户数据。

本轮私有发布前/后服务、IAM与SQLite备份在private/yijiexing的puir前缀，0600，不进源码；新包/构建位于外部pui-release-build，QA包仅隔离本地。CloudBuild镜像digest与源字节manifest见本目录。没有自动保活、暂停或未来后台任务。
