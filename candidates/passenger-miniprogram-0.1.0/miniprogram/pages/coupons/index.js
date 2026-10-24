'use strict';
const { loadCoupons } = require('../../lib/coupons');

Page({
  data: {
    activeTab: 'AVAILABLE',
    allCoupons: [],
    filteredCoupons: [],
    availableCount: 0
  },

  onShow() {
    this.refreshCoupons();
  },

  refreshCoupons() {
    const coupons = loadCoupons();
    const available = coupons.filter(c => c.status === 'AVAILABLE');
    const filtered = coupons.filter(c => c.status === this.data.activeTab);
    this.setData({
      allCoupons: coupons,
      filteredCoupons: filtered,
      availableCount: available.length
    });
  },

  onSelectTab(e) {
    const tab = e.currentTarget.dataset.tab;
    const filtered = this.data.allCoupons.filter(c => c.status === tab);
    this.setData({
      activeTab: tab,
      filteredCoupons: filtered
    });
  },

  onUseNow() {
    wx.switchTab({
      url: '/pages/home/index'
    });
  }
});
