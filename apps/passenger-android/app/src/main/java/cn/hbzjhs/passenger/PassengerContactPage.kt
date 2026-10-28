package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONObject

@Composable
fun PassengerContactPage(order: JSONObject, onBack: () -> Unit, onComplaint: (() -> Unit)? = null) {
  PassengerFullPage("联系与帮助", onBack) {
    val driver=order.optJSONObject("driver")
    Panel("本次行程司机") {
      if(driver==null) Note("司机尚未接单，暂未提供司机联系信息。")
      else {
        Text(driver.text("name","司机资料待同步"),fontWeight=FontWeight.Bold)
        KV("车牌",driver.text("plate","未提供"))
        KV("脱敏号码",driver.text("mobileMasked","未提供"))
      }
      Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()) { Text("司机电话暂不可用") }
      Note("平台尚未接入司机电话中继，脱敏号码不可拨打。这里不提供文字聊天，也不会显示已连接或已送达。")
    }
    PassengerCompanyPhone(order.optJSONObject("company"))
    Panel("行程信息与反馈") {
      Text("${order.text("origin")} → ${order.text("destination")}")
      IdentifierButton(order.text("id"))
      if(onComplaint!=null) ActionButton("反馈本次行程问题",onClick=onComplaint)
      else Note("当前行程的接驾与服务状态会自动同步；结束后可在订单内提交投诉，后台回复会保存到记录。")
    }
  }
}
