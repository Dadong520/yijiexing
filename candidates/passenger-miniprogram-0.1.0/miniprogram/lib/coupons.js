'use strict';

const STORAGE_BASE_KEY = 'yijiexing.coupons.v1';

function getStorageKey(accountKey) {
  if (!accountKey || accountKey === 'anonymous') return STORAGE_BASE_KEY;
  return `${STORAGE_BASE_KEY}.${accountKey}`;
}

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

function loadCoupons(wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  const key = getStorageKey(accountKey);
  const today = new Date().toISOString().slice(0, 10);

  let list = null;
  if (p) {
    try {
      const data = p.getStorageSync(key);
      if (Array.isArray(data) && data.length > 0) {
        list = data;
      }
    } catch (_) {}
  }

  if (!list) {
    list = JSON.parse(JSON.stringify(INITIAL_COUPONS));
    if (p) {
      try { p.setStorageSync(key, list); } catch (_) {}
    }
  }

  // 严格过期核对：过期的可用券自动标记为已失效 EXPIRED
  let changed = false;
  for (const c of list) {
    if (c.status === 'AVAILABLE' && c.expiresAt && today > c.expiresAt) {
      c.status = 'EXPIRED';
      changed = true;
    }
  }
  if (changed && p) {
    try { p.setStorageSync(key, list); } catch (_) {}
  }

  return list;
}

function saveCoupons(coupons, wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  const key = getStorageKey(accountKey);
  try {
    p.setStorageSync(key, coupons);
  } catch (_) {}
}

/**
 * 校验券是否在当前上下文可用
 * @param {Object} coupon 券对象
 * @param {Object} context { baseFareCents, orderType, city, pricingMode, tipCents, currentDate }
 */
function isCouponApplicable(coupon, context) {
  if (!coupon || coupon.status !== 'AVAILABLE') return false;

  const baseFare = typeof context === 'number' ? context : (context.baseFareCents || 0);
  const orderType = (context && context.orderType) || 'STANDARD';
  const city = (context && context.city) || '宜昌市';
  const pricingMode = (context && context.pricingMode) || 'ONLINE';
  const today = (context && context.currentDate) || new Date().toISOString().slice(0, 10);

  // 校验有效期：过期直接不可用
  if (coupon.expiresAt && today > coupon.expiresAt) {
    return false;
  }

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

function findBestCoupon(fareOrOptions, wxPlatform, accountKey) {
  let baseFareCents = typeof fareOrOptions === 'number' ? fareOrOptions : (fareOrOptions && fareOrOptions.baseFareCents) || 0;
  if (!baseFareCents || baseFareCents <= 0) return null;

  const list = loadCoupons(wxPlatform, accountKey).filter(c => c.status === 'AVAILABLE');
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

function freezeCoupon(couponId, orderId, wxPlatform, accountKey) {
  if (!couponId || !orderId) return false;
  const list = loadCoupons(wxPlatform, accountKey);
  const target = list.find(c => c.id === couponId);
  if (target && target.status === 'AVAILABLE') {
    target.status = 'FROZEN';
    target.frozenOrderId = orderId;
    target.frozenAt = new Date().toISOString();
    saveCoupons(list, wxPlatform, accountKey);
    return true;
  }
  return false;
}

function releaseCoupon(couponId, reason, wxPlatform, accountKey) {
  if (!couponId) return false;
  const list = loadCoupons(wxPlatform, accountKey);
  const target = list.find(c => c.id === couponId);
  if (target && (target.status === 'FROZEN' || target.status === 'USED')) {
    target.status = 'AVAILABLE';
    target.releasedReason = reason || 'ORDER_CANCELLED';
    target.releasedAt = new Date().toISOString();
    delete target.frozenOrderId;
    delete target.usedOrderId;
    saveCoupons(list, wxPlatform, accountKey);
    return true;
  }
  return false;
}

function consumeCoupon(couponId, orderId, wxPlatform, accountKey) {
  if (!couponId) return false;
  const list = loadCoupons(wxPlatform, accountKey);
  const target = list.find(c => c.id === couponId);
  if (target) {
    target.status = 'CONSUMED';
    target.consumedAt = new Date().toISOString();
    target.consumedOrderId = orderId;
    saveCoupons(list, wxPlatform, accountKey);
    return true;
  }
  return false;
}

function markCouponUsed(couponId, orderId, wxPlatform, accountKey) {
  return consumeCoupon(couponId, orderId, wxPlatform, accountKey);
}

function claimCoupon(couponCode, wxPlatform, accountKey) {
  const list = loadCoupons(wxPlatform, accountKey);
  const code = (couponCode || '').toUpperCase().trim();
  if (!code) return { success: false, message: '请输入兑换码' };

  if (list.some(c => c.id === code && c.status === 'AVAILABLE')) {
    return { success: false, message: '您已拥有该优惠券，不可重复领取' };
  }

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
  saveCoupons(list, wxPlatform, accountKey);
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
