package cn.hbzjhs.driver.preview

import org.json.JSONObject

internal fun driverReferralRuleText(rule: JSONObject?): String {
    if (rule == null) return "拉新规则待同步 · 单账号上限100人 · 奖励为模拟积分"
    val tiers = rule.bList("tiers").joinToString("；") { "${it.optInt("from")}人：${it.optInt("points")}分" }
    val bonuses = rule.bList("bonuses").joinToString("；") { "累计${it.optInt("from", it.optInt("count"))}人加${it.optInt("points")}分" }
    return "${if(rule.optBoolean("enabled")) "" else "活动暂停 · "}${if(rule.optString("trigger") == "REGISTER") "注册达标" else "首单达标"} · ${if(rule.optBoolean("tiered")) "阶梯积分 $tiers" else "固定${rule.optInt("points")}积分"} · ${if(rule.optBoolean("cumulative")) "累计奖励" else "非累计奖励"} · $bonuses · 单账号上限100人，奖励为模拟积分"
}
