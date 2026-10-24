'use strict';
const { loadCoupons, claimCoupon } = require('../../lib/coupons');

Page({
  data: {
    activeTab: 'AVAILABLE',
    allCoupons: [],
    filteredCoupons: [],
    availableCount: 0,
    claimCodeInput: ''
  },

  onShow() {
    this.refreshCoupons();
  },

  refreshCoupons() {
    const coupons = loadCoupons();
    const available = coupons.filter(c => c.status === 'AVAILABLE');
    const filtered = coupons.filter(c => {
      if (this.data.activeTab === 'AVAILABLE') return c.status === 'AVAILABLE';
      if (this.data.activeTab === 'USED') return c.status === 'USED' || c.status === 'CONSUMED';
      if (this.data.activeTab === 'EXPIRED') return c.status === 'EXPIRED';
      return true;
    });
    this.setData({
      allCoupons: coupons,
      filteredCoupons: filtered,
      availableCount: available.length
    });
  },

  onInputClaimCode(e) {
    this.setData({ claimCodeInput: e.detail.value });
  },

  onClaim() {
    const code = (this.data.claimCodeInput || '').trim();
    if (!code) {
      return wx.showToast({ title: '请输入兑换码', icon: 'none' });
    }
    const res = claimCoupon(code);
    if (res.success) {
      wx.showToast({ title: res.message, icon: 'success' });
      this.setData({ claimCodeInput: '', activeTab: 'AVAILABLE' });
      this.refreshCoupons();
    } else {
      wx.showToast({ title: res.message, icon: 'none' });
    }
  },

  onSelectTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ activeTab: tab }, () => {
      this.refreshCoupons();
    });
  },

  onUseNow() {
    wx.switchTab({
      url: '/pages/home/index'
    });
  }
});
