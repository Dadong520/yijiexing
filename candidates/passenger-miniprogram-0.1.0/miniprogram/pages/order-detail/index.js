'use strict';
const shared = require('../shared');
const { base, store, present, coords, error, modal, decorate } = shared;
const picker = require('../../lib/map-picker');

const loadEmergencyContacts = (shared.safety && typeof shared.safety.loadEmergencyContacts === 'function')
  ? shared.safety.loadEmergencyContacts
  : (() => []);

const createEmergencyMessage = (shared.safety && typeof shared.safety.createEmergencyMessage === 'function')
  ? shared.safety.createEmergencyMessage
  : (() => '');

function calculateStep(stage) {
  switch (stage) {
    case 'AVAILABLE': return 1;
    case 'PICKUP':
    case 'ARRIVED': return 2;
    case 'TRIP': return 3;
    case 'PREPAY':
    case 'WAIT_PAY':
    case 'WAIT_CONTINUE': return 4;
    case 'DONE': return 5;
    default: return 1;
  }
}

const page = base({
  data: {
    id: '',
    order: null,
    stepActive: 1,
    rating: 5,
    review: '',
    complaint: '',
    remark: '',
    dispute: '',
    markers: [],
    mapLat: 30.697,
    mapLng: 111.286,
    showSafetyModal: false,
    showDriverCardModal: false
  },

  onLoad(q) {
    this.setData({ id: q.id || '' });
  },

  async callOrderCompany() {
    try { await require('../../lib/operations').dialCompany(wx,this.data.order&&this.data.order.company); }
    catch(e) { error(e); }
  },
  syncState(s) {
    const raw = (s.orders || []).find(o => o.id === this.data.id);
    if (!raw) {
      this.setData({ order: null });
      return;
    }
    const o = decorate(raw);
    o.company=require('../../lib/operations').companyView(raw.company);
    const dc = raw.distanceConsent;
    o.distanceConsent = dc ? Object.assign({}, dc, {
      active: dc.status === 'PENDING' && dc.expectedStage === raw.stage && Date.parse(dc.expiresAt) > Date.now(),
      title: dc.type === 'DROPOFF' ? '司机申请确认已送达' : '司机申请确认已接到您',
      distanceText: Number.isFinite(dc.distanceMeters) ? Math.round(dc.distanceMeters) + ' 米' : '定位不足，无法计算',
      thresholdText: Number.isInteger(dc.thresholdMeters) ? dc.thresholdMeters + ' 米' : '规则待同步',
      expiresText: present.shortTime(dc.expiresAt)
    }) : null;
    o.adjustments = (raw.adjustments || []).map(a => Object.assign({}, a, {
      beforeText: present.money(a.before),
      afterText: present.money(a.after),
      atText: present.shortTime(a.at)
    }));
    o.bids = (raw.bids || []).map(b => Object.assign({}, b, {
      money: present.money(b.amount)
    }));

    const points = [{ name: '上车点', latitude: o.originLat, longitude: o.originLng }];
    if (!o.destinationPending) points.push({ name: '目的地', latitude: o.destinationLat, longitude: o.destinationLng });
    if (o.driver && o.driver.location) points.push(Object.assign({ name: '司机上报位置' }, o.driver.location));

    const markers = points.filter(p => Number.isFinite(p.latitude) && Number.isFinite(p.longitude)).map((p, i) => Object.assign(coords.wgs84ToGcj02(p.latitude, p.longitude), {
      id: i,
      iconPath: '/assets/location-pin.png',
      width: 28,
      height: 36,
      anchor: { x: 0.5, y: 1 },
      title: p.name,
      label: { content: p.name }
    }));

    this.setData({
      order: o,
      stepActive: calculateStep(o.stage),
      markers,
      mapLat: markers[0] ? markers[0].latitude : 30.697,
      mapLng: markers[0] ? markers[0].longitude : 111.286
    });
  },

  openSafetyPanel() {
    this.setData({ showSafetyModal: true });
  },

  closeSafetyPanel() {
    this.setData({ showSafetyModal: false });
  },

  openDriverCard() {
    this.setData({ showDriverCardModal: true });
  },

  closeDriverCard() {
    this.setData({ showDriverCardModal: false });
  },

  setRating(e) {
    const val = Number(e.currentTarget.dataset.val);
    this.setData({ rating: val });
  },

  callDriverMock() {
    const order = this.data.order || {};
    const company = order.company;
    const companyAvailable = company && company.status === 'CONFIGURED' &&
      typeof company.supportPhone === 'string' && company.supportPhone.trim();
    wx.showModal({
      title: '联系与帮助',
      content: '司机电话中继尚未配置，脱敏号码不可拨打。当前不提供文字聊天。' +
        (companyAvailable ? '如需协助，可前往公司客服电话入口，由您确认拨号。' : '本订单公司客服电话尚未配置。'),
      showCancel: !!companyAvailable,
      confirmText: companyAvailable ? '公司客服' : '知道了',
      success: (res) => {
        if (res.confirm && companyAvailable) this.callOrderCompany();
      }
    });
  },

  triggerPolice() {
    wx.showModal({
      title: '紧急报警呼叫',
      content: '即将调起手机电话系统拨打 110 报警电话。请向接警人员说明具体情况。',
      confirmText: '拨打110',
      confirmColor: '#d93025',
      success: (res) => {
        if (res.confirm) {
          wx.makePhoneCall({
            phoneNumber: '110',
            fail: () => {
              wx.showToast({ title: '模拟环境已触发拨打110', icon: 'none' });
            }
          });
        }
      }
    });
  },

  triggerSos() {
    const accountKey = (typeof shared.getAccountOwner === 'function') ? shared.getAccountOwner() : null;
    const contacts = accountKey ? loadEmergencyContacts(null, accountKey) : [];
    if (contacts.length === 0) {
      wx.showToast({ title: '尚未添加紧急联系人，请前往安全中心添加', icon: 'none' });
      return;
    }
    const target = contacts[0];
    const order = this.data.order;
    const plate = order && order.driver ? order.driver.plate : '待接驾车辆';
    const msg = createEmergencyMessage({
      driverCarPlate: plate,
      start: order ? order.origin : '起点',
      dest: order ? order.destination : '终点'
    }, target.name);

    wx.showModal({
      title: '模拟求助短信通知',
      content: msg,
      showCancel: false,
      confirmText: '确定'
    });
  },

  async decideDistance(e) {
    const order = this.data.order;
    const frozen = order && order.distanceConsent;
    if (!frozen || !frozen.active) return error(new Error('请求已处理或已过期，请刷新订单'));
    const approve = String(e.currentTarget.dataset.approve) === 'true';
    const title = approve ? (frozen.type === 'DROPOFF' ? '确认已经送达' : '确认已经上车') : '拒绝本次确认';
    const text = approve ? '请仅在实际已上车或已送达时同意；同意后订单会推进，送达可能结束本次行程。' : '拒绝后订单保持当前阶段，司机可重新定位后发起请求。';
    if (!(await modal(title, text))) return;
    const latest = this.data.order;
    const current = latest && latest.distanceConsent;
    if (!current || current.id !== frozen.id || latest.id !== order.id || latest.stage !== frozen.expectedStage || current.status !== 'PENDING' || !(Date.parse(current.expiresAt) > Date.now())) return error(new Error('订单或确认请求已变化，请刷新后核对'));
    await this.act('order.distanceConsent', { id: order.id, consentId: frozen.id, approve });
  },

  reportCurrentPosition() {
    const order = this.data.order;
    if (!order || !['AVAILABLE','PREPAY','RESERVED','PICKUP','ARRIVED','TRIP'].includes(order.stage)) return error(new Error('仅活动订单可更新位置'));
    wx.getLocation({
      type: 'wgs84', isHighAccuracy: true,
      success: async (position) => {
        const latest = this.data.order;
        if (!latest || latest.id !== order.id || !['AVAILABLE','PREPAY','RESERVED','PICKUP','ARRIVED','TRIP'].includes(latest.stage)) return;
        const accuracy = Number(position.horizontalAccuracy || position.accuracy);
        if (!Number.isFinite(position.latitude) || !Number.isFinite(position.longitude) || !Number.isFinite(accuracy) || accuracy <= 0) return error(new Error('本次定位无有效精度，请重试；定位不足时仍需您确认'));
        try { await this.act('location.report', { id: order.id, latitude: position.latitude, longitude: position.longitude, accuracy, time: Date.now(), coordinateSystem: 'WGS84' }); }
        catch (e) { error(e); }
      },
      fail: () => error(new Error('未获取定位，请在微信小程序设置允许位置权限；未定位时仍可核对并同意或拒绝司机请求'))
    });
  },

  async selectDriver(e) {
    const driverId = e.currentTarget.dataset.id;
    const o = this.data.order;
    if (await modal('确认选择司机', '选择此司机后将锁定报价，请核对车辆与金额。')) {
      if (!this.data.order || o.id !== this.data.order.id || this.data.order.stage !== 'AVAILABLE') return error(new Error('订单状态已变化，请重新选择'));
      await this.act('order.selectDriver', { id: o.id, driverId });
    }
  },

  async pay() {
    let frozen;
    try {
      frozen = store.confirmPayment(this.data.id);
    } catch (e) {
      return error(e);
    }
    if (!await modal('确认模拟支付', '本次为模拟支付，不扣真实资金。确认金额 ¥' + present.money(frozen.expectedFare) + '。')) return;
    try {
      await store.refresh();
      const now = store.confirmPayment(this.data.id);
      if (JSON.stringify(now) !== JSON.stringify(frozen)) throw new Error('金额或版本已变化，请重新确认');
      await store.action('order.pay', Object.assign({}, frozen, { outcome: 'SUCCESS' }));
      // 遵循Codex协作与账务规则：PREPAY仅为预付，营销持久化与完单积分统一由服务端后续处理，客户端不充当账务权威
    } catch (e) {
      error(e);
    }
  },

  async cancel() {
    const o = this.data.order;
    if (!o) return;
    if (await modal('取消行程', '确定取消当前行程？已模拟支付的款项按服务端规则模拟退款。')) {
      const res = await this.act('order.cancel', { id: o.id, reason: '乘客主动取消' });
      // 严格检查服务端返回：如果失败或返回null，绝不退券
      if (!res || !res.result) return;
      // 只有订单本身有真实使用的couponId时才执行本地释放，严禁兜底释放别单券
      if (o.couponId && shared.coupons && typeof shared.coupons.releaseCoupon === 'function') {
        shared.coupons.releaseCoupon(o.couponId, 'PASSENGER_CANCEL');
      }
    }
  },

  continueTrip(e) {
    this.act('order.continue', { id: this.data.id, type: Number(e.currentTarget.dataset.type) });
  },

  async remark() {
    await this.act('order.remark', { id: this.data.id, remark: this.data.remark });
  },

  async destination() {
    try {
      const p = await picker.pickLocation({ purpose: 'order-destination' });
      if (p && !p.pendingPlugin) await this.updateDestination(p);
    } catch (e) {
      error(e);
    }
  },

  existingDestination() {
    wx.navigateTo({ url: '/pages/places/index?pick=order-destination' });
  },

  async updateDestination(p) {
    const c = p.coordinateSystem === 'WGS84' ? p : coords.gcj02ToWgs84(p.latitude, p.longitude);
    await this.act('order.update', {
      id: this.data.id,
      destination: p.name || p.address,
      destinationLat: c.latitude,
      destinationLng: c.longitude
    });
  },

  async evaluate() {
    await this.act('order.evaluate', {
      id: this.data.id,
      rating: Number(this.data.rating),
      content: this.data.review
    });
  },

  async complain() {
    await this.act('order.complaint', {
      id: this.data.id,
      reason: '行程服务问题',
      content: this.data.complaint
    });
  },

  async attach(e) {
    const complaintId = e.currentTarget.dataset.id;
    try {
      const media = await new Promise((resolve, reject) => wx.chooseMedia({
        count: 3,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        success: resolve,
        fail: reject
      }));
      const files = media.tempFiles || [];
      if (files.some(f => f.size > 512 * 1024)) throw new Error('每张图片须不超过512KB');
      const imagesBase64 = await Promise.all(files.map(f => new Promise((resolve, reject) => wx.getFileSystemManager().readFile({
        filePath: f.tempFilePath,
        encoding: 'base64',
        success: r => resolve(r.data),
        fail: reject
      }))));
      await this.act('order.complaint.attach', { id: this.data.id, complaintId, imagesBase64 });
    } catch (e) {
      error(e);
    }
  },

  block() {
    this.act('order.blockDriver', { id: this.data.id, reason: '乘客选择屏蔽' });
  },

  async dispute() {
    const o = this.data.order;
    await this.act('negotiation.dispute', { id: o.id, quoteVersion: o.quoteVersion, content: this.data.dispute });
  },

  again() {
    const o = this.data.order;
    getApp().globalData.repeatOrder = {
      origin: { name: o.origin, latitude: o.originLat, longitude: o.originLng },
      destination: o.destinationPending ? null : { name: o.destination, latitude: o.destinationLat, longitude: o.destinationLng }
    };
    wx.switchTab({ url: '/pages/home/index' });
  }
});

const show = page.onShow;
page.onShow = function() {
  show.call(this);
  let picked = null;
  try {
    picked = picker.consumePickedLocation();
  } catch (e) {
    error(e);
  }
  if (picked && picked.purpose === 'order-destination') this.updateDestination(picked.location);
  const local = getApp().globalData.selectedPlace;
  if (local && local.purpose === 'order-destination') {
    getApp().globalData.selectedPlace = null;
    this.updateDestination(local.location);
  }
};

Page(page);