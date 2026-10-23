'use strict';
const STAGES={AVAILABLE:'等待司机接单',BID_SENT:'司机已报价',PREPAY:'待确认并模拟支付',RESERVED:'预约已确认',PICKUP:'司机前往接驾',ARRIVED:'司机已到达',TRIP:'行程中',WAIT_PAY:'待支付行程欠费',WAIT_CONTINUE:'待确认是否继续叫车',DONE:'行程已完成',CANCELLED:'订单已取消'};
function cents(value) {if (!Number.isSafeInteger(value)||value<0) throw new Error('金额须为非负整数分');return value;}
function money(value) {return (cents(value)/100).toFixed(2);}
function parseMoney(value) {if(typeof value!=='string'||!/^\d+(?:\.\d{1,2})?$/.test(value.trim())) throw new Error('请输入最多两位小数的金额');const [a,b='']=value.trim().split('.');return cents(Number(a)*100+Number(b.padEnd(2,'0')));}
const PAYMENT_STATUSES=Object.freeze({UNPAID:'尚未支付',SUCCESS:'模拟支付成功',FAILED:'模拟支付失败',PROCESSING:'模拟支付处理中',CANCELLED:'已取消模拟支付',REFUNDED:'已模拟退款'});
const FARE_SOURCES=Object.freeze({PENDING_DESTINATION:'待补充目的地后确定',DEMO_ESTIMATE:'独立体验估价',DRIVER_NEGOTIATED:'司机议价报价',ADMIN_SIMULATED_ADJUSTMENT:'后台调整后的体验费用'});
const NEGOTIATION_STATUSES=Object.freeze({OFFERED:'等待确认报价',DISPUTED:'已提出价格异议',ACCEPTED:'已接受报价'});
const COMPLAINT_STATUSES=Object.freeze({PENDING:'等待处理',REPLIED:'已回复'});
const KINDS=Object.freeze({STANDARD:'即时用车',RESERVED:'预约用车',BID:'司机竞价',NEGOTIATED:'线下议价',PHONE:'电话叫车'});
const ACTIONS=Object.freeze({'order.create':'创建行程','order.selectDriver':'选择司机','order.pay':'模拟支付','order.cancel':'取消行程','order.update':'更新行程','order.continue':'确认是否继续叫车','order.evaluate':'提交评价','order.complaint':'提交投诉','order.remark':'更新备注','order.complaint.attach':'上传投诉图片','order.blockDriver':'屏蔽司机','order.unblockDriver':'解除司机屏蔽','negotiation.claim':'认领议价订单','negotiation.dispute':'提交价格异议','profile.update':'更新个人资料','favorite.save':'保存常用地址','favorite.remove':'删除常用地址','feedback.submit':'提交意见反馈','rider.save':'保存乘车人','rider.remove':'删除乘车人','invoice.title.save':'保存发票抬头','invoice.title.remove':'删除发票抬头','invoice.apply':'申请模拟开票回执'});
const EVENTS=Object.freeze({PASSENGER_CREATED:'乘客已提交叫车请求',PASSENGER_SELECTDRIVER:'乘客已选择司机',PASSENGER_PAY:'模拟支付结果已更新',PASSENGER_CANCEL:'乘客已取消行程',PASSENGER_UPDATE:'乘客已更新行程信息',PASSENGER_CONTINUE:'乘客已确认后续叫车安排',PASSENGER_EVALUATE:'乘客已提交评价',PASSENGER_COMPLAINT:'乘客已提交投诉',PASSENGER_REMARK_UPDATED:'乘客已更新备注',PASSENGER_COMPLAINT_ATTACHMENT:'乘客已添加投诉图片',PASSENGER_NEGOTIATION_CLAIMED:'乘客已认领议价订单',PASSENGER_PRICE_DISPUTE:'乘客已提出价格异议',DRIVER_ACCEPT:'司机已接单',DRIVER_BID:'司机已提交报价',DRIVER_ADVANCE:'司机已更新行程进度',DRIVER_WAYPOINT:'司机已确认到达途经点',DRIVER_CANCEL:'司机已取消参与本次行程',DRIVER_COMPLAINT:'司机已提交行程问题',DRIVER_REQUOTE:'司机已更新议价报价',BIDDING_TIMEOUT_CANCELLED:'竞价等待超时，订单已取消',ADMIN_CANCEL:'后台已取消行程',ADMIN_ADJUSTFARE:'后台已调整体验费用',ADMIN_REFUND:'后台已处理模拟退款',ADMIN_COMPLAINT_REPLIED:'后台已回复行程投诉'});
// Own-key lookup ensures unexpected enum values never leak internal codes or prototypes.
const label=(map,value,fallback)=>typeof value==='string'&&Object.prototype.hasOwnProperty.call(map,value)?map[value]:fallback;
const paymentStatusLabel=value=>label(PAYMENT_STATUSES,value,'支付状态待刷新');
const fareSourceLabel=value=>label(FARE_SOURCES,value,'费用来源待确认');
const negotiationStatusLabel=value=>label(NEGOTIATION_STATUSES,value,'议价状态待刷新');
const complaintStatusLabel=value=>label(COMPLAINT_STATUSES,value,'处理状态待刷新');
const kindLabel=value=>label(KINDS,value,'用车类型待确认');
const actionLabel=value=>label(ACTIONS,value,'确认操作结果');
const eventLabel=value=>label(EVENTS,value,'订单记录已更新');
const pendingStatusLabel=value=>label({pending:'正在确认结果',uncertain:'结果未确认，请使用原请求重试'},value,'操作结果待确认');
// Backend timestamps carry a timezone; display consistently in UTC+8 on all devices.
function shortTime(value) {
 if(value===null||value===undefined||value==='')return '时间未记录';
 if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value))return '时间无效';
 const y=Number(value.slice(0,4)),month=Number(value.slice(5,7)),day=Number(value.slice(8,10));
 if(month<1||month>12||day<1||day>new Date(Date.UTC(y,month,0)).getUTCDate())return '时间无效';
 const ms=Date.parse(value);if(!Number.isFinite(ms))return '时间无效';
 const date=new Date(ms+8*60*60*1000),pad=n=>String(n).padStart(2,'0');
 return pad(date.getUTCMonth()+1)+'-'+pad(date.getUTCDate())+' '+pad(date.getUTCHours())+':'+pad(date.getUTCMinutes());
}
module.exports={paymentStatusLabel,fareSourceLabel,negotiationStatusLabel,complaintStatusLabel,eventLabel,shortTime,kindLabel,actionLabel,pendingStatusLabel,PAYMENT_STATUSES,FARE_SOURCES,NEGOTIATION_STATUSES,COMPLAINT_STATUSES,EVENTS,KINDS,cents,money,parseMoney,stageLabel:stage=>label(STAGES,stage,'状态待刷新'),STAGES,experienceNotice:'独立体验环境；验证码、支付、退款为模拟，非正式微信登录。',storageNotice:'本地 storage 仅用于会话和重试恢复，不等于硬件加密存储。'};
