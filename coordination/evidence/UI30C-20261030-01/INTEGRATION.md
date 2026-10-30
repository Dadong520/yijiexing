# UI30C-20261030-01 作者整合说明（claude）
session 93f99706-9822-4a37-b5a9-5369a6ac2364（取自task）；claude-opus-5-5。工作树 /Users/dongge/.codex/tmp/yijiexing-ui-feedback-20261030，branch codex/ui-feedback-20261030，base 362c7f8 + 9495caa cherry-pick --no-commit。作者未commit/add。2026-10-30。

## 8个冲突文件的处理（乘客端）
- ParityScreens / ExtraScreens×2 / ProfileScreens.DeleteDialog / OrderIdentity：用云端PassengerDialog/PassengerTextButton，保留main的PassengerModalSlot（PassengerDialog本身不注册slot）。
- OperationsSupport：拨号确认保留PassengerModalSlot(confirm)；公告/公告失败对话框改用PassengerDialog但保留`!hidden`（RANK_ANNOUNCEMENT被页面/确认弹窗压住时不绘制）。
- OrderDetail：保留云端底部固定支付/取消栏；main的PassengerModalSlot(dialog/choose)移到栏后，所有对话框之前。
- ProfileScreens：版本行用云端格式，并恢复main NAV阶段4的真实限制文案（定位授权/搜索逆地理依赖服务/外部导航/短信中继与人工客服未接入）；常用地址新增AddressDialog选点，slot在编辑与选点两态都持有。
- PassengerConfirmPrompts：保留main全部逻辑（按账号key隔离、rememberSaveable(account)、consent每秒过期、点按时rejectReason复核、仅成功回调settle、失败/同步拒绝显示原因可重试、RANK_PROMPT slot与othersOpen让位）。云端UI改为PassengerModal(holdPageSlot=false)。云端欠费提示移植为`arrearsPrompt`，键为key(account, unpaidId, "arrears:"+blockedId)，**追加在列表末尾**，不抢占订单自身问题/距离确认；公告/权限照旧通过slot让位。云端"关闭一条同类全部settle"未移植（与main"只settle一条，其它订单问题保持"规则冲突）。
- BookingScreens：保留PassengerAddressLookup.debounced（gate/400ms/stillWanted/同query重试）；只采纳云端空结果文案。云端已删手动坐标入口，相应删去"手动地点"提示；重试按钮改PassengerTextButton。

## 非冲突文件最小修复
- UiComponents.PassengerModal：新增`holdPageSlot: Boolean = true`；仅确认弹窗传false，避免自身rank0 slot把rank2弹窗压住引发闪烁循环。PassengerAlert的位置参数调用不受影响。
- 司机OperationsSupport.DriverQualificationBanner：pending替换中的证件若validity为EXPIRED/NOT_EFFECTIVE，横幅仍显示"不在有效期、新材料审核中，暂停新增接单"（之前被过滤掉）。
- 司机BusinessScreens证件页pending提示：当前证过期/未生效时不再显示"旧证仍在有效期内可继续接单"。

## 已知缺口
- 司机端无modal coordinator，DriverDialog统一后的互斥未逐个核查；司机欠费/GPS手动保护/导航阶段dedupe在自动合并文件中未逐行复核（无driver三方参考副本）。
- 乘客其余自动合并文件只抽查了PassengerDialog/PassengerModal调用点与slot；未逐行比对24项全部UI改动。
- WAIT_PAY订单自身"去支付"提示"稍后"后，欠费提示可能随即再弹一次（不同问题键）。
- 确认弹窗提交中"稍后"按钮不置灰（点击无效）。

## 测试
编译、单测（含PassengerConfirmPromptRulesTest/PassengerPickupDefaultsTest）、GUI全部NOT_RUN（作者无Bash）。由root前置检查与独立复核。

## 阶段2（正式任务UITC-20261030-01；UI30C为初始别名，证据仍在本目录）
同session 93f99706-9822-4a37-b5a9-5369a6ac2364。未触碰root的Gradle版本常量。依据backend-findings.json。
- UITB-01闭合（司机OperationsSupport.kt）：新增`certificateInReview`（pending或status PENDING）、`certificateCurrentlyValid`（status APPROVED且validity∈VALID/EXPIRING/LONG_TERM）、`certificateOutOfDate`（EXPIRED/NOT_EFFECTIVE）。横幅：仅当前材料有效时审核中才抑制常规催办；审核中且当前材料非有效的证件保留显示——过期/未生效写"不在有效期、审核中，暂停新增接单"（沿用BIZ08既有规则），UNKNOWN/非APPROVED写"当前材料有效期未确认或未通过、审核中，请以审核结果为准"，不宣称后端必禁接，也不宣称有效。未审核中的催办（reminder||UNKNOWN）不变。
- DriverCertificateStatus：LONG_TERM但非APPROVED不再说"长期有效"；REJECTED提示仅在当前材料有效时说"当前有效材料保留"。
- 司机BusinessScreens证件页pending提示三分支：当前有效→旧证可继续接单；过期/未生效→审核通过前仍暂停新增接单；其他（UNKNOWN/未通过）→不能视为有效证件，以审核结果为准。
- 欠费提示（乘客PassengerConfirmPrompts）：键改为key(account, unpaidId, "pay")，与WAIT_PAY原提示同一身份；构建列表时原地替换同key文案（位置不变，不前移，距离/其他问题顺序保持），无同key时追加末尾。"稍后"一次即settle该账单。只打开订单页，不自动支付。代价：此前已对该账单"稍后"的，出现被阻塞单后不再重弹欠费提示（订单页仍可见）。
- PassengerModal新增`dismissEnabled`/`extraEnabled`（默认true，其它调用不变）；确认弹窗提交中"稍后"与"拒绝"显示禁用（拒绝在busy时也禁用），与回调守卫一致。
- slot定向核对：乘客全部Dialog调用点——OrderDetail（block/choose/pay/destination/remark/dispute由dialog/choose slot覆盖，PassengerModal自带）、ExtraScreens×3、ProfileScreens（logout/常用地址/DeleteDialog）、ParityScreens、OrderIdentity、OperationsSupport拨号、MainActivity SettingsDialog（PassengerModalSlot(settings)）、PassengerTripDistance、PassengerPermissions、PassengerFullPage均有PAGE slot；公告（RANK_ANNOUNCEMENT+PassengerDialog）与确认弹窗（RANK_PROMPT+holdPageSlot=false）不自加PAGE，无自锁。BookingScreens原生Date/TimePickerDialog沿用main（未加slot，非本轮引入）。未发现需新增slot处。
- 剩余缺口：司机端无modal coordinator，DriverDialog互斥及司机欠费/GPS/导航自动合并未逐行复核；云端"同类一并settle"未移植。
- 测试：编译/单测/GUI全部NOT_RUN。
