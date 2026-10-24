'use strict';

const STORAGE_KEY_POINTS = 'yijiexing.points.v1';

const DEFAULT_POINTS_DATA = {
  totalPoints: 380,
  availablePoints: 380,
  referralStats: {
    invitedCount: 3,
    firstOrderCount: 2,
    totalBonusPoints: 150
  },
  history: [
    {
      id: 'PT_001',
      type: 'REFERRAL_FIRST_ORDER',
      typeName: '邀请好友完成首单',
      points: 100,
      createdAt: '2026-10-22 14:30',
      description: '好友 139****1234 完成首单'
    },
    {
      id: 'PT_002',
      type: 'ORDER_COMPLETE',
      typeName: '行程完成奖励',
      points: 50,
      createdAt: '2026-10-23 09:15',
      description: '完成订单 宜昌东站至当阳宾馆'
    },
    {
      id: 'PT_003',
      type: 'PAYMENT_AMOUNT',
      typeName: '支付金额积分',
      points: 30,
      createdAt: '2026-10-23 09:16',
      description: '实付 ¥32.50 奖励积分'
    },
    {
      id: 'PT_004',
      type: 'REFERRAL_REGISTER',
      typeName: '邀请新用户注册',
      points: 200,
      createdAt: '2026-10-24 10:00',
      description: '好友 186****8899 成功注册'
    }
  ],
  tierRules: [
    { step: 1, target: '成功邀请1位好友完成首单', reward: '赠送 ¥20 专享立减券' },
    { step: 2, target: '累计邀请3位好友完成首单', reward: '额外加赠 200 积分' },
    { step: 3, target: '累计邀请5位好友完成首单', reward: '升级尊享黑金会员 + 宜捷行月度畅行卡' }
  ]
};

function getStorage(wxPlatform) {
  const p = wxPlatform || (typeof wx !== 'undefined' ? wx : null);
  if (!p || !p.getStorageSync) return null;
  return p;
}

function loadPointsData(wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return JSON.parse(JSON.stringify(DEFAULT_POINTS_DATA));
  try {
    const data = p.getStorageSync(STORAGE_KEY_POINTS);
    if (data && typeof data === 'object' && typeof data.totalPoints === 'number') {
      return data;
    }
  } catch (_) {}
  const initial = JSON.parse(JSON.stringify(DEFAULT_POINTS_DATA));
  try {
    p.setStorageSync(STORAGE_KEY_POINTS, initial);
  } catch (_) {}
  return initial;
}

function savePointsData(data, wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  try {
    p.setStorageSync(STORAGE_KEY_POINTS, data);
  } catch (_) {}
}

function awardPoints(type, points, description, wxPlatform) {
  const data = loadPointsData(wxPlatform);
  data.totalPoints = (data.totalPoints || 0) + points;
  data.availablePoints = (data.availablePoints || 0) + points;
  
  const typeNames = {
    REFERRAL_REGISTER: '邀请新用户注册',
    REFERRAL_FIRST_ORDER: '邀请好友完成首单',
    ORDER_COMPLETE: '行程完成奖励',
    PAYMENT_AMOUNT: '支付金额积分'
  };

  const item = {
    id: 'PT_' + Date.now(),
    type,
    typeName: typeNames[type] || '积分变动',
    points: points > 0 ? `+${points}` : `${points}`,
    createdAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
    description: description || ''
  };

  if (!Array.isArray(data.history)) data.history = [];
  data.history.unshift(item);

  if (type === 'REFERRAL_REGISTER') {
    data.referralStats.invitedCount = (data.referralStats.invitedCount || 0) + 1;
  } else if (type === 'REFERRAL_FIRST_ORDER') {
    data.referralStats.firstOrderCount = (data.referralStats.firstOrderCount || 0) + 1;
    data.referralStats.totalBonusPoints = (data.referralStats.totalBonusPoints || 0) + points;
  }

  savePointsData(data, wxPlatform);
  return data;
}

module.exports = {
  DEFAULT_POINTS_DATA,
  loadPointsData,
  savePointsData,
  awardPoints
};
