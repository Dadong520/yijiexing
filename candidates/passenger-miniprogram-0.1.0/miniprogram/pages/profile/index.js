'use strict';
const { base, store, error } = require('../shared');

Page(base({
  data: {
    showInviteModal: false,
    inviteCode: 'YJX8888'
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

  onOpenInvite() {
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
    const text = `【宜捷行出行】邀请您体验安全高品质出行！使用我的邀请码 [${this.data.inviteCode}] 注册即送 ¥20 乘车券！`;
    wx.setClipboardData({
      data: text,
      success: () => {
        wx.showToast({ title: '邀请语已复制到剪贴板', icon: 'success' });
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