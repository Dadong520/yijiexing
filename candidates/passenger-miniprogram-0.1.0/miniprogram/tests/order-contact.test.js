'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
function load(order) {
  let page, modal, companyRequests = 0, phoneCalls = 0;
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../pages/order-detail/index.js'), 'utf8'), {
    Page: value => { page = value; },
    require: name => name === '../shared' ? { base: value => value } : {},
    wx: { showModal: value => { modal = value; }, makePhoneCall: () => { phoneCalls++; } },
    getApp: () => ({ globalData: {} })
  });
  page.data.order = order;
  page.callOrderCompany = () => { companyRequests++; };
  page.callDriverMock();
  return { modal, companyRequests: () => companyRequests, phoneCalls: () => phoneCalls };
}
test('contact explains unavailable relay without fake number or connected state', () => {
  for (const company of [null, { status: 'UNCONFIGURED' }, { status: 'DISABLED', supportPhone: '400-123-4567' }, { status: 'CONFIGURED', supportPhone: '   ' }]) {
    const h = load({ driver: { mobileMasked: '138****0003' }, company });
    assert.match(h.modal.content, /电话中继尚未配置/);
    assert.doesNotMatch(h.modal.content, /正在.*连接|138|挂断/);
    assert.equal(h.modal.confirmText, '知道了');
    h.modal.success({ confirm: true });
    assert.equal(h.companyRequests(), 0);
    assert.equal(h.phoneCalls(), 0);
  }
});
test('company is a separate explicitly selected help route, never a driver dial', () => {
  const h = load({ driver: { mobileMasked: '138****0003' }, company: { status: 'CONFIGURED', supportPhone: '400-123-4567' } });
  assert.equal(h.modal.confirmText, '公司客服');
  h.modal.success({ confirm: false });
  assert.equal(h.companyRequests(), 0);
  h.modal.success({ confirm: true });
  assert.equal(h.companyRequests(), 1);
  assert.equal(h.phoneCalls(), 0);
  assert.doesNotMatch(h.modal.content, /138\*|正在通过/);
});
