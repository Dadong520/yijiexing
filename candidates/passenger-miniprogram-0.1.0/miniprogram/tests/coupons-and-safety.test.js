'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const coupons = require('../lib/coupons');
const safety = require('../lib/safety');
const extras = require('../lib/extras');

test('coupons: calculates amount and percent discounts correctly', () => {
  const couponAmount = {
    id: 'T1',
    status: 'AVAILABLE',
    type: 'AMOUNT',
    discountCents: 1000,
    minSpendCents: 2000,
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL']
  };
  assert.equal(coupons.calculateDiscount(couponAmount, 1500), 0);
  assert.equal(coupons.calculateDiscount(couponAmount, 2500), 1000);

  const couponPercent = {
    id: 'T2',
    status: 'AVAILABLE',
    type: 'PERCENT',
    percent: 80,
    maxDiscountCents: 1500,
    minSpendCents: 1500,
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL']
  };
  // 2000分 * 20% = 400分
  assert.equal(coupons.calculateDiscount(couponPercent, 2000), 400);
  // 10000分 * 20% = 2000分，但封顶1500分
  assert.equal(coupons.calculateDiscount(couponPercent, 10000), 1500);
});

test('coupons: rule #3 tips cannot contribute to min spend threshold', () => {
  const couponAmount = {
    id: 'T_RULE3',
    status: 'AVAILABLE',
    type: 'AMOUNT',
    discountCents: 1000,
    minSpendCents: 3000, // 门槛30元
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL']
  };
  // 运费本金25元 + 小费10元 = 35元，但门槛判断只看运费本金25元，因此不可抵扣
  const discountWithTip = coupons.calculateDiscount(couponAmount, {
    baseFareCents: 2500,
    tipCents: 1000,
    city: '宜昌市',
    orderType: 'STANDARD',
    pricingMode: 'ONLINE'
  });
  assert.equal(discountWithTip, 0);

  // 运费本金35元时达到门槛，可以抵扣10元
  const discountEligible = coupons.calculateDiscount(couponAmount, {
    baseFareCents: 3500,
    tipCents: 500,
    city: '宜昌市',
    orderType: 'STANDARD',
    pricingMode: 'ONLINE'
  });
  assert.equal(discountEligible, 1000);
});

test('coupons: rule #4 & #5 lifecycle - freeze, release, and consume', () => {
  const mockWx = {
    _storage: {},
    getStorageSync(k) { return this._storage[k]; },
    setStorageSync(k, v) { this._storage[k] = v; }
  };

  const list = coupons.loadCoupons(mockWx);
  const targetId = list[0].id;

  // 下单冻结
  const frozen = coupons.freezeCoupon(targetId, 'ORDER_TEST_101', mockWx);
  assert.equal(frozen, true);
  let updated = coupons.loadCoupons(mockWx).find(c => c.id === targetId);
  assert.equal(updated.status, 'FROZEN');

  // 取消订单退券
  const released = coupons.releaseCoupon(targetId, 'PASSENGER_CANCEL', mockWx);
  assert.equal(released, true);
  updated = coupons.loadCoupons(mockWx).find(c => c.id === targetId);
  assert.equal(updated.status, 'AVAILABLE');

  // 再次冻结并真实核销
  coupons.freezeCoupon(targetId, 'ORDER_TEST_102', mockWx);
  const consumed = coupons.consumeCoupon(targetId, 'ORDER_TEST_102', mockWx);
  assert.equal(consumed, true);
  updated = coupons.loadCoupons(mockWx).find(c => c.id === targetId);
  assert.equal(updated.status, 'CONSUMED');
});

test('coupons: exclusions for bidding orders and claim code', () => {
  const coupon = {
    id: 'T_BID',
    status: 'AVAILABLE',
    type: 'AMOUNT',
    discountCents: 500,
    minSpendCents: 1000,
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL']
  };
  // 司机竞价订单排斥优惠券
  assert.equal(coupons.calculateDiscount(coupon, {
    baseFareCents: 2000,
    orderType: 'BID',
    pricingMode: 'BID'
  }), 0);

  // 领券与兑换码
  const mockWx = {
    _storage: {},
    getStorageSync(k) { return this._storage[k]; },
    setStorageSync(k, v) { this._storage[k] = v; }
  };
  const claimRes = coupons.claimCoupon('VIP2026', mockWx);
  assert.equal(claimRes.success, true);
  assert.ok(claimRes.coupon);
});

test('coupons: findBestCoupon selects highest discount', () => {
  const best = coupons.findBestCoupon(3000);
  assert.ok(best);
  assert.equal(best.status, 'AVAILABLE');
  assert.ok(best.calculatedDiscount > 0);
});

test('safety: emergency contacts CRUD and message generator', () => {
  const mockWx = {
    _storage: {},
    getStorageSync(k) { return this._storage[k]; },
    setStorageSync(k, v) { this._storage[k] = v; }
  };

  const initial = safety.loadEmergencyContacts(mockWx);
  assert.ok(Array.isArray(initial));

  const added = safety.addEmergencyContact({ name: '李四', phone: '13900000002', relation: '朋友' }, mockWx);
  assert.equal(added.name, '李四');

  const list = safety.loadEmergencyContacts(mockWx);
  assert.ok(list.some(c => c.name === '李四'));

  const msg = safety.createEmergencyMessage({ driverCarPlate: '鄂A·12345', start: '宜昌东站', dest: '国贸大厦' }, '李四');
  assert.match(msg, /鄂A·12345/);
  assert.match(msg, /宜昌东站/);
  assert.match(msg, /110/);

  safety.removeEmergencyContact(added.id, mockWx);
  const afterRemove = safety.loadEmergencyContacts(mockWx);
  assert.ok(!afterRemove.some(c => c.id === added.id));
});

test('extras: options and format utilities conform to cents', () => {
  assert.ok(extras.PRIORITY_OPTIONS.length >= 3);
  assert.equal(extras.formatCents(500), '5.00');
  assert.equal(extras.formatCents(0), '0.00');
});
