'use strict';

const STORAGE_KEY = 'yijiexing.coupons.v1';

/**
 * 原团队优惠券模板定义（参考《宜捷行优惠券开发V-20260914.xlsx》）：
 * 1. 满减券门槛与减免金额精确到分
 * 2. 适用城市精确到区县（当阳市、宜昌市或全部）
 * 3. 适用订单类型（全部、普通叫车 STANDARD、预约用车 RESERVED 等），明确排斥司机竞价(BID)
 * 4. 有效期支持固定时间起止与天数
 */
const INITIAL_COUPONS = [
  {
    id: 'CPN_NEW10',
    title: '新用户立减券',
    type: 'AMOUNT',
    discountCents: 1000,
    minSpendCents: 2000,
    expiresAt: '2026-12-31',
    description: '满20元立减10元，打车通用（小费不计入满减）',
    status: 'AVAILABLE',
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL', 'STANDARD', 'RESERVED'],
    excludeBidding: true
  },
  {
    id: 'CPN_RIDE8',
    title: '出行8折畅行券',
    type: 'PERCENT',
    percent: 80,
    maxDiscountCents: 1500,
    minSpendCents: 1500,
    expiresAt: '2026-12-31',
    description: '享8折优惠，最高抵扣15元（限当阳市、宜昌市使用）',
    status: 'AVAILABLE',
    applicableCities: ['当阳市', '宜昌市'],
    applicableOrderTypes: ['ALL', 'STANDARD', 'RESERVED'],
    excludeBidding: true
  },
  {
    id: 'CPN_PEAK5',
    title: '早晚高峰减免券',
    type: 'AMOUNT',
    discountCents: 500,
    minSpendCents: 2500,
    expiresAt: '2026-12-31',
    description: '满25元减5元，高峰期出行专享',
    status: 'AVAILABLE',
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL', 'STANDARD'],
    excludeBidding: true
  },
  {
    id: 'CPN_INVITE20',
    title: '好友邀请专享券',
    type: 'AMOUNT',
    discountCents: 2000,
    minSpendCents: 4000,
    expiresAt: '2026-12-31',
    description: '满40元减20元，好友助力赠送',
    status: 'AVAILABLE',
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL', 'STANDARD', 'RESERVED'],
    excludeBidding: true
  }
];

function getStorage(wxPlatform) {
  const p = wxPlatform || (typeof wx !== 'undefined' ? wx : null);
  if (!p || !p.getStorageSync) return null;
  return p;
}

function loadCoupons(wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return JSON.parse(JSON.stringify(INITIAL_COUPONS));
  try {
    const data = p.getStorageSync(STORAGE_KEY);
    if (Array.isArray(data) && data.length > 0) return data;
  } catch (_) {}
  const initial = JSON.parse(JSON.stringify(INITIAL_COUPONS));
  try {
    p.setStorageSync(STORAGE_KEY, initial);
  } catch (_) {}
  return initial;
}

function saveCoupons(coupons, wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  try {
    p.setStorageSync(STORAGE_KEY, coupons);
  } catch (_) {}
}

/**
 * 校验券是否在当前上下文可用
 * @param {Object} coupon 券对象
 * @param {Object} context { baseFareCents, orderType, city, pricingMode, tipCents }
 */
function isCouponApplicable(coupon, context) {
  if (!coupon || coupon.status !== 'AVAILABLE') return false;

  const baseFare = typeof context === 'number' ? context : (context.baseFareCents || 0);
  const orderType = (context && context.orderType) || 'STANDARD';
  const city = (context && context.city) || '宜昌市';
  const pricingMode = (context && context.pricingMode) || 'ONLINE';

  // 竞价订单排除
  if (pricingMode === 'BID' || orderType === 'BID') return false;

  // 门槛判断（只看运费本金，不包含小费）
  if (baseFare < (coupon.minSpendCents || 0)) return false;

  // 城市校验
  if (Array.isArray(coupon.applicableCities) && !coupon.applicableCities.includes('ALL')) {
    if (!coupon.applicableCities.some(c => city.includes(c) || c.includes(city))) {
      return false;
    }
  }

  // 订单类型校验
  if (Array.isArray(coupon.applicableOrderTypes) && !coupon.applicableOrderTypes.includes('ALL')) {
    if (!coupon.applicableOrderTypes.includes(orderType)) {
      return false;
    }
  }

  return true;
}

/**
 * 计算优惠抵扣金额
 * 遵循原团队需求文档特殊场景第3条：“添加小费金额数不能影响优惠券满减”
 * @param {Object} coupon 券对象
 * @param {number|Object} fareOrOptions 运价分或选项上下文
 */
function calculateDiscount(coupon, fareOrOptions) {
  let baseFareCents = 0;
  let context = {};

  if (typeof fareOrOptions === 'number') {
    baseFareCents = fareOrOptions;
    context = { baseFareCents };
  } else if (fareOrOptions && typeof fareOrOptions === 'object') {
    baseFareCents = fareOrOptions.baseFareCents || 0;
    context = fareOrOptions;
  }

  if (!isCouponApplicable(coupon, context)) return 0;

  if (coupon.type === 'AMOUNT') {
    return Math.min(coupon.discountCents, baseFareCents);
  }
  if (coupon.type === 'PERCENT') {
    const rawDiscount = Math.round(baseFareCents * (100 - coupon.percent) / 100);
    return Math.min(rawDiscount, coupon.maxDiscountCents || rawDiscount, baseFareCents);
  }
  return 0;
}

/**
 * 寻找最优券
 */
function findBestCoupon(fareOrOptions, wxPlatform) {
  let baseFareCents = typeof fareOrOptions === 'number' ? fareOrOptions : (fareOrOptions && fareOrOptions.baseFareCents) || 0;
  if (!baseFareCents || baseFareCents <= 0) return null;

  const list = loadCoupons(wxPlatform).filter(c => c.status === 'AVAILABLE');
  let best = null;
  let maxDiscount = 0;
  for (const c of list) {
    const d = calculateDiscount(c, fareOrOptions);
    if (d > maxDiscount) {
      maxDiscount = d;
      best = { ...c, calculatedDiscount: d };
    }
  }
  return best;
}

/**
 * 下单时冻结优惠券（遵循特殊规则第5条：下单后进入冻结状态，不作真实核销）
 */
function freezeCoupon(couponId, orderId, wxPlatform) {
  const list = loadCoupons(wxPlatform);
  const target = list.find(c => c.id === couponId);
  if (target && target.status === 'AVAILABLE') {
    target.status = 'FROZEN';
    target.frozenOrderId = orderId;
    target.frozenAt = new Date().toISOString();
    saveCoupons(list, wxPlatform);
    return true;
  }
  return false;
}

/**
 * 退券逻辑（遵循特殊规则第4条：乘客取消、有责取消、后台有责/无责取消、超时取消均退券）
 */
function releaseCoupon(couponId, reason, wxPlatform) {
  const list = loadCoupons(wxPlatform);
  const target = list.find(c => c.id === couponId);
  if (target && (target.status === 'FROZEN' || target.status === 'USED')) {
    target.status = 'AVAILABLE';
    target.releasedReason = reason || 'ORDER_CANCELLED';
    target.releasedAt = new Date().toISOString();
    delete target.frozenOrderId;
    delete target.usedOrderId;
    saveCoupons(list, wxPlatform);
    return true;
  }
  return false;
}

/**
 * 订单完成并入账时真实核销优惠券
 */
function consumeCoupon(couponId, orderId, wxPlatform) {
  const list = loadCoupons(wxPlatform);
  const target = list.find(c => c.id === couponId);
  if (target) {
    target.status = 'CONSUMED';
    target.consumedAt = new Date().toISOString();
    target.consumedOrderId = orderId;
    saveCoupons(list, wxPlatform);
    return true;
  }
  return false;
}

// 保持向下兼容
function markCouponUsed(couponId, orderId, wxPlatform) {
  return consumeCoupon(couponId, orderId, wxPlatform);
}

/**
 * 领取新优惠券（支持兑换码或营销活动）
 */
function claimCoupon(couponCode, wxPlatform) {
  const list = loadCoupons(wxPlatform);
  const code = (couponCode || '').toUpperCase().trim();
  if (!code) return { success: false, message: '请输入兑换码' };

  if (list.some(c => c.id === code && c.status === 'AVAILABLE')) {
    return { success: false, message: '您已拥有该优惠券，不可重复领取' };
  }

  // 模拟兑换特定体验券
  const newCoupon = {
    id: 'CLAIM_' + code + '_' + Date.now().toString().slice(-4),
    title: '专属体验兑换券',
    type: 'AMOUNT',
    discountCents: 1500,
    minSpendCents: 3000,
    expiresAt: '2026-12-31',
    description: `兑换码[${code}]专享立减15元`,
    status: 'AVAILABLE',
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL', 'STANDARD', 'RESERVED']
  };

  list.unshift(newCoupon);
  saveCoupons(list, wxPlatform);
  return { success: true, message: '领取成功！已存入卡券中心', coupon: newCoupon };
}

module.exports = {
  INITIAL_COUPONS,
  loadCoupons,
  saveCoupons,
  isCouponApplicable,
  calculateDiscount,
  findBestCoupon,
  freezeCoupon,
  releaseCoupon,
  consumeCoupon,
  markCouponUsed,
  claimCoupon
};
