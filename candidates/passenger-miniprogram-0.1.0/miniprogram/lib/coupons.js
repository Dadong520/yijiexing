'use strict';

const STORAGE_KEY = 'yijiexing.coupons.v1';

const INITIAL_COUPONS = [
  {
    id: 'CPN_NEW10',
    title: '新用户立减券',
    type: 'AMOUNT',
    discountCents: 1000,
    minSpendCents: 2000,
    expiresAt: '2026-12-31',
    description: '满20元立减10元，打车通用',
    status: 'AVAILABLE'
  },
  {
    id: 'CPN_RIDE8',
    title: '出行8折畅行券',
    type: 'PERCENT',
    percent: 80,
    maxDiscountCents: 1500,
    minSpendCents: 1500,
    expiresAt: '2026-12-31',
    description: '享8折优惠，最高抵扣15元',
    status: 'AVAILABLE'
  },
  {
    id: 'CPN_PEAK5',
    title: '早晚高峰减免券',
    type: 'AMOUNT',
    discountCents: 500,
    minSpendCents: 2500,
    expiresAt: '2026-12-31',
    description: '满25元减5元，高峰期出行专享',
    status: 'AVAILABLE'
  },
  {
    id: 'CPN_INVITE20',
    title: '好友邀请专享券',
    type: 'AMOUNT',
    discountCents: 2000,
    minSpendCents: 4000,
    expiresAt: '2026-12-31',
    description: '满40元减20元，好友助力赠送',
    status: 'AVAILABLE'
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

function calculateDiscount(coupon, fareCents) {
  if (!coupon || coupon.status !== 'AVAILABLE') return 0;
  if (fareCents < (coupon.minSpendCents || 0)) return 0;
  if (coupon.type === 'AMOUNT') {
    return Math.min(coupon.discountCents, fareCents);
  }
  if (coupon.type === 'PERCENT') {
    const rawDiscount = Math.round(fareCents * (100 - coupon.percent) / 100);
    return Math.min(rawDiscount, coupon.maxDiscountCents || rawDiscount, fareCents);
  }
  return 0;
}

function findBestCoupon(fareCents, wxPlatform) {
  if (!fareCents || fareCents <= 0) return null;
  const list = loadCoupons(wxPlatform).filter(c => c.status === 'AVAILABLE');
  let best = null;
  let maxDiscount = 0;
  for (const c of list) {
    const d = calculateDiscount(c, fareCents);
    if (d > maxDiscount) {
      maxDiscount = d;
      best = { ...c, calculatedDiscount: d };
    }
  }
  return best;
}

function markCouponUsed(couponId, orderId, wxPlatform) {
  const list = loadCoupons(wxPlatform);
  const target = list.find(c => c.id === couponId);
  if (target) {
    target.status = 'USED';
    target.usedAt = new Date().toISOString();
    target.usedOrderId = orderId;
    saveCoupons(list, wxPlatform);
  }
}

module.exports = {
  INITIAL_COUPONS,
  loadCoupons,
  saveCoupons,
  calculateDiscount,
  findBestCoupon,
  markCouponUsed
};
