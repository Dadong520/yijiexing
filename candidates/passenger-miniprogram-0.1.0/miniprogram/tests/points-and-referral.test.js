'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const points = require('../lib/points');

test('points: load points initial data and tier rules', () => {
  const mockWx = {
    _storage: {},
    getStorageSync(k) { return this._storage[k]; },
    setStorageSync(k, v) { this._storage[k] = v; }
  };

  const data = points.loadPointsData(mockWx);
  assert.ok(data);
  assert.equal(typeof data.totalPoints, 'number');
  assert.ok(data.tierRules.length >= 3);
  assert.equal(data.referralStats.invitedCount, 3);
  assert.equal(data.referralStats.firstOrderCount, 2);
});

test('points: award points for referral and order complete', () => {
  const mockWx = {
    _storage: {},
    getStorageSync(k) { return this._storage[k]; },
    setStorageSync(k, v) { this._storage[k] = v; }
  };

  const initial = points.loadPointsData(mockWx);
  const basePoints = initial.availablePoints;

  // 邀请好友完成首单
  const updated = points.awardPoints('REFERRAL_FIRST_ORDER', 100, '好友 188****0001 完成首单', mockWx);
  assert.equal(updated.availablePoints, basePoints + 100);
  assert.equal(updated.referralStats.firstOrderCount, 3);
  assert.equal(updated.history[0].type, 'REFERRAL_FIRST_ORDER');
  assert.equal(updated.history[0].typeName, '邀请好友完成首单');

  // 支付金额奖励积分
  const afterPay = points.awardPoints('PAYMENT_AMOUNT', 35, '实付金额奖励积分', mockWx);
  assert.equal(afterPay.availablePoints, basePoints + 135);
  assert.equal(afterPay.history[0].type, 'PAYMENT_AMOUNT');
});
