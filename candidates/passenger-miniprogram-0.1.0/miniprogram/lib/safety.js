'use strict';

const CONTACTS_BASE_KEY = 'yijiexing.safety.contacts.v1';
const CONFIG_BASE_KEY = 'yijiexing.safety.config.v1';

function getStorageKey(baseKey, accountKey) {
  if (!accountKey || accountKey === 'anonymous') return baseKey;
  return `${baseKey}.${accountKey}`;
}

const DEFAULT_CONFIG = {
  autoShareNight: false,
  dialNumber: '110',
  notice: '体验环境：未接入硬件录音与警方自动专网，紧急按钮仅调起本地电话拨号'
};

const DEFAULT_CONTACTS = [];

function getStorage(wxPlatform) {
  const p = wxPlatform || (typeof wx !== 'undefined' ? wx : null);
  if (!p || !p.getStorageSync) return null;
  return p;
}

function loadSafetyConfig(wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  const key = getStorageKey(CONFIG_BASE_KEY, accountKey);
  if (!p) return { ...DEFAULT_CONFIG };
  try {
    const data = p.getStorageSync(key);
    if (data && typeof data === 'object') return { ...DEFAULT_CONFIG, ...data };
  } catch (_) {}
  return { ...DEFAULT_CONFIG };
}

function saveSafetyConfig(config, wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  const key = getStorageKey(CONFIG_BASE_KEY, accountKey);
  try {
    p.setStorageSync(key, config);
  } catch (_) {}
}

function loadEmergencyContacts(wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  const key = getStorageKey(CONTACTS_BASE_KEY, accountKey);
  if (!p) return [];
  try {
    const data = p.getStorageSync(key);
    if (Array.isArray(data)) return data;
  } catch (_) {}
  return [];
}

function saveEmergencyContacts(contacts, wxPlatform, accountKey) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  const key = getStorageKey(CONTACTS_BASE_KEY, accountKey);
  try {
    p.setStorageSync(key, contacts);
  } catch (_) {}
}

function addEmergencyContact(contact, wxPlatform, accountKey) {
  const list = loadEmergencyContacts(wxPlatform, accountKey);
  const item = {
    id: 'SEC_' + Date.now(),
    name: contact.name,
    phone: contact.phone,
    relation: contact.relation || '紧急联系人',
    isEmergency: true
  };
  list.push(item);
  saveEmergencyContacts(list, wxPlatform, accountKey);
  return item;
}

function removeEmergencyContact(contactId, wxPlatform, accountKey) {
  const list = loadEmergencyContacts(wxPlatform, accountKey);
  const filtered = list.filter(c => c.id !== contactId);
  saveEmergencyContacts(filtered, wxPlatform, accountKey);
  return filtered;
}

function createEmergencyMessage(tripInfo, contactName) {
  const plate = tripInfo.driverCarPlate || '待接单车辆';
  const start = tripInfo.start || '未指定起点';
  const dest = tripInfo.dest || '未指定终点';
  return `【紧急求助通知】${contactName || '您好'}：我正在乘坐宜捷行车辆（车牌：${plate}），行程路线从【${start}】到【${dest}】。若您收到此消息且无法与我取得联系，请及时关注我的安全并协助向110报警求助。`;
}

module.exports = {
  DEFAULT_CONFIG,
  DEFAULT_CONTACTS,
  loadSafetyConfig,
  saveSafetyConfig,
  loadEmergencyContacts,
  saveEmergencyContacts,
  addEmergencyContact,
  removeEmergencyContact,
  createEmergencyMessage
};
