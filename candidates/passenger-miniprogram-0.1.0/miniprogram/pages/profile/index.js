'use strict';
const { base, store, error, points } = require('../shared');

Page(base({
  data: {
    showInviteModal: false,
    showPointsModal: false,
    showRulesModal: false,
    inviteCode: 'YJX8888',
    pointsData: {
      totalPoints: 380,
      availablePoints: 380,
      referralStats: { invitedCount: 3, firstOrderCount: 2, totalBonusPoints: 150 },
      history: [],
      tierRules: []
    }
  },

  onShow() {
    this.refreshPoints();
  },

  refreshPoints() {
    try {
      if (points && points.loadPointsData) {
        const pd = points.loadPointsData();
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
    const text = `【宜捷行出行】邀请您体验高品质出行！使用我的邀请码 [${this.data.inviteCode}] 注册即送 ¥20 专享乘车券，完成首单双方均享豪礼！`;
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