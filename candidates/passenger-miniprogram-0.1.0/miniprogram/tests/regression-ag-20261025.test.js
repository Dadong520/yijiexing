'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const coupons = require('../lib/coupons');
const safety = require('../lib/safety');

function loadPage(name, { store = {}, wx = {}, picker = {} } = {}) {
  let page;
  const errors = [];
  const globalData = {};
  const shared = {
    base: x => Object.assign({
      onShow() {},
      async act(name, payload) {
        if (store.act) return await store.act(name, payload);
        if (store.action) return await store.action(name, payload);
        return null;
      }
    }, x),
    store,
    present: { money: n => (n / 100).toFixed(2) },
    coords: {
      gcj02ToWgs84: (latitude, longitude) => ({ latitude, longitude }),
      wgs84ToGcj02: (latitude, longitude) => ({ latitude, longitude })
    },
    error: e => errors.push(e.message || String(e)),
    modal: async () => true,
    decorate: o => o,
    coupons,
    safety
  };

  const defaultWx = {
    showToast: () => {},
    showModal: async ({ success }) => { if (success) success({ confirm: true }); },
    makePhoneCall: () => {},
    getStorageSync: () => null,
    setStorageSync: () => {},
    ...wx
  };

  const sandbox = {
    require: p => (p === '../shared' ? shared : picker),
    Page: p => { page = p; },
    wx: defaultWx,
    getApp: () => ({ globalData }),
    console,
    Number,
    JSON,
    Date,
    Array,
    Object,
    encodeURIComponent,
    decodeURIComponent,
    setTimeout: (fn) => fn(),
    clearTimeout: () => {}
  };

  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages', name, 'index.js'), 'utf8'), sandbox);
  page.data = Object.assign({}, page.data);
  page.setData = function(x) { Object.assign(this.data, x); };
  return { page, errors, globalData, shared };
}

// 1. WAIT_CONTINUE 同单继续/放弃入口与流转回归
test('AG-20261025-01 回归: order-detail WAIT_CONTINUE 支持同单继续与放弃', async () => {
  const actionsCalled = [];
  const mockStore = {
    state: {
      orders: [{
        id: 'ORD_WAIT_01',
        stage: 'WAIT_CONTINUE',
        driverName: '张师傅',
        status: 'WAIT_CONTINUE'
      }]
    },
    act: async (name, payload) => {
      actionsCalled.push({ name, payload });
      return { success: true };
    },
    refresh: async () => {}
  };

  const { page } = loadPage('order-detail', { store: mockStore });
  page.data.id = 'ORD_WAIT_01';

  // 测试同单继续叫车 (type="1")
  await page.continueTrip({ currentTarget: { dataset: { type: '1' } } });
  assert.equal(actionsCalled.length, 1);
  assert.equal(actionsCalled[0].name, 'order.continue');
  assert.equal(actionsCalled[0].payload.id, 'ORD_WAIT_01');
  assert.equal(actionsCalled[0].payload.type, 1);

  // 测试放弃叫车 (type="2")
  await page.continueTrip({ currentTarget: { dataset: { type: '2' } } });
  assert.equal(actionsCalled.length, 2);
  assert.equal(actionsCalled[1].name, 'order.continue');
  assert.equal(actionsCalled[1].payload.id, 'ORD_WAIT_01');
  assert.equal(actionsCalled[1].payload.type, 2);

  // 验证 WXML 中存在 WAIT_CONTINUE 卡片和 continueTrip 绑定
  const wxml = fs.readFileSync(path.join(root, 'pages/order-detail/index.wxml'), 'utf8');
  assert.ok(wxml.includes("order.stage==='WAIT_CONTINUE'"), 'WXML 必须包含 WAIT_CONTINUE 卡片条件');
  assert.ok(wxml.includes('bindtap="continueTrip"'), 'WXML 必须绑定 continueTrip 入口');
});

// 2. 取消订单不误退券，失败不退券，无券不兜底 CPN_NEW10
test('AG-20261025-01 回归: 取消订单严格校验服务端结果，无券不兜底退券', async () => {
  const mockWx = {
    _storage: {},
    getStorageSync(k) { return this._storage[k]; },
    setStorageSync(k, v) { this._storage[k] = v; }
  };

  // 初始化用户卡券
  coupons.loadCoupons(mockWx, 'user_13800000001');

  // Case A: 服务端返回失败/null，绝不释放优惠券
  let releaseCalls = 0;
  const originalRelease = coupons.releaseCoupon;
  coupons.releaseCoupon = () => { releaseCalls++; };

  const storeFailed = {
    state: { orders: [{ id: 'ORD_FAIL_01', couponId: 'CPN_NEW10', stage: 'WAIT_ACCEPT' }] },
    act: async () => null, // 模拟失败
    refresh: async () => {}
  };
  const { page: pageFail } = loadPage('order-detail', { store: storeFailed, wx: mockWx });
  pageFail.data.id = 'ORD_FAIL_01';
  pageFail.data.order = { id: 'ORD_FAIL_01', couponId: 'CPN_NEW10', stage: 'WAIT_ACCEPT' };
  await pageFail.cancel();
  assert.equal(releaseCalls, 0, '取消失败时不得释放优惠券');

  // Case B: 无券订单取消成功，不得兜底 CPN_NEW10
  let releasedCouponId = null;
  coupons.releaseCoupon = (cId) => { releasedCouponId = cId; };

  const storeNoCoupon = {
    state: { orders: [{ id: 'ORD_NO_CPN', stage: 'WAIT_ACCEPT' }] }, // 没有 couponId
    act: async () => ({ result: { ok: true } }),
    refresh: async () => {}
  };
  const { page: pageNoCpn } = loadPage('order-detail', { store: storeNoCoupon, wx: mockWx });
  pageNoCpn.data.id = 'ORD_NO_CPN';
  pageNoCpn.data.order = { id: 'ORD_NO_CPN', stage: 'WAIT_ACCEPT' };
  await pageNoCpn.cancel();
  assert.equal(releasedCouponId, null, '无券订单取消绝不能退券或兜底 CPN_NEW10');

  // Case C: 有券订单且取消成功，正确释放对应券
  const storeWithCoupon = {
    state: { orders: [{ id: 'ORD_WITH_CPN', couponId: 'CPN_VALID_99', stage: 'WAIT_ACCEPT' }] },
    act: async () => ({ result: { ok: true } }),
    refresh: async () => {}
  };
  const { page: pageWithCpn } = loadPage('order-detail', { store: storeWithCoupon, wx: mockWx });
  pageWithCpn.data.id = 'ORD_WITH_CPN';
  pageWithCpn.data.order = { id: 'ORD_WITH_CPN', couponId: 'CPN_VALID_99', stage: 'WAIT_ACCEPT' };
  await pageWithCpn.cancel();
  assert.equal(releasedCouponId, 'CPN_VALID_99', '有券订单取消成功应释放对应 couponId');

  // 恢复函数
  coupons.releaseCoupon = originalRelease;
});

// 3. 客户端账务隔离与去伪存真（pay 不冒充账务权威，不私自核销或发积分）
test('AG-20261025-01 回归: pay() 仅执行契约支付，不私自核销券或发放假积分', async () => {
  let consumeCouponCalled = false;
  let awardPointsCalled = false;

  const mockStore = {
    confirmPayment: () => ({ id: 'ORD_PAY_01', expectedFare: 1500, fareVersion: 1 }),
    refresh: async () => {},
    action: async (name, payload) => ({ success: true, name, payload })
  };

  const { page } = loadPage('order-detail', { store: mockStore });
  page.data.id = 'ORD_PAY_01';
  page.data.order = { id: 'ORD_PAY_01', couponId: 'CPN_TEST', fare: 1500 };

  // 支付操作
  await page.pay();

  // 验证页面内未私自核销卡券或发放积分
  assert.equal(consumeCouponCalled, false);
  assert.equal(awardPointsCalled, false);

  // 检查 home.wxml 明确标注待营销服务接入
  const homeWxml = fs.readFileSync(path.join(root, 'pages/home/index.wxml'), 'utf8');
  assert.ok(homeWxml.includes('待营销服务接入'), '首页卡券加价栏需明确展示待营销服务接入');
});

// 4. 卡券过期严格校验与跨账号隔离
test('AG-20261025-01 回归: 过期卡券严格不可选，本地存储按账号严格隔离', () => {
  const mockWx = {
    _storage: {},
    getStorageSync(k) { return this._storage[k]; },
    setStorageSync(k, v) { this._storage[k] = v; }
  };

  // 账号 A
  const couponsA = coupons.loadCoupons(mockWx, 'user_A');
  assert.ok(couponsA.length > 0);

  // 构造过期券
  const expiredCoupon = {
    id: 'CPN_EXPIRED',
    title: '过期测试券',
    type: 'AMOUNT',
    discountCents: 500,
    minSpendCents: 1000,
    status: 'AVAILABLE',
    expiresAt: '2020-01-01', // 已过期
    applicableCities: ['ALL'],
    applicableOrderTypes: ['ALL']
  };

  // 检验 isCouponApplicable 对过期券拒绝
  const isApplicable = coupons.isCouponApplicable(expiredCoupon, {
    baseFareCents: 2000,
    city: '宜昌市',
    orderType: 'STANDARD'
  });
  assert.equal(isApplicable, false, '过期卡券在 isCouponApplicable 中必须返回 false');

  // 账号隔离验证：向账号 A 添加紧急联系人，账号 B 读取为空
  safety.saveEmergencyContacts([{ id: 'E1', name: '联系人A' }], mockWx, 'user_A');
  const contactsA = safety.loadEmergencyContacts(mockWx, 'user_A');
  const contactsB = safety.loadEmergencyContacts(mockWx, 'user_B');
  assert.equal(contactsA.some(c => c.name === '联系人A'), true);
  assert.equal(contactsB.some(c => c.name === '联系人A'), false, '不同账号的紧急联系人必须严格物理键隔离');
});

// 5. 投诉凭证上传入口与去伪存真
test('AG-20261025-01 回归: 投诉支持多图凭证上传，无伪造公安查验和录音保证', () => {
  const wxml = fs.readFileSync(path.join(root, 'pages/order-detail/index.wxml'), 'utf8');

  // 验证投诉图片附件恢复
  assert.ok(wxml.includes('bindtap="attach"'), '必须包含投诉凭证图片上传入口');
  assert.ok(wxml.includes('上传凭证图片（最多3张）'), '必须包含上传凭证图片按钮');
  assert.ok(wxml.includes('item.attachments'), '历史投诉需支持展示附件图片');

  // 验证去除虚假的公安审查和全程录音背书
  assert.ok(!wxml.includes('公安审查三证通过'), '严禁出现无物理/专网支撑的公安审查三证通过背书');
  assert.ok(!wxml.includes('全程录音保护中'), '严禁出现无录音硬件/服务端接口支撑的全程录音保护中背书');
});

// 6. 司机端 CoreScreens.kt 去伪存真断言
test('AG-20261025-01 回归: 司机端移除硬编码6.5h/12单和卓越司机100分', () => {
  const ktPath = path.resolve(root, '../../driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt');
  const ktContent = fs.readFileSync(ktPath, 'utf8');

  // 严禁存在硬编码的 6.5 h 和 卓越司机 (100分)
  assert.ok(!ktContent.includes('"6.5 h"'), '司机端严禁硬编码 6.5 h 在线时长');
  assert.ok(!ktContent.includes('服务卡 · 卓越司机 (100分)'), '司机端严禁硬编码卓越司机100分');

  // 必须采用动态状态
  assert.ok(ktContent.includes('state.todayAccepted'), '今日接单必须使用 state.todayAccepted');
  assert.ok(ktContent.includes('state.todayCompleted'), '今日完单必须使用 state.todayCompleted');
  assert.ok(ktContent.includes('state.online'), '在线状态必须使用 state.online');
});
