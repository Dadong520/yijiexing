# PMDEST作者交付

BIZ06用户最新要求取消新增无终点叫车。候选SHA018ca49d7c3b8c7230e6541754729e1a0178ec37，base ea41f17998ad1812f031ac1886fec45895b09f36；唯一写源/Users/dongge/.codex/tmp/yijiexing-admin-drafts-20261027，branch codex/pm-admin-drafts-20261027，提交后clean。backend作者，独立验收由coordinator完成。

## 修改

passenger_create_order最前校验destinationPending类型并拒绝true，HTTP400提示“请先选择目的地后再叫车”，不再为新单填占位目的地。false/缺省保持原STANDARD/RESERVED/BID创建逻辑。历史order.update补目的地、上车/送达/金额确认/支付流程不改；不删除或迁移历史记录。passenger_extras及其它能力声明未发现无终点推荐，不做无关修改。

新增test_destination_required.py四组：全部三类true无新订单/幂等/审计/账号副作用，拒绝后相同requestId可提交合法新单；缺失/空白/非法/同起终点拒绝；三类false和缺省正常创建；本地CAS导入旧形态后补齐目的地→上车→结束→模拟支付完成。

受影响原用例仅test_passenger.PassengerIntegration.test_no_destination_pickup_requires_completion_and_confirms_price（169行）。已按coordinator新增授权改成正常单后在本地CAS更新旧形态，不再通过新API创建true；原0元待定/补齐/上车/金额确认断言全部保留。原test_passenger_options/test_admin_queries在本基线没有新建true断言，无须修改。test_trip_distance:147直接注入旧单原写法仍兼容，未改该文件。

## 实际作者测试

- 专项：PYTHONPATH=apps/cloud-service:apps/cloud-service/tests python3 -m unittest test_destination_required test_passenger.PassengerIntegration.test_no_destination_pickup_requires_completion_and_confirms_price：5 tests PASS，2.300s，exit0；author-specific.log。
- 差量：... python3 -m unittest test_destination_required test_passenger test_trip_distance test_passenger_options test_admin_queries：70 tests，69 PASS/1 FAIL，12.445s，exit1；author-regression.log。唯一失败是原有test_trip_distance.TripDistanceRules.test_location_upload_validation_old_driver_compatibility_and_no_attestation_claim的“未来31秒”子例，该测试在列表构造时取秒精度时间，运行耗时可能使其落入现允许未来30秒范围；trip_distance源字节未改。
- 上述单项单独复跑：1 test PASS，0.030s，exit0；time-case-recheck.log。没有修改或skip原测试，不将70项首轮冒称全通过；疑似时间边界不稳定由coordinator独立评估。
- py_compile、git diff --check、中央真实backend记忆门禁PASS。

仅3个获准文件提交，无App/旧PM树/私有Key或线上订单写入，不部署。作者自测非独立验收；Android/小程序入口由coordinator适配，独立复核/云端验证/合并/发布NOT_RUN。
