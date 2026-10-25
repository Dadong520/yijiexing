# 本轮发布与回退

发布合并PR23：af7ec4ce6c618cf0b984c54a76ba7024751e5e10。精确产品集成edb532549506abd962e0a2000015857b6ec6d6ce；后续回执提交不改产品。

- 管理地址：https://demo-admin.hbzjhs.xyz/；备用https://driver-admin-491829048305.us-west1.run.app/。先选原已授权Google账号dadonggev587@gmail.com，再登录原管理账号。密码不写源码/文档，沿用用户原保管凭据。
- 司机0.3.12-cloud/code15：https://yijiexing-demo.hbzjhs.xyz/download
- 乘客1.1.12-experience/code14：https://yijiexing-demo.hbzjhs.xyz/download-passenger
- 两包保持原签名，可直接覆盖原体验版，保留原账号。无演示访问码。
- API固定https://yijiexing-demo.hbzjhs.xyz，之后更换底层IP不要求因IP重新打包。demo.hbzjhs.xyz原交互演示保留。

仅独立项目yijiexing-driver-demo-1023/us-west1。Cloud Run自动伸缩、按请求计费、min0/max1，无访问自动缩至0；结束后不手工暂停。不新增常驻代理或负载均衡，IAP/IAM/env/SA维持。下一次访问会自动恢复，可能有冷启动等待。部署账号通过本机既有gcloud授权使用，不把账户库或密钥入库。

## 回退点

当前API driver-demo-distance-8ec8419d，上一版driver-demo-publiccopy-ecab94fd。当前admin driver-admin-trip-2863623f，上一版driver-admin-sidebar-7f489c24。先检查服务当前100% revision没有被其他发布者改变，再明确更新流量到指定旧revision，不使用to-latest。

示例后台回退：
```
gcloud run services update-traffic driver-admin --project=yijiexing-driver-demo-1023 --region=us-west1 --to-revisions=driver-admin-sidebar-7f489c24=100
```

两APK上一版服务器端备份与当前generation见ALERT-20261025-01/{driver,passenger}-rollback.json及public-release.json。回退先describe当前对象并确认generation仍等于本次发布receipt；使用if-generation-match条件将指定备份复制回原对象。保留备份/历史订单，不清空数据。下载后重新校验整包SHA和签名。

本轮两APK均去代理直连上传，并从公开HTTPS地址完整重新下载校验200/TLS、SHA、版本、包名及签名。签名证书SHA256：9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f。
