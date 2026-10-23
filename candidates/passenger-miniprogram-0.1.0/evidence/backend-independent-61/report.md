# GPT-6.1 独立本地复测

冻结后端 35 个清单文件在运行前、运行后均匹配 SHA-256，未改动冻结源码。清单 SHA-256：246aa424aedef52b746825ada8416ed59a48f99e7c4838c01f17aec5f539fd30。

复跑开发方完整测试：159 项通过，53.619 秒。独立新增测试：4 项通过，0.759 秒；合计执行 163 项，无失败。新增探针覆盖金额/版本的负数、小数、字符串类型，requestId 跨动作及付款结果碰撞，121 字符 requestId，旧单晚付款保持新预约单完整状态，以及旧订单推进拒绝。

完整回归包含 WAIT_PAY 释放司机、乘客欠款阻断、私有欠款后共享新单、晚付不改新单、并发与跨实例 CAS、不确定提交重试、金额 ABA 与旧客户端、expectedStage、证件日期边界及旧行程可继续、司机投诉投影与旧幂等缓存隐私测试。完整回归为独立执行既有测试；4 项额外测试为本次独立编写。

## 可复现命令

在 /private/tmp/yijiexing-passenger-v8/cloud-service 执行：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_*.py' -v
```

在项目根目录执行：

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=/private/tmp/yijiexing-passenger-v8/cloud-service:/private/tmp/yijiexing-passenger-v8/cloud-service/tests python3 -m unittest discover -s yijiexing-passenger-20261023/qa/wait-pay-release/independent-61 -p 'test_independent61.py' -v
```

原始日志为 full-regression.log 和 independent-probes.log，探针源码 test_independent61.py；SHA-256 见 artifact-hashes.json。测试仅调用本机 127.0.0.1 HTTP、临时 SQLite 与 MemoryCAS；没有部署、设备操作、真实支付、云访问或 .private 访问。

Antigravity：仅运行 agy --help，发现 --model 参数与 models 子命令，但 help 没有证明 GPT-6.1 是可用模型。没有运行 AG 推理、延续旧 Gemini 会话、读取凭据或更改全局配置；因此本报告不能称为 Antigravity 测试。输出保存在 agy-help.log。

日志存在测试 HTTPError 对象清理的 ResourceWarning；不影响断言通过，不将其解释为业务失败。上述结论只适用于冻结后端的本地契约测试，不代表小程序/Android、云部署或真实用户体验验收。
