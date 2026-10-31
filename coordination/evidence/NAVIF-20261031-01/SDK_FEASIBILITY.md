# NAVIF-20261031-01：Android 应用内导航 SDK 可行性

2026-10-31，frontend 独立探测；真实会话 `01a182c6-c475-7110-bbae-0deafe6d2385`。只读基线 `d5d878e2e56ca11fabc576545c46a277d8e7b985`，source `/Users/dongge/.codex/tmp/yijiexing-in-app-navigation-20261031`。**结论：固定依赖、核心接口及最小 Android 编译可行；真实导航能力未验收。** 未修改产品、未创建 APK、未启动 AVD、未安装、未访问凭据或云环境。

## 1. 精确依赖与实物验证

建议司机模块用以下依赖**替换**现有 `com.amap.api:3dmap-location-search:11.2.100_loc11.2.100_sea9.8.1`：

```kotlin
implementation("com.amap.api:navi-3dmap-location-search:11.2.100_3dmap11.2.100_loc11.2.100_sea9.8.1")
```

保留现有 `org.maplibre.gl:android-sdk:11.8.0`。不再另加 `3dmap`、旧 `3dmap-location-search`、`location`、`search` 或另一个 navi 合包，也不重复手动放入旧 JAR/SO。本组合保持 map/location 11.2.100、search 9.8.1，不顺带升级 11.3，不使用动态 latest。官方更新日志说明 10.0.700 起导航合包增加搜索并更名；旧导航自动部署页面的 `navi-3dmap` 示例为 2021 年资料，应结合新版记录核对。[导航更新日志](https://lbs.amap.com/api/android-navi-sdk/changelog)、[官方地图集成注意事项](https://lbs.amap.com/api/maps-sdk-for-android/guide/create-project/android-studio-create-project)。

实物来自发布者在 Maven Central 的[精确 POM](https://repo.maven.apache.org/maven2/com/amap/api/navi-3dmap-location-search/11.2.100_3dmap11.2.100_loc11.2.100_sea9.8.1/navi-3dmap-location-search-11.2.100_3dmap11.2.100_loc11.2.100_sea9.8.1.pom)及同目录 JAR，直连 curl 实际 exit0，无需代理。POM packaging=jar，无运行依赖声明。实际 JAR 100458122 bytes，SHA256 `2504d64ebf52ba4a9731bbf71996f6242fe07f6553fec92efe52f0c5ac7621c1`，ZIP 校验通过，3772 个 class。

旧地图合包和新导航合包有 **2045 个同名 class**，包含 `MapView`、`AMapLocationClient`、`ServiceSettings`。实际加入两个包后 `checkDebugDuplicateClasses` 按预期 exit1；正确替换组合 exit0。不能用 pickFirst 解决 Java 重复类。虽然版本号相同，合包内部 class 字节并不相同，不称整包二进制等价；实际产品全量编译仍需作者执行。

私有下载及 Maven 布局保存在 `/Users/dongge/.codex/tmp/yijiexing-navi-sdk-probe-20261031/`，可供后续离线构建使用 `maven/`；JAR、native 库及构建缓存未放入 Git 或本证据目录。

## 2. 核心接口：已对实际 11.2.100 JAR 运行 javap

以下为实物方法签名摘要，完整 10 类输出见同目录 `*.javap.txt`，并由 `NaviCompileProbe.java` 实际类型检查。官网参考可能随发布更新，因此本结论以指定 JAR 为准。[AMapNavi](https://a.amap.com/lbs/static/unzip/Android_Navi_Doc/com/amap/api/navi/AMapNavi.html)、[AMapNaviView](https://a.amap.com/lbs/static/unzip/Android_Navi_Doc/com/amap/api/navi/AMapNaviView.html)。

| 类 | 指定 JAR 确认的接口 | 接入含义 |
|---|---|---|
| AMapNavi | `static synchronized AMapNavi getInstance(Context) throws com.amap.api.maps.AMapException` | 导航单例；初始化异常需要真实处理 |
| AMapNavi | `boolean calculateDriveRoute(List<NaviLatLng> from, List<NaviLatLng> to, List<NaviLatLng> wayPoints, int strategy)` | 可显式传起点、终点、有序途经点；另有无起点重载 |
| AMapNavi | `addAMapNaviListener(AMapNaviListener)` / `removeAMapNaviListener(AMapNaviListener)` | 注册/解绑当前页面 owner 的监听 |
| AMapNavi | `boolean selectRouteId(int)` / `boolean startNavi(int)` | 选路线后启动；实时用 `NaviType.GPS`，不用模拟冒充 |
| AMapNavi | `setUseInnerVoice(boolean, boolean)` / `stopNavi()` / `static synchronized destroy()` | 内置播报配置、结束导航和销毁单例 |
| AMapNaviView | 构造 `AMapNaviView(Context)` / `AMapNaviView(Context, AMapNaviViewOptions)` | 自定义应用内 View，不必启动外部地图或导航组件 Activity |
| AMapNaviView | `onCreate(Bundle)`、`onResume()`、`onPause()`、`onSaveInstanceState(Bundle)`、`onDestroy()` | 与页面真实生命周期对应 |
| AMapNaviView | `getViewOptions()` / `setViewOptions(AMapNaviViewOptions)` / `boolean onBackPressed()` | 配置与返回处理 |
| AMapNaviViewOptions | `setLayoutVisible(boolean)`、`setSettingMenuEnabled(Boolean)`、`setRouteListButtonShow(boolean)`、`setPointToCenter(double,double)` | SDK UI 显隐、菜单、路线按钮及车标锚点 |
| AMapNaviListener | `onCalculateRouteSuccess(int[])` 与 `onCalculateRouteSuccess(AMapCalcRouteResult)`；对应两种 failure 回调 | 需统一一次处理，不能因双重回调重复启动 |
| AMapCalcRouteResult | `getRouteid()`、`getRouteRequestId()`、`getErrorCode()`、`getErrorDescription()` | 注意真实拼写 `getRouteid`；可辅助请求归属、失败提示 |
| AMapNaviListener | `onArrivedWayPoint(int)`、`onArriveDestination()`、`onLocationChange(AMapNaviLocation)`、`onNaviInfoUpdate(NaviInfo)` | 仅导航事件；不能直接视为业务到达/验尾号通过 |
| AMapNaviPath | `getAllLength()`、`getWayPoint()`、`getWayPointIndex()` | 可读取路线数据；不是改平台计价规则的授权 |

`calculateDriveRoute` 返回 true 只表示请求执行，不代表获得路线。必须等当前请求的成功回调，才可启动实时导航；失败可见、可重试，不用静态连线或外部导航伪装成功。官方说明网络算路以及最多 16 个 SDK 途经点（组件上限不同）；本项目仍只保留既有最多 2 个业务途经点。[驾车路线规划](https://lbs.amap.com/api/android-navi-sdk/guide/route-plan/drive-route-plan)、[实时导航](https://lbs.amap.com/api/android-navi-sdk/guide/navigation-map/gps-navi)。

## 3. 保留业务按钮的可行方式与生命周期

官方支持 `AMapNaviView` 及控件配置。[自定义 View](https://lbs.amap.com/api/android-navi-sdk/guide/custom-ui/customview)、[自定义 UI 控件](https://lbs.amap.com/api/android-navi-sdk/guide/custom-ui/custom-ui-elements)。实际 JAR 没有本次所查的 `setNaviFooter`；不要猜造 footer API。

建议 Compose 容器中上部承载 `AndroidView(AMapNaviView)`，下部保留原有业务操作区，或用 FrameLayout 为底栏预留空间。最小编译样例已实际构建导航 View + 预留底边距 + 独立按钮区域。不要直接盖住 SDK 原有转向提示/结束按钮；若选择 `setLayoutVisible(false)`，就须自行补齐导航指引，不能只剩一张地图。本次只证明容器/API可编译，未证明实际绘制、遮挡和触摸可用。

实现时需由唯一导航 owner 管理单例，使用订单 ID、业务阶段、目标/途经点版本及请求序列隔离异步结果；切单/退出后旧回调不能重启导航或修改新单。所有 UI 更新适当切回主线程。Compose 重组不能反复构造/启动；对页面进入、前后台、保存状态和退出建立成对调用。移除监听、按明确的退出策略 `stopNavi`，销毁 View；释放单例后不再调用旧实例。官方明确新版 View.onDestroy 不自动替应用执行 stopNavi。[View 生命周期参考](https://a.amap.com/lbs/static/unzip/Android_Navi_Doc/com/amap/api/navi/AMapNaviView.html)。

以上为后续实现建议，本轮未写入产品：SDK 到达、途经点通知与取消导航**不自动**触发订单推进/取消。接驾、尾号校验、距离与乘客同意、拨号确认、途经点完成和到达等，仍走原有业务事件及服务端门禁。去上车点和去下一有效途经点/终点应按当前阶段生成导航目标，不跨越未完成业务步骤。

## 4. 清单、隐私与权限要求

- Key 通过既有授权配置机制提供 `com.amap.api.v2.apikey`；本轮不读取/填入真实 Key，也未验证导航服务开通、配额或签名授权。使用定位 SDK 时需声明 `com.amap.api.location.APSService`。前台导航需网络、定位及相关网络状态权限并处理运行时授权。[高德清单配置](https://lbs.amap.com/api/android-navi-sdk/guide/create-project/configuration-considerations)。
- 实际 JAR 确认 `NaviSetting.updatePrivacyShow(Context,boolean,boolean)` 和 `updatePrivacyAgree(Context,boolean)`；初始化导航前按真实展示及同意状态调用，不硬编码虚构同意。地图/搜索初始化继续分别处理 `MapsInitializer` / `ServiceSettings` 的隐私接口；若自行实例化定位客户端也保持其前置要求。[NaviSetting](https://a.amap.com/lbs/static/unzip/Android_Navi_Doc/com/amap/api/navi/NaviSetting.html)、[地图初始化与隐私](https://lbs.amap.com/api/maps-sdk-for-android/guide/create-project/dev-attention)。
- 当前 target34 的可见 Activity 内导航与后台持续导航是不同权限/服务范围。若后续要在切后台/锁屏后持续运行，应明确实现并验证 location 前台服务、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_LOCATION` 和类型声明/运行时前置条件。不要照旧示例无条件新增后台定位、WRITE_SETTINGS 或其它无关权限；后台定位权限只按实际访问模式判断。[Android 定位访问说明](https://developer.android.com/develop/sensors-and-location/location/permissions)、[location 前台服务](https://developer.android.com/develop/background-work/services/fgs/service-types#location)。

本次最小 manifest 故意无 Key/服务/定位请求，只用于编译；不能称清单运行配置已验收。

## 5. 实际命令、结果与限制

| 探测 | 实际结果 |
|---|---|
| 直连下载 Maven metadata / 指定 POM / 指定 JAR，ZIP与SHA检查 | PASS，无代理；下载日志与 artifact-summary.json |
| OpenJDK17 `javap -public -classpath <指定jar>`，10 个导航类 | PASS，全部 exit0 |
| 私有最小工程 AGP8.5.2 / Gradle8.7 / JDK17 / compile34 min26 target34，固定坐标从私有Maven解析，保留 MapLibre11.8.0 及现有 Compose/lifecycle 基线 | PASS |
| `gradlew --offline --no-daemon :app:compileDebugJavaWithJavac :app:checkDebugDuplicateClasses :app:mergeDebugNativeLibs` | exit0，16秒，18 tasks；无 APK生成/安装/执行 |
| `gradlew --offline --no-daemon -PincludeLegacyMap :app:checkDebugDuplicateClasses` | 按预期 exit1，重复类反例已留日志 |

最初探针缺 AndroidX 开关、随后最小 MapLibre 图请求本机未缓存的旧 lifecycle 依赖；这两次属于探针配置失败，原日志保留。补齐 AndroidX 并使用现有应用的 Compose/lifecycle 版本图后，上述正例成功，不冒充首次构建即通过。探针和构建日志在本目录，执行工程及依赖仍在私有 probe 目录。

实际包只含 `armeabi-v7a`、`arm64-v8a` 原生导航库，无 x86/x86_64；与 MapLibre 的 native 合并成功。不要据有 v7a 目录承诺32位OS兼容：官方日志记载11.2.000不支持32位OS、11.3.100恢复，11.2.100真实设备边界需要后续确认，当前不为此扩大版本升级。[官方变更记录](https://lbs.amap.com/api/android-navi-sdk/changelog)。

**NOT_RUN：**完整司机产品构建；真实授权/服务开通/配额；GPS及定位精度；真实算路（包括起终相同且途经点不同的环线）；导航 View 渲染、触摸、横竖屏/前后台/锁屏恢复；内置语音/来电音频；真机/OEM；业务流程集成。可编译与16途经点接口不证明环线路由服务实际成功。真实失败应保留可见提示与重试，SDK到达不能替代业务验收。

结论供 root 安排 Claude 实现；无产品修改、无待合并产品 SHA、无部署。源码最后只读状态与 gate 见任务记录。

复现正例（全部在私有探针运行，不能指向产品目录）：

```sh
cd /Users/dongge/.codex/tmp/yijiexing-navi-sdk-probe-20261031/minimal
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew --offline --no-daemon :app:compileDebugJavaWithJavac :app:checkDebugDuplicateClasses :app:mergeDebugNativeLibs
```

精确 curl/Gradle argv 和环境也保存在 PROBE_RESULTS.json；反例仅加 `-PincludeLegacyMap` 并运行 `:app:checkDebugDuplicateClasses`，预期失败。
