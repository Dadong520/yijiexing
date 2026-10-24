'use strict';
const shared = require('../shared');
const {
  DEFAULT_CONFIG,
  loadSafetyConfig,
  saveSafetyConfig,
  loadEmergencyContacts,
  addEmergencyContact,
  removeEmergencyContact,
  createEmergencyMessage
} = require('../../lib/safety');

Page({
  data: {
    authenticated: false,
    config: DEFAULT_CONFIG,
    contacts: [],
    showModal: false,
    inputName: '',
    inputPhone: '',
    inputRelation: ''
  },

  onShow() {
    this.refreshData();
  },

  getAccountKey() {
    return (typeof shared.getAccountOwner === 'function') ? shared.getAccountOwner() : null;
  },

  refreshData() {
    const accountKey = this.getAccountKey();
    if (!accountKey) {
      this.setData({
        authenticated: false,
        config: DEFAULT_CONFIG,
        contacts: []
      });
      return;
    }
    const wxPlatform = typeof wx !== 'undefined' ? wx : null;
    this.setData({
      authenticated: true,
      config: loadSafetyConfig(wxPlatform, accountKey),
      contacts: loadEmergencyContacts(wxPlatform, accountKey)
    });
  },

  onToggleNight() {
    // 禁用虚假开启反馈，明确未接入
    this.setData({ 'config.autoShareNight': false });
    wx.showToast({ title: '体验环境：未接入服务端守护接口，暂不可启用', icon: 'none' });
  },

  onToggleAudio() {
    // 禁用虚假开启反馈，明确未接入
    this.setData({ 'config.audioRecordProtection': false });
    wx.showToast({ title: '体验环境：未接入车载录音硬件，暂不可启用', icon: 'none' });
  },

  openAddModal() {
    if (!this.getAccountKey()) {
      return wx.showToast({ title: '请先登录体验账户后再添加联系人', icon: 'none' });
    }
    this.setData({
      showModal: true,
      inputName: '',
      inputPhone: '',
      inputRelation: ''
    });
  },

  closeAddModal() {
    this.setData({ showModal: false });
  },

  onInputName(e) {
    this.setData({ inputName: e.detail.value });
  },

  onInputPhone(e) {
    this.setData({ inputPhone: e.detail.value });
  },

  onInputRelation(e) {
    this.setData({ inputRelation: e.detail.value });
  },

  confirmAddContact() {
    const accountKey = this.getAccountKey();
    if (!accountKey) {
      return wx.showToast({ title: '请先登录体验账户', icon: 'none' });
    }
    const { inputName, inputPhone, inputRelation } = this.data;
    if (!inputName || !inputPhone) {
      wx.showToast({ title: '姓名和电话必填', icon: 'none' });
      return;
    }
    try {
      const wxPlatform = typeof wx !== 'undefined' ? wx : null;
      addEmergencyContact({ name: inputName, phone: inputPhone, relation: inputRelation }, wxPlatform, accountKey);
      this.closeAddModal();
      this.refreshData();
      wx.showToast({ title: '联系人已保存', icon: 'success' });
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' });
    }
  },

  onRemoveContact(e) {
    const accountKey = this.getAccountKey();
    if (!accountKey) return;
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '提示',
      content: '确定要删除该紧急联系人吗？',
      success: (res) => {
        if (res.confirm) {
          const wxPlatform = typeof wx !== 'undefined' ? wx : null;
          removeEmergencyContact(id, wxPlatform, accountKey);
          this.refreshData();
          wx.showToast({ title: '已删除', icon: 'none' });
        }
      }
    });
  },

  onCallPolice() {
    wx.showModal({
      title: '紧急报警提示',
      content: '即将调起系统电话拨号呼叫 110。当前体验环境未接入警方自动专网或定位报备。',
      confirmText: '呼叫',
      confirmColor: '#d93025',
      success: (res) => {
        if (res.confirm) {
          wx.makePhoneCall({
            phoneNumber: '110',
            fail: () => {
              wx.showToast({ title: '模拟环境已调起拨号呼叫', icon: 'none' });
            }
          });
        }
      }
    });
  },

  onSendSos() {
    if (!this.getAccountKey()) {
      return wx.showToast({ title: '请先登录体验账户', icon: 'none' });
    }
    if (this.data.contacts.length === 0) {
      wx.showToast({ title: '请先添加紧急联系人', icon: 'none' });
      return;
    }
    const target = this.data.contacts[0];
    const snap = shared.store.snapshot();
    const order = ((snap.data && snap.data.orders) || []).find(o => ['PICKUP', 'ARRIVED', 'TRIP'].includes(o.stage)) || {};
    const msg = createEmergencyMessage({ driverCarPlate: (order.driver && order.driver.plate) || '未提供', start: order.origin || '暂无进行中行程', dest: order.destination || '未提供' }, target.name);
    wx.showModal({
      title: '求助信息预览（未发送）',
      content: msg,
      showCancel: false,
      confirmText: '知道了'
    });
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' });
  }
});
