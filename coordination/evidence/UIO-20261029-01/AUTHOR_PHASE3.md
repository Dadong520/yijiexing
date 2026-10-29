I fixed both review items in source, but nothing has been compiled or run, including the new test.

**UIOB-01 (`kmOrMissing`)**
- A plain number that is zero or positive gets " km" added: `"0"` shows "0 km" and `"12.5"` shows "12.5 km".
- If the value already ends in km (any case) or 公里, the unit is not repeated: `"3.2km"` and `"8公里"` become "3.2 km" and "8 km".
- Anything containing "待确认" shows "待确认" with no km.
- Everything else shows "未提供": empty, null, "—", "-", NaN, Infinity, negative numbers, exponent forms like "1e3", and other text.
- The parameter now also accepts null; existing calls are unchanged.

**UIOB-02 (new-order dialog note)**
- When the order is found, the dialog now always shows a source line.
  - Private demo orders: "演示订单 · 未关联乘客 App · 金额为模拟".
  - All other (shared sandbox) orders: "共享沙箱订单 · 距乘客为服务端沙箱估算，非实时测距 · 金额为模拟".
- If the order isn't in the current list, all three fields already show "未提供" and no note appears.
- How alerts are triggered and de-duplicated, and the data, Domain and Store, are unchanged.

**Files changed**
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/UIOComponents.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/NewOrderAlerts.kt`
- `candidates/driver-0.3.6-r2/android/app/src/test/java/cn/hbzjhs/driver/preview/UIODistancePresentationTest.kt` (new, plain JUnit4)

The test covers empty, null and placeholder values, "待确认", NaN, Infinity and negatives, other non-numeric text, 0, decimals, values that already have a unit, and that the private and shared notes differ.

**NOT_RUN:** compile, the new and existing unit tests, offline build, AVD.
