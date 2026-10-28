package cn.hbzjhs.passenger

/** UI choices only. Cancellation stage, refund and responsibility remain server decisions. */
internal object PassengerUiRules {
  val orderFilters = listOf("全部", "进行中", "已完成", "已取消")
  val cancellationReasons = listOf("行程计划有变", "等待时间较长", "上车地点有误", "重复叫车", "已选择其他出行方式", "其他")
  fun cancellationReason(choice: String, other: String): String? {
    if (choice !in cancellationReasons) return null
    val value = (if (choice == "其他") other else choice).trim()
    return value.takeIf { it.length in 2..200 }
  }
  fun matchesOrderFilter(stage: String, filter: String): Boolean = when(filter) {
    "进行中" -> stage !in setOf("DONE", "CANCELLED")
    "已完成" -> stage == "DONE"
    "已取消" -> stage == "CANCELLED"
    else -> true
  }
}
