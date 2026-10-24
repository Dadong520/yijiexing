'use strict';
const { base, store, error, points, getAccountOwner } = require('../shared');

Page(base({
  data: {
    showInviteModal: false,
    showPointsModal: false,
    showRulesModal: false,
    inviteCode: '演示未启用',
    pointsData: {
      totalPoints: 0,
      availablePoints: 0,
      referralStats: { invitedCount: 0, firstOrderCount: 0, totalBonusPoints: 0 },
      history: [],
      tierRules: []
    }
  },

  syncState() {
    this.refreshPoints();
  },

  refreshPoints() {
    try {
      const accountKey = getAccountOwner();
      if (!accountKey) {
        this.setData({ pointsData: JSON.parse(JSON.stringify(points.DEFAULT_POINTS_DATA)), inviteCode: '演示未启用' });
        return;
      }
      if (points && points.loadPointsData) {
        const pd = points.loadPointsData(typeof wx !== 'undefined' ? wx : null, accountKey);
        this.setData({ pointsData: pd });
      }
    } catch (_) {}
  },

  goOrders() {
    wx.switchTab({
      url: '/pages/orders/index'
    });
  },

  goCoupons() {
    wx.navigateTo({
      url: '/pages/coupons/index'
    });
  },

  goSafety() {
    wx.navigateTo({
      url: '/pages/safety/index'
    });
  },

  onOpenPoints() {
    this.refreshPoints();
    this.setData({ showPointsModal: true });
  },

  onClosePoints() {
    this.setData({ showPointsModal: false });
  },

  onOpenRules() {
    this.setData({ showRulesModal: true });
  },

  onCloseRules() {
    this.setData({ showRulesModal: false });
  },

  onOpenInvite() {
    this.refreshPoints();
    const mobile = this.data.state && this.data.state.passenger && this.data.state.passenger.mobile;
    const suffix = mobile ? mobile.slice(-4) : '6688';
    this.setData({
      showInviteModal: true,
      inviteCode: 'YJX' + suffix
    });
  },

  onCloseInvite() {
    this.setData({ showInviteModal: false });
  },

  onCopyInviteCode() {
    const text = `【宜捷行开发体验】邀请您体验页面。演示邀请码 [${this.data.inviteCode}] 未接入服务端拉新，不发放乘车券、积分或其他奖励。`;
    wx.setClipboardData({
      data: text,
      success: () => {
        wx.showToast({ title: '邀请语与邀请码已复制', icon: 'success' });
        this.onCloseInvite();
      },
      fail: () => {
        wx.showToast({ title: '复制失败，请重试', icon: 'none' });
      }
    });
  },

  async logout() {
    try {
      await store.logout();
    } catch (e) {
      error(e);
    }
  }
}));