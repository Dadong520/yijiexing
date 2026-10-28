'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const coupons = require('../lib/coupons');
const safety = require('../lib/safety');
const points = require('../lib/points');

function loadPage(name, { store = {}, wx = {}, picker = {} } = {}) {
  let page;
  const errors = [];
  const globalData = {};
  let currentOwner = store.owner || null;

  const shared = {
    base: x => Object.assign({
      onShow() {},
      async act(name, payload) {
        if (store.act) return await store.act(name, payload);
        if (store.action) return await store.action(name, payload);
        return null;
      }
    }, x),
    store: {
      snapshot: () => ({
        authenticated: !!currentOwner,
        data: currentOwner ? { passenger: { id: currentOwner } } : null
      }),
      action: async (name, payload) => {
        if (store.action) return await store.action(name, payload);
        return { success: true };
      },
      ...store
    },
    present: { money: n => (n / 100).toFixed(2) },
    coords: {
      gcj02ToWgs84: (latitude, longitude) => ({ latitude, longitude }),
      wgs84ToGcj02: (latitude, longitude) => ({ latitude, longitude })
    },
    error: e => errors.push(e.message || String(e)),
    modal: async () => true,
    decorate: o => o,
    coupons,
    safety,
    points,
    getAccountOwner: () => currentOwner
  };

  const defaultWx = {
    showToast: (...args) => (wx.showToast ? wx.showToast(...args) : {}),
    showModal: async (...args) => (wx.showModal ? wx.showModal(...args) : (args[0] && args[0].success && args[0].success({ confirm: true }))),
    makePhoneCall: (...args) => (wx.makePhoneCall ? wx.makePhoneCall(...args) : {}),
    getStorageSync: (...args) => (wx.getStorageSync ? wx.getStorageSync(...args) : null),
    setStorageSync: (...args) => (wx.setStorageSync ? wx.setStorageSync(...args) : {}),
    switchTab: (...args) => (wx.switchTab ? wx.switchTab(...args) : {}),
    navigateTo: (...args) => (wx.navigateTo ? wx.navigateTo(...args) : {})
  };

  const sandbox = {
    require: p => {
      if (p === '../shared') return shared;
      if (p.includes('safety')) return safety;
      if (p.includes('coupons')) return coupons;
      if (p.includes('points')) return points;
      if (p.includes('operations')) return require('../lib/operations');
      return picker;
    },
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
  page.setData = function(x, cb) {
    Object.assign(this.data, x);
    if (typeof cb === 'function') cb();
  };

  return {
    page,
    errors,
    globalData,
    shared,
    setOwner: (owner) => { currentOwner = owner; }
  };
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

  // Case A: 服务端返回失败/null，绝不释放优惠券
  let releaseCalls = 0;
  const originalRelease = coupons.releaseCoupon;
  coupons.releaseCoupon = () => { releaseCalls++; };

  const storeFailed = {
    owner: 'user_cancel_test',
    state: { orders: [{ id: 'ORD_FAIL_01', couponId: 'CPN_NEW10', stage: 'WAIT_ACCEPT' }] },
    act: async () => null,
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
    owner: 'user_cancel_test',
    state: { orders: [{ id: 'ORD_NO_CPN', stage: 'WAIT_ACCEPT' }] },
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
    owner: 'user_cancel_test',
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

// 3. 客户端账务隔离与有效 spy 接线验证（pay 运行时绝不调用本地核销或发积分）
test('AG-20261025-01 回归: pay() 仅执行契约支付，有效 spy 验证不触发本地账务与积分', async () => {
  let consumeCalls = 0;
  let awardCalls = 0;
  let serverActionName = null;
  let serverActionPayload = null;

  // 挂载真实有效 spy
  const origConsume = coupons.consumeCoupon;
  const origAward = points.awardPoints;
  coupons.consumeCoupon = () => { consumeCalls++; return true; };
  points.awardPoints = () => { awardCalls++; return {}; };

  const mockStore = {
    owner: 'user_pay_test',
    confirmPayment: () => ({ id: 'ORD_PAY_01', expectedFare: 1500, fareVersion: 1 }),
    refresh: async () => {},
    action: async (name, payload) => {
      serverActionName = name;
      serverActionPayload = payload;
      return { success: true };
    }
  };

  const { page } = loadPage('order-detail', { store: mockStore });
  page.data.id = 'ORD_PAY_01';
  page.data.order = { id: 'ORD_PAY_01', couponId: 'CPN_TEST', fare: 1500 };

  // 执行页面支付操作
  await page.pay();

  // 恢复原始方法
  coupons.consumeCoupon = origConsume;
  points.awardPoints = origAward;

  // 断言：客户端绝对没有私自核销优惠券，也没有私自发放积分
  assert.equal(consumeCalls, 0, '客户端 pay() 严禁调用 coupons.consumeCoupon');
  assert.equal(awardCalls, 0, '客户端 pay() 严禁调用 points.awardPoints');

  // 断言：页面正确向服务端调用了契约 order.pay
  assert.equal(serverActionName, 'order.pay');
  assert.equal(serverActionPayload.id, 'ORD_PAY_01');
  assert.equal(serverActionPayload.outcome, 'SUCCESS');
});

// 4. 页面级 A/B 账号切换隔离与未登录检查
test('AG-20261025-01 回归: 页面级 A/B 账号切换与未登录检查，未登录绝不读历史', async () => {
  const sharedStorage = {};
  const mockWx = {
    getStorageSync(k) { return sharedStorage[k]; },
    setStorageSync(k, v) { sharedStorage[k] = v; },
    showToast: () => {},
    showModal: ({ success }) => { if (success) success({ confirm: true }); }
  };

  // --- Step 1: 未登录状态进入 safety 与 coupons 页面 ---
  const { page: safetyPage, setOwner: setSafetyOwner } = loadPage('safety', { store: { owner: null }, wx: mockWx });
  safetyPage.refreshData();
  assert.equal(safetyPage.data.authenticated, false);
  assert.equal(safetyPage.data.contacts.length, 0, '未登录时紧急联系人必须为空，绝不读历史');

  const { page: couponsPage, setOwner: setCouponsOwner } = loadPage('coupons', { store: { owner: null }, wx: mockWx });
  couponsPage.refreshCoupons();
  assert.equal(couponsPage.data.authenticated, false);
  assert.equal(couponsPage.data.allCoupons.length, 0, '未登录时卡券列表必须为空，绝不读历史');

  // 未登录兑换卡券必须被拦截
  let toastMsg = '';
  couponsPage.data.claimCodeInput = 'TESTCODE';
  const origToast = mockWx.showToast;
  mockWx.showToast = ({ title }) => { toastMsg = title; };
  couponsPage.onClaim();
  assert.match(toastMsg, /登录/, '未登录兑换卡券必须提示登录');
  mockWx.showToast = origToast;

  // --- Step 2: 登录用户 A (PA) ---
  setSafetyOwner('PA');
  safetyPage.refreshData();
  assert.equal(safetyPage.data.authenticated, true);
  // 用户 A 添加联系人
  safetyPage.data.inputName = '张三A';
  safetyPage.data.inputPhone = '13800000001';
  safetyPage.confirmAddContact();
  assert.equal(safetyPage.data.contacts.length, 1);
  assert.equal(safetyPage.data.contacts[0].name, '张三A');

  setCouponsOwner('PA');
  couponsPage.refreshCoupons();
  assert.equal(couponsPage.data.authenticated, true);
  assert.ok(couponsPage.data.allCoupons.length > 0, '用户 A 拥有初始体验卡券');

  // --- Step 3: 切换到用户 B (PB) ---
  setSafetyOwner('PB');
  safetyPage.refreshData();
  assert.equal(safetyPage.data.authenticated, true);
  assert.equal(safetyPage.data.contacts.length, 0, '切换到用户 B 后，绝对不能读取用户 A 的联系人');

  // 用户 B 添加自己的联系人
  safetyPage.data.inputName = '李四B';
  safetyPage.data.inputPhone = '13900000002';
  safetyPage.confirmAddContact();
  assert.equal(safetyPage.data.contacts.length, 1);
  assert.equal(safetyPage.data.contacts[0].name, '李四B');

  // --- Step 4: 退出登录 ---
  setSafetyOwner(null);
  safetyPage.refreshData();
  assert.equal(safetyPage.data.authenticated, false);
  assert.equal(safetyPage.data.contacts.length, 0, '退出登录后读取联系人为空');

  // --- Step 5: 重新切回用户 A (PA) ---
  setSafetyOwner('PA');
  safetyPage.refreshData();
  assert.equal(safetyPage.data.contacts.length, 1);
  assert.equal(safetyPage.data.contacts[0].name, '张三A', '重新登录用户 A 能正确读取其私有联系人且不混入用户 B');
});

// 5. 安全中心与司机卡去伪存真与演示文案断言
test('AG-20261025-01 回归: safety 开关禁用虚假开启反馈，卡券页与司机卡去除硬编码企业及三证保证', () => {
  // 检验 safety.js 开关禁用虚假反馈
  let toastMsg = '';
  const mockWx = { showToast: ({ title }) => { toastMsg = title; } };
  const { page: safetyPage } = loadPage('safety', { store: { owner: 'TEST_USER' }, wx: mockWx });

  safetyPage.onToggleNight();
  assert.match(toastMsg, /未接入/, '开启夜间守护必须提示未接入');
  safetyPage.onToggleAudio();
  assert.match(toastMsg, /未接入/, '开启录音保护必须提示未接入');

  // 检验 safety/index.wxml 绑定了 notice 并明确体验版
  const safetyWxml = fs.readFileSync(path.join(root, 'pages/safety/index.wxml'), 'utf8');
  assert.ok(safetyWxml.includes('config.notice'), 'safety/index.wxml 必须绑定 config.notice');
  assert.ok(safetyWxml.includes('安全中心（体验版）'), '标题必须为体验版');
  assert.ok(safetyWxml.includes('disabled="{{true}}"'), '虚假开关必须处于禁用状态');

  // 检验 order-detail/index.wxml 报警文案与司机卡
  const orderWxml = fs.readFileSync(path.join(root, 'pages/order-detail/index.wxml'), 'utf8');
  assert.ok(!orderWxml.includes('直接呼叫110并定位报备'), '严禁宣称定位报备警方专网');
  assert.ok(orderWxml.includes('调起系统拨号盘拨打110'), '必须明确为调起拨号盘');
  assert.ok(orderWxml.includes('宜捷行驾驶员信息卡（演示）'), '司机服务卡必须标注演示');
  assert.ok(!orderWxml.includes('宜昌交运集团 · 宜捷行驾驶员信息卡'), '严禁硬编码宜昌交运集团');
  assert.ok(!orderWxml.includes('YJX-DRV-8021'), '严禁硬编码工号8021');
  assert.ok(!orderWxml.includes('宜昌交运旅游客运有限责任公司'), '严禁硬编码所属企业');

  // 检验 coupons/index.wxml 演示声明与不可抵扣
  const couponsWxml = fs.readFileSync(path.join(root, 'pages/coupons/index.wxml'), 'utf8');
  assert.ok(couponsWxml.includes('前端交互演示'), '卡券页必须包含前端交互演示声明');
  assert.ok(couponsWxml.includes('前端演示·不可抵扣'), '卡券列表必须标明不可抵扣');
  assert.ok(couponsWxml.includes('演示兑换'), '兑换按钮必须为演示兑换');
});

// 6. 司机端 CoreScreens.kt 组织字段与累计口径断言
test('AG-20261025-01 回归: 司机端 CoreScreens.kt 采用累计口径并读取动态企业组织', () => {
  const ktPath = path.resolve(root, '../../driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt');
  const ktContent = fs.readFileSync(ktPath, 'utf8');

  // 严禁存在硬编码的 6.5 h 和 卓越司机 (100分)
  assert.ok(!ktContent.includes('"6.5 h"'), '司机端严禁硬编码 6.5 h');
  assert.ok(!ktContent.includes('服务卡 · 卓越司机 (100分)'), '司机端严禁硬编码卓越司机100分');

  // 标题必须为累计运营明细，列标签必须为累计接单/累计完单
  assert.ok(ktContent.includes('运营明细（累计）'), '标题必须为累计运营明细');
  assert.ok(ktContent.includes('累计接单'), '必须采用累计接单口径');
  assert.ok(ktContent.includes('累计完单'), '必须采用累计完单口径');

  // 组织信息必须动态读取
  assert.ok(!ktContent.includes('Text("宜昌交运", color = Muted'), '严禁硬编码宜昌交运');
  assert.ok(ktContent.includes('organization'), '组织必须读取 driver.organization');
});
