'use strict';
const shared = require('../shared');
const { loadCoupons, claimCoupon } = require('../../lib/coupons');

Page({
  data: {
    authenticated: false,
    activeTab: 'AVAILABLE',
    allCoupons: [],
    filteredCoupons: [],
    availableCount: 0,
    claimCodeInput: ''
  },

  onShow() {
    this.refreshCoupons();
  },

  getAccountKey() {
    return (typeof shared.getAccountOwner === 'function') ? shared.getAccountOwner() : null;
  },

  refreshCoupons() {
    const accountKey = this.getAccountKey();
    if (!accountKey) {
      this.setData({
        authenticated: false,
        allCoupons: [],
        filteredCoupons: [],
        availableCount: 0
      });
      return;
    }

    const wxPlatform = typeof wx !== 'undefined' ? wx : null;
    const coupons = loadCoupons(wxPlatform, accountKey);
    const available = coupons.filter(c => c.status === 'AVAILABLE');
    const filtered = coupons.filter(c => {
      if (this.data.activeTab === 'AVAILABLE') return c.status === 'AVAILABLE';
      if (this.data.activeTab === 'USED') return c.status === 'USED' || c.status === 'CONSUMED';
      if (this.data.activeTab === 'EXPIRED') return c.status === 'EXPIRED';
      return true;
    });

    this.setData({
      authenticated: true,
      allCoupons: coupons,
      filteredCoupons: filtered,
      availableCount: available.length
    });
  },

  onInputClaimCode(e) {
    this.setData({ claimCodeInput: e.detail.value });
  },

  onClaim() {
    const accountKey = this.getAccountKey();
    if (!accountKey) {
      return wx.showToast({ title: '请先登录体验账户后再兑换', icon: 'none' });
    }
    const code = (this.data.claimCodeInput || '').trim();
    if (!code) {
      return wx.showToast({ title: '请输入体验兑换码', icon: 'none' });
    }
    const wxPlatform = typeof wx !== 'undefined' ? wx : null;
    const res = claimCoupon(code, wxPlatform, accountKey);
    if (res.success) {
      wx.showToast({ title: res.message, icon: 'none', duration: 2500 });
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
    wx.showToast({ title: '卡券仅用于前端UI体验，不参与真实订单抵扣', icon: 'none', duration: 2000 });
    setTimeout(() => {
      wx.switchTab({
        url: '/pages/home/index'
      });
    }, 1200);
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' });
  }
});
