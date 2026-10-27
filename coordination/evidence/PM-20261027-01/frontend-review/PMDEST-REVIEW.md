# PMDEST客户端独立复核

精确HEAD 0df8b187ac2f68c52fa6d17db846fbb8854b4eda，含786f2bb目的地必填及0df8b18最后一处过时文案删除。root作者，frontend_review 01a17cd2-bcf7-7931-a94b-62119cefce48非作者；产品只读。

结论：本轮客户端差量PASS；未修改云端订单、未重启设备。SDK整体不通过的结论独立保留。

- Android BookingScreen已删除destinationPending状态/开关/请求分支，名称与经纬度均为必填创建字段；新增请求不发送pending=true。普通界面无“先叫车/稍后补目的地”选项。
- 小程序home已删除状态、togglePending及开关，终点行常显；submit不受旧flag影响，缺目的地立即返回错误；完整终点始终进入payload。
- 独立真实shared.base流测试通过：缺目的地+旧flag=true不调用创建；STANDARD/BID/RESERVED三类型均携带完整终点、不发送旧flag；从旧无目的地订单repeat后再次创建仍被阻止；历史详情updateDestination继续发order.update且精确同ID，未误变为创建。
- Android与小程序历史专属destinationPending详情/补填入口保留。0df8b18只删除普通详情通用Note中过时宣传，未删历史面板。后台拒绝旧App直接true请求及历史兼容已有4组非作者HTTP/CAS证据，本角色不重复。
- 实跑定向2项mini旧flag测试PASS，另独立真实数据流与Android/WXML入口约束probe PASS；复用20项Android构建和62项mini日志成功。最后纯字符串删除不重复整套。

证据：PMDEST-targeted-mini.log、PMDEST-independent-flow.cjs/.log及PMDEST-REVIEW.json绑定5文件哈希。新最终App GUI与微信真机NOT_RUN；没有以SDK旧QA截图冒充此新要求已实测。
