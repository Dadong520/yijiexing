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
    minSpendCents: 2000
  };
  assert.equal(coupons.calculateDiscount(couponAmount, 1500), 0);
  assert.equal(coupons.calculateDiscount(couponAmount, 2500), 1000);

  const couponPercent = {
    id: 'T2',
    status: 'AVAILABLE',
    type: 'PERCENT',
    percent: 80,
    maxDiscountCents: 1500,
    minSpendCents: 1500
  };
  // 2000分 * 20% = 400分
  assert.equal(coupons.calculateDiscount(couponPercent, 2000), 400);
  // 10000分 * 20% = 2000分，但封顶1500分
  assert.equal(coupons.calculateDiscount(couponPercent, 10000), 1500);
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
