'use strict';
const {
  loadSafetyConfig,
  saveSafetyConfig,
  loadEmergencyContacts,
  addEmergencyContact,
  removeEmergencyContact,
  createEmergencyMessage
} = require('../../lib/safety');

Page({
  data: {
    config: {},
    contacts: [],
    showModal: false,
    inputName: '',
    inputPhone: '',
    inputRelation: ''
  },

  onShow() {
    this.refreshData();
  },

  refreshData() {
    this.setData({
      config: loadSafetyConfig(),
      contacts: loadEmergencyContacts()
    });
  },

  onToggleNight(e) {
    const val = e.detail.value;
    const next = { ...this.data.config, autoShareNight: val };
    saveSafetyConfig(next);
    this.setData({ config: next });
    wx.showToast({ title: val ? '已开启夜间守护' : '已关闭夜间守护', icon: 'none' });
  },

  onToggleAudio(e) {
    const val = e.detail.value;
    const next = { ...this.data.config, audioRecordProtection: val };
    saveSafetyConfig(next);
    this.setData({ config: next });
    wx.showToast({ title: val ? '录音保护已启用' : '录音保护已停用', icon: 'none' });
  },

  openAddModal() {
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
    const { inputName, inputPhone, inputRelation } = this.data;
    if (!inputName || !inputPhone) {
      wx.showToast({ title: '姓名和电话必填', icon: 'none' });
      return;
    }
    try {
      addEmergencyContact({ name: inputName, phone: inputPhone, relation: inputRelation });
      this.closeAddModal();
      this.refreshData();
      wx.showToast({ title: '联系人已保存', icon: 'success' });
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' });
    }
  },

  onRemoveContact(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '提示',
      content: '确定要删除该紧急联系人吗？',
      success: (res) => {
        if (res.confirm) {
          removeEmergencyContact(id);
          this.refreshData();
          wx.showToast({ title: '已删除', icon: 'none' });
        }
      }
    });
  },

  onCallPolice() {
    wx.showModal({
      title: '紧急报警提示',
      content: '即将呼叫 110 报警电话。请在确保自身安全的情况下配合警方处置。',
      confirmText: '呼叫',
      confirmColor: '#d93025',
      success: (res) => {
        if (res.confirm) {
          wx.makePhoneCall({
            phoneNumber: '110',
            fail: () => {
              wx.showToast({ title: '模拟环境已触发110呼叫', icon: 'none' });
            }
          });
        }
      }
    });
  },

  onSendSos() {
    if (this.data.contacts.length === 0) {
      wx.showToast({ title: '请先添加紧急联系人', icon: 'none' });
      return;
    }
    const target = this.data.contacts[0];
    const msg = createEmergencyMessage({ driverCarPlate: '鄂A·88888', start: '当前行程位置', dest: '目的地' }, target.name);
    wx.showModal({
      title: '模拟紧急短信已触发',
      content: msg,
      showCancel: false,
      confirmText: '知道了'
    });
  }
});
