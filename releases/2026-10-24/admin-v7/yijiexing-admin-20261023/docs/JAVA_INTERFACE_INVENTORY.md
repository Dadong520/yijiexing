# 原 Java 管理及业务接口索引

提取日期：2026-10-23。仅从源码提取注解，不能证明接口已部署、已授权或业务已验收。包含 app/移动接口以保留核对上下文；路径未拼网关前缀。未复制账户、数据库数据或密钥。

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DataImportController.java

- 路由注解：@RequestMapping("/data")；@PostMapping("/driver-car-import")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DriverOnlineDurationRankController.java

- 路由注解：@RequestMapping("/driver/online-duration-rank")；@GetMapping()
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DriverOperationStatController.java

- 路由注解：@RequestMapping("/driver/operation/daily")；@GetMapping("/page")；@GetMapping("/export")；@GetMapping("/{driverId}/{statisticDate}")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DriverOperationTaskController.java

- 路由注解：@GetMapping("/driver/service-score/task/settle-online-duration")；@GetMapping("/driver/online-duration/rank/task/refresh")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DriverProfileAdminController.java

- 路由注解：@RequestMapping("/admin/driver/profile")；@GetMapping("/{driverId}")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DriverProfileController.java

- 路由注解：@RequestMapping("/driver/profile")；@GetMapping("/me")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DriverScoreAdminController.java

- 路由注解：@RequestMapping("/admin/driver/service-score")；@GetMapping("/config/page")；@GetMapping("/config/{id}")；@PostMapping("/config")；@PutMapping("/config/{id}")；@PostMapping("/config/{id}/copy")；@PutMapping("/config/{id}/status")；@DeleteMapping("/config/{id}")；@GetMapping("/driver/page")；@GetMapping("/driver/{driverId}")；@GetMapping("/ledger/page")；@GetMapping("/ledger/{id}")；@PostMapping("/change")；@GetMapping("/ledger/export")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/DriverServiceScoreController.java

- 路由注解：@RequestMapping("/driver/service-score")；@GetMapping("/risk/me")；@GetMapping("/me")；@GetMapping("/ledger/page")；@PostMapping("/change")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/FileController.java

- 路由注解：@RequestMapping("/file")；@PostMapping(value = "/upload", consumes = "multipart/form-data")；@GetMapping(value = "/url")；@GetMapping(value = "/obj")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/FptController.java

- 路由注解：@RequestMapping("/pt")；@PostMapping("/invoiceBlueNotifyUrl")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/GuoJiaoCallResultController.java

- 路由注解：@RequestMapping("/webhooks/gj")；@PostMapping("/call-results")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAccountAuthController.java

- 路由注解：@RequestMapping("/hsAccountAuth")；@PostMapping("/driver-reset-password/check-id-card")；@PostMapping("/driver-reset-password/check-code")；@PostMapping("/driver-reset-password/reset")；@PostMapping("/settingPassword")；@GetMapping("/settingPasswordFlag")；@PostMapping("/check-password")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsAccountAuth_add、ride_hsAccountAuth_del、ride_hsAccountAuth_edit、ride_hsAccountAuth_export、ride_hsAccountAuth_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAccountBalanceReconciliationAlertController.java

- 路由注解：@RequestMapping("/hsAccountBalanceReconciliationAlert")；@PostMapping("/page")；@GetMapping("/reconciliation")；@GetMapping("/clear")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAccountBalanceReconciliationController.java

- 路由注解：@RequestMapping("/hsAccountBalanceReconciliation")；@PostMapping("/page")；@PostMapping("/export")；@GetMapping("/download")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAccountController.java

- 路由注解：@RequestMapping("/hsAccount")；@GetMapping("/platform")；@PostMapping("/companyAccountPage")；@PostMapping("/companyAccountExport")；@PostMapping("/driverAccountPage")；@PostMapping("/driverAccountExport")；@GetMapping("/driverAccountClient")；@PostMapping("/page")；@PostMapping("/export")；@GetMapping("/authorizedStatus")；@GetMapping("/platform-config")；@GetMapping("/{id}")；@GetMapping("/accountList")；@GetMapping("/detail")；@GetMapping("/driverDetail")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")；@PostMapping("/accountOpen")；@PostMapping("/entryAmount")
- 权限标识：ride_hsAccount_add、ride_hsAccount_del、ride_hsAccount_edit、ride_hsAccount_export、ride_hsAccount_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAccountSolidifyController.java

- 路由注解：@RequestMapping("/hsAccountSolidify")；@PostMapping("/page")；@GetMapping("/solidify")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAccountSplitInfoController.java

- 路由注解：@RequestMapping("/hsAccountSplitInfo")；@PostMapping("/page")；@GetMapping("/{id}")；@PostMapping("/save")；@PostMapping("/update")；@GetMapping("/enable")；@DeleteMapping()；@PostMapping("/export")
- 权限标识：ride_hsAccountSplitInfo_del、ride_hsAccountSplitInfo_edit、ride_hsAccountSplitInfo_export、ride_hsAccountSplitInfo_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAreaAppFunctionController.java

- 路由注解：@RequestMapping("/hsAreaAppFunction")；@GetMapping("/detail")；@PostMapping("/switch")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAreaConfigController.java

- 路由注解：@RequestMapping("/hsAreaConfig")；@GetMapping("/getAreaConfig")；@GetMapping("/driver-config")；@GetMapping("/passenger-config")；@GetMapping("/order-config")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsAreaServiceFeeController.java

- 路由注解：@RequestMapping("/hsAreaServiceFee")；@PostMapping("/page")；@GetMapping("/detail")；@PostMapping("/add")；@PostMapping("/updateById")；@GetMapping("/updateStatus")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsBalanceChangeRecordController.java

- 路由注解：@RequestMapping("/hsBalanceChangeRecord")；@GetMapping("/page")；@PostMapping("/driverBalancePage")；@PostMapping("/driverBalanceDetail")；@PostMapping("/summaryOrderIncomeAndExpense")；@GetMapping("/driverWalletSummary")；@GetMapping("/driverYesterdayIncome")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsBalanceChangeRecord_add、ride_hsBalanceChangeRecord_del、ride_hsBalanceChangeRecord_edit、ride_hsBalanceChangeRecord_export、ride_hsBalanceChangeRecord_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsBankBranchController.java

- 路由注解：@RequestMapping("/hsBankBranch")；@PostMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsBankBranch_add、ride_hsBankBranch_del、ride_hsBankBranch_edit、ride_hsBankBranch_export、ride_hsBankBranch_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsBankCardBindingController.java

- 路由注解：@RequestMapping("/hsBankCardBinding")；@GetMapping("/page")；@PostMapping("/driverBankPage")；@PostMapping("/bindingBankCard")；@GetMapping("/setDefaultBank")；@GetMapping("/bankList")；@GetMapping("/ownerBankList")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsBankCardBinding_add、ride_hsBankCardBinding_del、ride_hsBankCardBinding_edit、ride_hsBankCardBinding_export、ride_hsBankCardBinding_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsBusinessDataScreenController.java

- 路由注解：@RequestMapping("/hsBusinessDataScreen")；@GetMapping("/getBusinessDataScreenRegion")；@GetMapping("/getMapInfo")；@GetMapping("/getBusinessStatistics")；@GetMapping("/getOrderTrend")；@GetMapping("/getComplaintDimension")；@GetMapping("/getOrderAmountTrendPoint")；@GetMapping("/getFeeSegmentItem")；@GetMapping("/getDriverGrabRank")；@GetMapping("/getRealTimeOrder")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsCommentController.java

- 路由注解：@RequestMapping("/hsComment")；@PostMapping({ "/admin/detail", "/admin/score-preview" })；@GetMapping("/default-config")；@PostMapping("/page")；@PostMapping("/handle")；@PostMapping("/export")；@GetMapping("/{id}")；@PostMapping("/add")；@PutMapping("/edit")；@DeleteMapping()；@PostMapping("/all/complaint")；@PostMapping("/complaint/handle")；@PostMapping("/detail")
- 权限标识：ride_hsComment_add、ride_hsComment_del、ride_hsComment_edit、ride_hsComment_export、ride_hsComment_handle、ride_hsComment_page、ride_hsComment_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsCommonAddressController.java

- 路由注解：@RequestMapping("/hsCommonAddress")；@GetMapping("/page")；@GetMapping("/page-list-by-user")；@GetMapping("/save-or-update-addr")；@PostMapping("/detail")；@PostMapping("/add")；@PutMapping("/update")；@PostMapping("/remove-by-ids")；@GetMapping("/export")
- 权限标识：ride_hsCommonAddress_add、ride_hsCommonAddress_edit、ride_hsCommonAddress_export、ride_hsCommonAddress_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsCompanyController.java

- 路由注解：@RequestMapping("/hsCompany")；@PostMapping("/page")；@PostMapping("/companyAreaList")；@PostMapping("/selectAll")；@PostMapping("/add")；@PutMapping("/edit")；@PutMapping("/detail")；@PutMapping("/enable")；@PostMapping("/delete")；@PostMapping("/export")；@GetMapping("/getCompanyByCompanyId")；@PostMapping("/bindBank")
- 权限标识：ride_hsCompany_add、ride_hsCompany_del、ride_hsCompany_detail、ride_hsCompany_edit、ride_hsCompany_enable、ride_hsCompany_export、ride_hsCompany_page

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsCompanyCustomerServicePhoneController.java

- 路由注解：@RequestMapping("/hsCompanyCustomerServicePhone")；@PostMapping("/page")；@PostMapping("/edit")；@PostMapping("/enable")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsCompanyInvoiceController.java

- 路由注解：@RequestMapping("/hsCompanyInvoice")；@PostMapping("/page")；@GetMapping("/detailById")；@GetMapping("/detailByCompanyId")；@PostMapping("/save")；@GetMapping("/queryCompanyInfo")；@PostMapping("/provinceList")；@PostMapping("/updateById")；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsCompanyInvoice_del、ride_hsCompanyInvoice_export

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsConfigChangeRecordController.java

- 路由注解：@RequestMapping("/hsConfigChangeRecord")；@PostMapping("/page")；@GetMapping("/getById")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDailyOrderStatsController.java

- 路由注解：@RequestMapping("/hsDailyOrderStats")；@GetMapping("/page")；@GetMapping("/calculateOrder")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：admin_hsDailyOrderStats_add、admin_hsDailyOrderStats_del、admin_hsDailyOrderStats_edit、admin_hsDailyOrderStats_export、admin_hsDailyOrderStats_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDeviceAreaRelationController.java

- 路由注解：@RequestMapping("/hsDeviceAreaRelation")；@GetMapping("/page")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDispatchRuleController.java

- 路由注解：@RequestMapping("/hsDispatchRule")；@PostMapping("/page")；@GetMapping("/detail")；@PostMapping("/add")；@PostMapping("/updateById")；@GetMapping("/updateStatus")；@PostMapping("/delete")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDriverBankCardBindingController.java

- 路由注解：@RequestMapping("/hsDriverBankCardBinding")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsDriverBankCardBinding_add、ride_hsDriverBankCardBinding_del、ride_hsDriverBankCardBinding_edit、ride_hsDriverBankCardBinding_export、ride_hsDriverBankCardBinding_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDriverCertificateApplicationController.java

- 路由注解：@RequestMapping("/hsDriverCertificateApplication")；@GetMapping("/current")；@PostMapping("/submit")；@PostMapping("/editRejected")；@PostMapping("/page")；@GetMapping("/detail")；@PostMapping("/audit")；@GetMapping("/auditRecords")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDriverController.java

- 路由注解：@RequestMapping("/hsDriver")；@GetMapping("/test")；@GetMapping("/pick-up")；@PostMapping("/arrivePickupPoint")；@PostMapping("/arriveWaypoint")；@PostMapping("/passengerArrivedPickupPoint")；@PostMapping("/page")；@PostMapping("/dispatch/status")；@PostMapping("/certificateExpire/page")；@PostMapping("/certificateExpire/update")；@GetMapping("/getDriverInfo")；@GetMapping("/info-by-user-id")；@PostMapping("/add")；@PostMapping("/enable")；@PostMapping("/getByVehicleId")；@PostMapping("/unbundleCar")；@PostMapping("/bundleCar")；@PostMapping("/getByCondition")；@PostMapping("/export")；@PostMapping("/importIdentityCard")；@PostMapping("/addDriver")；@PostMapping("/editDriver")；@PostMapping("/editById")；@PostMapping("/manager/all")；@PostMapping("/manager/del")；@GetMapping("/detailById")；@GetMapping("/manager/list")；@GetMapping("/current/info")；@GetMapping("/receiveOrderScope")；@PostMapping("/near-passenger")；@GetMapping("/driver-receive-type")；@GetMapping("/driver-voice-broadcast")；@PostMapping("/bindBank")；@PostMapping("/wxQrCode")；@PostMapping("/passenger-info/push")
- 权限标识：ride_hsDriver_add、ride_hsDriver_bundleCar、ride_hsDriver_enable、ride_hsDriver_export、ride_hsDriver_getByCondition、ride_hsDriver_getByVehicleId、ride_hsDriver_page、ride_hsDriver_unbundleCar

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDriverGrabOrderController.java

- 路由注解：@RequestMapping("/hsDriverOrder")；@PostMapping("/page")；@PostMapping("/grabOrder")；@PostMapping("/abandonBid")；@PostMapping("/grabList")；@PostMapping("/grabQueueList")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")；@GetMapping("/autoUpdateGrabOrderStatus")；@GetMapping("/owner-grab-order-history")；@GetMapping("/driver-location")
- 权限标识：admin_hsDriverOrder_add、admin_hsDriverOrder_del、admin_hsDriverOrder_edit、admin_hsDriverOrder_export、admin_hsDriverOrder_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDriverOnlineController.java

- 路由注解：@RequestMapping("/hsDriverOnline")；@GetMapping("/test")；@PostMapping("/test")；@PostMapping("/onlineOrOffline")；@GetMapping("/checkOnline")；@GetMapping("/find-fiveKM-car")
- 权限标识：admin_hsDriverOnline_del

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDriverOrderRankController.java

- 路由注解：@RequestMapping("/hsDriverOrderRank")；@GetMapping("/page")；@GetMapping("/rankList")；@GetMapping("/ownerRankDetail")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：admin_hsDriverOrderRank_add、admin_hsDriverOrderRank_del、admin_hsDriverOrderRank_edit、admin_hsDriverOrderRank_export、admin_hsDriverOrderRank_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsDriverPasswordResetRecordController.java

- 路由注解：@RequestMapping("/hsDriverPasswordResetRecord")；@PostMapping("/page")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsHeatmapConfigController.java

- 路由注解：@RequestMapping("/hsHeatmapConfig")；@GetMapping("/detail")；@GetMapping("/detailByAreaCode")；@PostMapping("/saveOrUpdate")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsHeatmapController.java

- 路由注解：@RequestMapping("/hsHeatmap")；@GetMapping("/points")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsInvoiceApplyController.java

- 路由注解：@RequestMapping("/hsInvoiceApply")；@PostMapping("/backPage")；@PostMapping("/waitOpenInvoicePage")；@PostMapping("/order-invoice-rel-page")；@PostMapping("/mini-order-invoice-rel-page")；@PostMapping("/platform-wait-open-page")；@PostMapping("/platform-issue-open-page")；@PostMapping("/platform-wait-open-commission")；@PostMapping("/page")；@GetMapping("/getById")；@PostMapping("/applyInvoice")；@GetMapping("/platformApplyInvoice")；@PostMapping("/applyInvoiceRed")；@PostMapping("/reOpenInvoice")；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsInvoiceApply_del、ride_hsInvoiceApply_edit、ride_hsInvoiceApply_export

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsInvoiceController.java

- 路由注解：@RequestMapping("/hsInvoice")；@PostMapping("/page")；@PostMapping("/list")；@GetMapping("/detail")；@PostMapping("/save")；@PostMapping("/update")；@GetMapping("/delete")；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsInvoice_del、ride_hsInvoice_export

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsInvoiceReopenAuditRecordController.java

- 路由注解：@RequestMapping("/hsInvoiceReopenAuditRecord")；@PostMapping("/page")；@PostMapping("/mini/page")；@GetMapping("/getById")；@PostMapping("/submit")；@PostMapping("/audit")；@GetMapping("/chain")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsLedgerController.java

- 路由注解：@RequestMapping("/hsLedger")；@PostMapping("/page")；@GetMapping("/{id}")；@GetMapping("/detail")；@PostMapping()；@PutMapping()；@DeleteMapping()；@PostMapping("/export")；@GetMapping("/processSplit")；@GetMapping("/ledger")；@GetMapping("/processSplitRefund")
- 权限标识：ride_hsLedger_add、ride_hsLedger_del、ride_hsLedger_edit、ride_hsLedger_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsLedgerDetailController.java

- 路由注解：@RequestMapping("/hsLedgerDetail")；@GetMapping("/page")；@GetMapping("/ledgerDetails")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsLedgerDetail_add、ride_hsLedgerDetail_del、ride_hsLedgerDetail_edit、ride_hsLedgerDetail_export、ride_hsLedgerDetail_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsNoticeController.java

- 路由注解：@RequestMapping("/hsNotice")；@PostMapping("/page")；@GetMapping("/{id}")；@PostMapping("/add")；@PostMapping("/edit")；@PostMapping("/delete")；@PostMapping("/mini/page")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOfflineNegotiationController.java

- 路由注解：@RequestMapping("/hsOrder/negotiation")；@PostMapping("/order/init")；@PostMapping("/order/cancel")；@PostMapping("/order/complete")；@PostMapping("/income-estimate")；@PostMapping("/dispute")；@PostMapping("/requote")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderCancelController.java

- 路由注解：@RequestMapping("/hsOrderCancel")；@GetMapping("/cancel-order")；@GetMapping("/reminderPay")；@GetMapping("/get-cancel-count")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：admin_hsOrderCancel_add、admin_hsOrderCancel_del、admin_hsOrderCancel_edit、admin_hsOrderCancel_export、admin_hsOrderCancel_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderCollectionSmsTaskController.java

- 路由注解：@RequestMapping("/hsOrderCollectionSmsTask")；@GetMapping("/processDueTasks")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderController.java

- 路由注解：@RequestMapping("/hsOrder")；@PostMapping("/page")；@GetMapping("/get-order-by-id")；@GetMapping("/wait-set-amount-order")；@GetMapping("/orderDetail")；@GetMapping("/nodeList")；@GetMapping("/bidDetail")；@PostMapping("/owner/wait-pay-order")；@PostMapping("/owner/passenger/wait-pay-order")；@PostMapping("/export")；@GetMapping("/owner/running")；@PostMapping("/owner/all")；@GetMapping("/pick/up")；@GetMapping("/complete")；@PostMapping("/set-order")；@PostMapping("/manager/all")；@GetMapping("/autoTimeoutCancelOrderTask")；@GetMapping("/autoRemoveRedisKeyTask")；@GetMapping("/pickupReminderOrRenewPush")；@GetMapping("/get-all-driver")；@GetMapping("/cancelOrder")；@PostMapping("/cancelExport")；@GetMapping("/truck-by-order-id")；@GetMapping("/operation-calculate")；@GetMapping("/test-truck")
- 权限标识：ride_hsOrder_export、ride_hsOrder_page

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderEarlyCompleteRecordController.java

- 路由注解：@RequestMapping("/hsOrderEarlyCompleteRecord")；@PostMapping("/page")；@PostMapping("/export")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderFeeController.java

- 路由注解：@RequestMapping("/hsOrderFee")；@GetMapping("/page")；@GetMapping("/orderFeeDetail")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsOrderFee_add、ride_hsOrderFee_del、ride_hsOrderFee_edit、ride_hsOrderFee_export、ride_hsOrderFee_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderInvoiceRelController.java

- 路由注解：@RequestMapping("/hsOrderInvoiceRel")；@GetMapping("/page")；@GetMapping("/backPage")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsOrderInvoiceRel_add、ride_hsOrderInvoiceRel_del、ride_hsOrderInvoiceRel_edit、ride_hsOrderInvoiceRel_export、ride_hsOrderInvoiceRel_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderPayController.java

- 路由注解：@RequestMapping("/orderPay")；@PostMapping("/miniPay")；@PostMapping("/miniRefund")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderSplitReconciliationAlertController.java

- 路由注解：@RequestMapping("/hsOrderSplitReconciliationAlert")；@PostMapping("/page")；@GetMapping("/reconciliation")；@GetMapping("/clear")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsOrderTransactionRecordController.java

- 路由注解：@RequestMapping("/hsOrderTransactionRecord")；@PostMapping("/page")；@PostMapping("/export")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsPassengerController.java

- 路由注解：@RequestMapping("/hsPassenger")；@PostMapping("/page")；@PostMapping("/get-user-passengers")；@PostMapping("/add-new-passengers")；@PostMapping("/delete-passengers")；@PostMapping("/update-passengers")；@PostMapping("/export")
- 权限标识：ride_hsPassenger_export、ride_hsPassenger_page

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsPassengerUserController.java

- 路由注解：@RequestMapping("/hsPassengerUser")；@PostMapping("/page")；@PostMapping("/enable")；@PostMapping("/export")
- 权限标识：ride_hsPassengerUser_enable、ride_hsPassengerUser_export、ride_hsPassengerUser_page

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsPaymentController.java

- 路由注解：@RequestMapping("/hsPayment")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsPayment_add、ride_hsPayment_del、ride_hsPayment_edit、ride_hsPayment_export、ride_hsPayment_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsPaymentRecordController.java

- 路由注解：@RequestMapping("/hsPaymentRecord")；@GetMapping("/page")；@GetMapping("/{id}")；@GetMapping("/cancel-payment")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsPaymentRecord_add、ride_hsPaymentRecord_del、ride_hsPaymentRecord_edit、ride_hsPaymentRecord_export、ride_hsPaymentRecord_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsProblemFeedbackController.java

- 路由注解：@RequestMapping("/hsProblemFeedback")；@GetMapping("/me/count")；@GetMapping("/me/page")；@PostMapping("/page")；@PostMapping("/handle")；@GetMapping("/{id}")；@PostMapping("/problem-feedback-passenger-add")；@PutMapping("/edit")；@DeleteMapping()；@GetMapping("/export")；@PostMapping("from/driver/add")
- 权限标识：ride_hsProblemFeedback_del、ride_hsProblemFeedback_edit、ride_hsProblemFeedback_export、ride_hsProblemFeedback_handle、ride_hsProblemFeedback_page

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsPushController.java

- 路由注解：@RequestMapping("/hsPush")；@GetMapping("/send-wx-message")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsReconciliationAlertController.java

- 路由注解：@RequestMapping("/hsReconciliationAlert")；@PostMapping("/page")；@GetMapping("/reconciliation")；@GetMapping("/clear")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsReconciliationAlert_add、ride_hsReconciliationAlert_del、ride_hsReconciliationAlert_edit、ride_hsReconciliationAlert_export、ride_hsReconciliationAlert_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsReconciliationFileController.java

- 路由注解：@RequestMapping("/hsEconciliationFile")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsEconciliationFile_add、ride_hsEconciliationFile_del、ride_hsEconciliationFile_edit、ride_hsEconciliationFile_export、ride_hsEconciliationFile_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsReconciliationRecordController.java

- 路由注解：@RequestMapping("/hsReconciliationRecords")；@PostMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@GetMapping("/download")；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsEconciliationRecords_add、ride_hsEconciliationRecords_del、ride_hsEconciliationRecords_edit、ride_hsEconciliationRecords_export、ride_hsEconciliationRecords_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsRefundRecordController.java

- 路由注解：@RequestMapping("/hsRefundRecord")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsRefundRecord_add、ride_hsRefundRecord_del、ride_hsRefundRecord_edit、ride_hsRefundRecord_export、ride_hsRefundRecord_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsRegionController.java

- 路由注解：@RequestMapping("/hsRegion")；@GetMapping("/page")；@GetMapping("/list")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsRegion_add、ride_hsRegion_del、ride_hsRegion_edit、ride_hsRegion_export、ride_hsRegion_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsServiceRulesController.java

- 路由注解：@RequestMapping("/hsServiceRules")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping("/add")；@PutMapping("/edit")；@DeleteMapping()；@PostMapping("/export")；@GetMapping("/get-phone")
- 权限标识：ride_hsServiceRules_add、ride_hsServiceRules_del、ride_hsServiceRules_edit、ride_hsServiceRules_export、ride_hsServiceRules_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsSplitReconciliationRecordController.java

- 路由注解：@RequestMapping("/hsSplitReconciliationRecords")；@PostMapping("/page")；@GetMapping("/download")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsUserBlacklistController.java

- 路由注解：@RequestMapping("/hsUserBlacklist")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping("get-black-list")；@GetMapping("delete")；@PostMapping("remove-black-list")；@PostMapping("driver/remove-black-list")；@PostMapping("passenger/remove-black-list")；@PostMapping()；@PutMapping()；@GetMapping("driver/get-black-list")；@GetMapping("passenger/get-black-list")；@GetMapping("/export")
- 权限标识：admin_hsUserBlacklist_add、admin_hsUserBlacklist_edit、admin_hsUserBlacklist_export、admin_hsUserBlacklist_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsVehicleController.java

- 路由注解：@RequestMapping("/hsVehicle")；@PostMapping("/page")；@PostMapping("/getByCompanyId")；@GetMapping("/{id}")；@PostMapping("/add")；@PutMapping("/edit")；@PutMapping("/enable")；@DeleteMapping()；@PostMapping("/export")；@PostMapping("/import")；@PostMapping("/select/page")；@PostMapping("/save")；@PostMapping("/manager/edit")；@PostMapping("/del")；@PostMapping("/locations")；@GetMapping("manager/list")
- 权限标识：ride_hsVehicle_add、ride_hsVehicle_del、ride_hsVehicle_edit、ride_hsVehicle_enable、ride_hsVehicle_export、ride_hsVehicle_getByCompanyId、ride_hsVehicle_page、ride_hsVehicle_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsVehicleDriverRelationController.java

- 路由注解：@RequestMapping("/hsVehicleDriverRelation")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsVehicleDriverRelation_add、ride_hsVehicleDriverRelation_del、ride_hsVehicleDriverRelation_edit、ride_hsVehicleDriverRelation_export、ride_hsVehicleDriverRelation_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsWithdrawalRecordController.java

- 路由注解：@RequestMapping("/hsWithdrawalRecord")；@GetMapping("/platformPage")；@PostMapping("/companyPage")；@PostMapping("/companyExport")；@PostMapping("/driverPage")；@PostMapping("/driverExport")；@GetMapping("/driverDetail")；@PostMapping("/applyWithdrawal")；@PostMapping("/auditWithdraw")；@GetMapping("/processing-withdrawal-record")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsWithdrawalRecord_add、ride_hsWithdrawalRecord_del、ride_hsWithdrawalRecord_edit、ride_hsWithdrawalRecord_export、ride_hsWithdrawalRecord_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/HsWsAckController.java

- 路由注解：@RequestMapping("/hsWsAck")；@GetMapping("/page")；@GetMapping("/ack-msg")；@GetMapping("/retry-send-msg")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsWsAck_add、ride_hsWsAck_del、ride_hsWsAck_edit、ride_hsWsAck_export、ride_hsWsAck_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/JobController.java

- 路由注解：@RequestMapping("/job")；@GetMapping("driver/car/unBind")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/MarketingAdminController.java

- 路由注解：@RequestMapping("/admin/marketing")；@PostMapping("/activity/rules")；@PostMapping("/activity/referral-rules")；@GetMapping("/activity/{activityId}/rules")；@GetMapping("/activity/{activityId}/referral-rules")；@GetMapping("/referral/records/page")；@GetMapping("/referral/activities")；@PostMapping("/activity/{activityId}/referral-rules/status")；@GetMapping("/points/rules")；@PostMapping("/points/rules/{ruleId}/status")；@GetMapping("/points/accounts/page")；@GetMapping("/points/ledger/page")；@GetMapping("/referral/records/export")；@GetMapping("/points/accounts/export")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/MarketingPointController.java

- 路由注解：@RequestMapping("/marketing/points")；@GetMapping("/me")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/MarketingReferralController.java

- 路由注解：@RequestMapping("/marketing/referral")；@GetMapping("/me")；@PostMapping("/bind")；@GetMapping("/records")；@GetMapping("/records/page")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/NlsController.java

- 路由注解：@RequestMapping("/nls")；@GetMapping("/getToken")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/ReceiveLinkController.java

- 路由注解：@RequestMapping("/receive")；@PostMapping("/route/plan")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/SweepPayAccountController.java

- 路由注解：@RequestMapping("/sweep")；@PostMapping("/accountOpen")；@GetMapping("/splitAccountSign")；@PostMapping("/accountIn")；@GetMapping("/queryAccount")；@PostMapping("/authCollectionNotify")；@PostMapping("/withdrawNotify")；@PostMapping("/fundCollectionNotify")；@PostMapping("/miniPayNotify")；@PostMapping("/balanceSplitNotify")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/VaaController.java

- 路由注解：@RequestMapping("/vaa")；@PostMapping("/voiceEvent")；@PostMapping(value = "/voiceFileEvent")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/YptController.java

- 路由注解：@RequestMapping("/tongTianXing/test")；@GetMapping("/getDeviceStatus")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/app/GuoJiaoCallAppController.java

- 路由注解：@RequestMapping("/guoJiaoCallApp")；@GetMapping("/phone")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/app/HsCompanyCustomerServicePhoneAppController.java

- 路由注解：@RequestMapping("/hsCompanyCustomerServicePhoneApp")；@GetMapping("/getPhone")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/app/HsOrderAppController.java

- 路由注解：@RequestMapping("/hsOrderApp")；@PostMapping("/page-order-list")；@GetMapping("/get-user-in-progress-order")；@PostMapping("/create-order")；@PostMapping("/create-orderV1")；@PostMapping("/driver-bid-list")；@PostMapping("/select-bid-driver")；@PostMapping("/cancel-timeout-bidding-order")；@PostMapping("/create-call-phone-order")；@PostMapping("/create-call-phone-location")；@PostMapping("/fill-in-order-destination")；@PostMapping("/order-remarks")；@PostMapping("/cancel-order")；@PostMapping("/cancel-go-order")；@PostMapping("/ask-to-continue-order")；@PostMapping("/order-evaluate")；@PostMapping("/order-complaint")；@GetMapping("/get-order-route-map")；@PostMapping("/plan-the-route")；@GetMapping("/get-order-cache-route")；@PostMapping("/get-route")；@PostMapping("/get-route-fee")；@GetMapping("/get-driver-location-by-id")；@GetMapping("/get-order-estimated-time")；@GetMapping("/get-historical-location-list")；@PostMapping("/del-historical-location")；@GetMapping("/del-all-historical-location")；@GetMapping("/expense-detail")；@GetMapping("/income-detail")；@PostMapping("/bid-income-estimate")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/app/HsPassengerFunctionAppController.java

- 路由注解：@RequestMapping("/hsPassengerFunctionApp")；@GetMapping("/enabled")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/app/SysUserAppController.java

- 路由注解：@RequestMapping("/user")；@PostMapping("/wx/quickLogin")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/fare/HsEmptyFeeController.java

- 路由注解：@RequestMapping("/hsEmptyFee")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsEmptyFee_add、ride_hsEmptyFee_del、ride_hsEmptyFee_edit、ride_hsEmptyFee_export、ride_hsEmptyFee_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/fare/HsFareRuleController.java

- 路由注解：@RequestMapping("/hsFareRule")；@GetMapping("/page")；@PostMapping("/list")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsFareRule_add、ride_hsFareRule_del、ride_hsFareRule_edit、ride_hsFareRule_export、ride_hsFareRule_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/fare/HsFareSettingController.java

- 路由注解：@RequestMapping("/hsFareSetting")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：admin_hsFareSetting_add、admin_hsFareSetting_del、admin_hsFareSetting_edit、admin_hsFareSetting_export、admin_hsFareSetting_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/fare/HsMileagePricingController.java

- 路由注解：@RequestMapping("/hsMileagePricing")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsMileagePricing_add、ride_hsMileagePricing_del、ride_hsMileagePricing_edit、ride_hsMileagePricing_export、ride_hsMileagePricing_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/fare/HsPrepaymentSettingController.java

- 路由注解：@RequestMapping("/hsPrepaymentSetting")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsPrepaymentSetting_add、ride_hsPrepaymentSetting_del、ride_hsPrepaymentSetting_edit、ride_hsPrepaymentSetting_export、ride_hsPrepaymentSetting_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/fare/HsRegionFareManagementController.java

- 路由注解：@RequestMapping("/hsRegionFareManagement")；@PostMapping("/page")；@GetMapping("/getById")；@GetMapping("/getByAreaCode")；@GetMapping("/getFareInfo")；@GetMapping("/offlineNegotiation/available")；@PostMapping("/save")；@PostMapping("/updateById")；@GetMapping("/updateStatus")；@GetMapping("/delete")；@DeleteMapping()；@GetMapping("/export")
- 权限标识：admin_hsRegionFareManagement_add、admin_hsRegionFareManagement_del、admin_hsRegionFareManagement_edit、admin_hsRegionFareManagement_export、admin_hsRegionFareManagement_view

## hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/fare/HsWaitingFareController.java

- 路由注解：@RequestMapping("/hsWaitingFare")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：ride_hsWaitingFare_add、ride_hsWaitingFare_del、ride_hsWaitingFare_edit、ride_hsWaitingFare_export、ride_hsWaitingFare_view

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/ConnectController.java

- 路由注解：@RequestMapping("/connect")；@PostMapping("/sync/ding/user")；@PostMapping("/sync/ding/dept")；@PostMapping("/sync/cp/user")；@PostMapping("/sync/cp/dept")
- 权限标识：sys_connect_sync

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysAreaController.java

- 路由注解：@RequestMapping("/sysArea")；@GetMapping("/page")；@GetMapping("/tree")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：sys_sysArea_add、sys_sysArea_del、sys_sysArea_edit、sys_sysArea_export、sys_sysArea_view

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysAuditLogController.java

- 路由注解：@RequestMapping("/audit")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@DeleteMapping("/delete")；@GetMapping("/export")
- 权限标识：sys_audit_del、sys_audit_export

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysClientController.java

- 路由注解：@RequestMapping("/client")；@GetMapping("/{clientId}")；@GetMapping("/page")；@PostMapping()；@DeleteMapping()；@PutMapping()；@GetMapping("/getClientDetailsById/{clientId}")；@GetMapping("/list")；@PutMapping("/sync")；@GetMapping("/export")
- 权限标识：sys_client_add、sys_client_del、sys_client_edit

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysDeptController.java

- 路由注解：@RequestMapping("/dept")；@GetMapping("/{id}")；@GetMapping("/list")；@GetMapping("/listDept")；@GetMapping("/dept-by-area")；@GetMapping("/business-data-screen-region")；@PostMapping("/area-fence")；@PostMapping("/areaList")；@PostMapping("/export")；@GetMapping("/areaSubList")；@GetMapping("/updateStatus")；@GetMapping("/listByParentId")；@GetMapping(value = "/tree")；@PostMapping()；@PostMapping("/add")；@GetMapping("/getDeptByHierarchy")；@DeleteMapping("/{id}")；@GetMapping("/delete")；@GetMapping("/getDeptById")；@PutMapping()；@PostMapping("/edit")；@GetMapping(value = "/getDescendantList/{deptId}")；@GetMapping(value = "/leader/{deptId}")；@GetMapping("/export")；@PostMapping("import")；@GetMapping("/org")；@GetMapping("/org/user/search")
- 权限标识：sys_dept_add、sys_dept_del、sys_dept_edit、sys_dept_getDeptByHierarchy

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysDictController.java

- 路由注解：@RequestMapping("/dict")；@GetMapping("/details/{id}")；@GetMapping("/details")；@GetMapping("/page")；@GetMapping("/type/{type}")；@PostMapping()；@DeleteMapping()；@PutMapping()；@GetMapping("/list")；@GetMapping("/item/page")；@GetMapping("/item/details/{id}")；@GetMapping("/item/details")；@PostMapping("/item")；@PutMapping("/item")；@DeleteMapping("/item/{id}")；@PutMapping("/sync")；@GetMapping("/export")
- 权限标识：sys_dict_add、sys_dict_del、sys_dict_edit

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysFileController.java

- 路由注解：@RequestMapping("/sys-file")；@GetMapping("/page")；@DeleteMapping()；@PutMapping("/rename")；@PostMapping(value = "/upload")；@PostMapping(value = "/upload/inner")；@GetMapping("/oss/file")；@GetMapping("/local/file/{fileName}")；@GetMapping("/group/list")；@PostMapping("/group/add")；@PutMapping("/group/update")；@DeleteMapping("/group/delete/{id}")；@PutMapping("/group/move")
- 权限标识：sys_file_del

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysI18nController.java

- 路由注解：@RequestMapping("/i18n")；@GetMapping("/page")；@GetMapping("/details/{id}")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")；@GetMapping("/info")；@PutMapping("/sync")
- 权限标识：sys_i18n_add、sys_i18n_del、sys_i18n_edit、sys_i18n_export、sys_i18n_view

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysLogController.java

- 路由注解：@RequestMapping("/log")；@GetMapping("/page")；@GetMapping("/sum")；@DeleteMapping()；@PostMapping("/save")；@PostMapping("/logs")；@GetMapping("/export")；@PostMapping("/save-client-log")
- 权限标识：sys_log_del、sys_log_export

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysMenuController.java

- 路由注解：@RequestMapping("/menu")；@GetMapping()；@GetMapping(value = "/tree")；@GetMapping("/tree/{roleId}")；@GetMapping("/details")；@PostMapping()；@DeleteMapping("/{id}")；@PutMapping()
- 权限标识：sys_menu_add、sys_menu_del、sys_menu_edit

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysMessageController.java

- 路由注解：@RequestMapping("/sysMessage")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")；@PostMapping("/send")；@PostMapping("/read")；@GetMapping("/user/page")；@GetMapping("/user/read/page")；@GetMapping("/send/smsCode")；@GetMapping("/send/smsCode/relPassword")；@PostMapping("/send/sms")；@PostMapping("/send/sms-bao")；@PostMapping("/send/email")；@PostMapping("/send/webhook")
- 权限标识：sys_message_add、sys_message_del、sys_message_edit、sys_message_export、sys_message_view

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysPostController.java

- 路由注解：@RequestMapping("/post")；@GetMapping("/list")；@GetMapping("/page")；@GetMapping("/details/{postId}")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")；@PostMapping("/import")
- 权限标识：sys_post_add、sys_post_del、sys_post_edit、sys_post_export、sys_post_view

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysPublicParamController.java

- 路由注解：@RequestMapping("/param")；@GetMapping("/publicValue/{publicKey}")；@GetMapping("/publicValues")；@GetMapping("/page")；@GetMapping("/details/{publicId}")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")；@PutMapping("/sync")
- 权限标识：sys_syspublicparam_add、sys_syspublicparam_del、sys_syspublicparam_edit

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysRegisterController.java

- 路由注解：@RequestMapping("/register")；@PostMapping("/user")；@PostMapping("/password")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysRoleController.java

- 路由注解：@RequestMapping("/role")；@GetMapping("/details/{id}")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/list")；@GetMapping("/page")；@PutMapping("/menu")；@PostMapping("/getRoleList")；@GetMapping("/export")；@PostMapping("/import")
- 权限标识：sys_role_add、sys_role_del、sys_role_edit、sys_role_export、sys_role_perm

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysRouteConfController.java

- 路由注解：@RequestMapping("/route")；@GetMapping()；@DeleteMapping("/{routeId}")；@PostMapping()；@PostMapping("/save")
- 权限标识：sys_route_manage

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysScheduleController.java

- 路由注解：@RequestMapping("/schedule")；@GetMapping("/page")；@GetMapping("/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")；@GetMapping("/list")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysSensitiveWordController.java

- 路由注解：@RequestMapping("/sysSensitiveWord")；@GetMapping("/page")；@GetMapping("/remote/list/{type}")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@PostMapping("/match")；@DeleteMapping()；@GetMapping("/export")；@GetMapping("/refresh")
- 权限标识：admin_sysSensitiveWord_add、admin_sysSensitiveWord_del、admin_sysSensitiveWord_edit、admin_sysSensitiveWord_export、admin_sysSensitiveWord_view

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysSocialDetailsController.java

- 路由注解：@RequestMapping("/social")；@GetMapping("/page")；@GetMapping("/getLoginAppList")；@GetMapping("/{type}")；@GetMapping("/getById/{id}")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/info/{inStr}")；@PostMapping("/bind")；@GetMapping("/export")
- 权限标识：sys_social_details_add、sys_social_details_del、sys_social_details_edit

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysSystemConfigController.java

- 路由注解：@RequestMapping("/system-config")；@GetMapping("/page")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/export")
- 权限标识：sys_message_add、sys_message_del、sys_message_edit、sys_message_export、sys_message_view

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysSystemInfoController.java

- 路由注解：@RequestMapping("/system")；@GetMapping("/cache")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysTenantController.java

- 路由注解：@RequestMapping("/tenant")；@GetMapping("/page")；@GetMapping("/details/{id}")；@GetMapping("/details")；@PostMapping()；@PutMapping()；@DeleteMapping()；@GetMapping("/list")；@GetMapping("/export")；@GetMapping(value = "/tree/menu")
- 权限标识：sys_systenant_add、sys_systenant_del、sys_systenant_edit、sys_systenant_export

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysTokenController.java

- 路由注解：@RequestMapping("/sys-token")；@RequestMapping("/page")；@DeleteMapping("/delete")
- 权限标识：sys_token_del

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysUserController.java

- 路由注解：@RequestMapping("/user")；@GetMapping("/info/{username}")；@PostMapping("/info")；@GetMapping(value = {"/info"})；@GetMapping("/details/{id}")；@GetMapping("/details")；@DeleteMapping()；@PostMapping("/del")；@PostMapping()；@GetMapping("/page")；@PutMapping()；@PostMapping("/editUser")；@PostMapping("/enableUser")；@PostMapping("/pageUser")；@PutMapping("/personal/edit")；@PostMapping("/applet/personal/edit")；@PostMapping("/applet/personal/currentInfo")；@PostMapping("/applet/personal/registration-district")；@PutMapping("/personal/password")；@GetMapping("/ancestor/{username}")；@GetMapping("/export")；@PostMapping("/import")；@PutMapping("/lock/{username}")；@PostMapping("/unbinding")；@PostMapping("/check")；@GetMapping("/getUserIdListByRoleIdList")；@GetMapping("/getUserIdListByDeptIdList")；@PostMapping("/getUserIdListByUserDTO")；@GetMapping("/getUserListByUserName")；@GetMapping("/list")；@GetMapping("/getById")
- 权限标识：sys_user_add、sys_user_del、sys_user_edit、sys_user_export

## hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/SysUserMiniController.java

- 路由注解：@RequestMapping("/patient")；@GetMapping("/loginByWx")；@GetMapping("/bindMobile")
- 权限标识：该文件未提取到显式权限注解；不能据此判定无鉴权
