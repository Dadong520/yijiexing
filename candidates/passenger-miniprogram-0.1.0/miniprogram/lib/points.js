'use strict';

const STORAGE_BASE_KEY = 'yijiexing.points.v1';

function getStorageKey(accountKey) {
  if (!accountKey || accountKey === 'anonymous') return STORAGE_BASE_KEY;
  return `${STORAGE_BASE_KEY}.${accountKey}`;
}

const DEFAULT_POINTS_DATA = {
  isSimulated: true, // 明确标注为演示/未接入后端真实账务
  notice: '营销积分体系待服务端统一持久化接入，当前仅供界面体验，不作用于真实账户结算',
  totalPoints: 0,
  availablePoints: 0,
  referralStats: {
    invitedCount: 0,
    firstOrderCount: 0,
    totalBonusPoints: 0
  },
  history: [],
  tierRules: [
    { step: 1, target: '成功邀请1位好友完成首单', reward: '赠送 ¥20 专享立减券（待营销服务接入）' },
    { step: 2, target: '累计邀请3位好友完成首单', reward: '额外加赠 200 积分（待营销服务接入）' },
    { step: 3, target: '累计邀请5位好友完成首单', reward: '升级尊享黑金会员 + 宜捷行月度畅行卡（待营销服务接入）' }
  ]
};

function getStorage(wxPlatform) {
  const p = wxPlatform || (typeof wx !== 'undefined' ? wx : null);
  if (!p || !p.getStorageSync) return null;
  return p;
}

function loadPointsData(wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  const key = getStorageKey(accountKey);
  if (!p) return JSON.parse(JSON.stringify(DEFAULT_POINTS_DATA));
  try {
    const data = p.getStorageSync(key);
    if (data && typeof data === 'object' && typeof data.totalPoints === 'number') {
      return data;
    }
  } catch (_) {}
  const initial = JSON.parse(JSON.stringify(DEFAULT_POINTS_DATA));
  try {
    p.setStorageSync(key, initial);
  } catch (_) {}
  return initial;
}

function savePointsData(data, wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  const key = getStorageKey(accountKey);
  try {
    p.setStorageSync(key, data);
  } catch (_) {}
}

function awardPoints(type, points, description, wxPlatform, accountKey) {
  const data = loadPointsData(wxPlatform, accountKey);
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

  savePointsData(data, wxPlatform, accountKey);
  return data;
}

module.exports = {
  DEFAULT_POINTS_DATA,
  loadPointsData,
  savePointsData,
  awardPoints
};
