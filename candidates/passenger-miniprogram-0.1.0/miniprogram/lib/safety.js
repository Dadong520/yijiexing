'use strict';

const CONTACTS_STORAGE_KEY = 'yijiexing.safety.contacts.v1';
const CONFIG_STORAGE_KEY = 'yijiexing.safety.config.v1';

const DEFAULT_CONFIG = {
  autoShareNight: true,       // 22:00-06:00 自动开启夜间守护
  audioRecordProtection: true,// 行程录音保护标识
  dialNumber: '110'
};

const DEFAULT_CONTACTS = [
  {
    id: 'SEC_001',
    name: '家人（备用联系人）',
    phone: '13800000101',
    relation: '家人',
    isEmergency: true
  }
];

function getStorage(wxPlatform) {
  const p = wxPlatform || (typeof wx !== 'undefined' ? wx : null);
  if (!p || !p.getStorageSync) return null;
  return p;
}

function loadSafetyConfig(wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return { ...DEFAULT_CONFIG };
  try {
    const data = p.getStorageSync(CONFIG_STORAGE_KEY);
    if (data && typeof data === 'object') return { ...DEFAULT_CONFIG, ...data };
  } catch (_) {}
  return { ...DEFAULT_CONFIG };
}

function saveSafetyConfig(config, wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  try {
    p.setStorageSync(CONFIG_STORAGE_KEY, config);
  } catch (_) {}
}

function loadEmergencyContacts(wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return JSON.parse(JSON.stringify(DEFAULT_CONTACTS));
  try {
    const data = p.getStorageSync(CONTACTS_STORAGE_KEY);
    if (Array.isArray(data)) return data;
  } catch (_) {}
  const initial = JSON.parse(JSON.stringify(DEFAULT_CONTACTS));
  try {
    p.setStorageSync(CONTACTS_STORAGE_KEY, initial);
  } catch (_) {}
  return initial;
}

function saveEmergencyContacts(contacts, wxPlatform) {
  const p = getStorage(wxPlatform);
  if (!p) return;
  try {
    p.setStorageSync(CONTACTS_STORAGE_KEY, contacts);
  } catch (_) {}
}

function addEmergencyContact(contact, wxPlatform) {
  if (!contact || !contact.name || !contact.phone) throw new Error('请完整填写姓名和手机号');
  const list = loadEmergencyContacts(wxPlatform);
  const newContact = {
    id: 'SEC_' + Date.now().toString(36),
    name: String(contact.name).trim(),
    phone: String(contact.phone).trim(),
    relation: String(contact.relation || '亲友').trim(),
    isEmergency: true
  };
  list.push(newContact);
  saveEmergencyContacts(list, wxPlatform);
  return newContact;
}

function removeEmergencyContact(id, wxPlatform) {
  const list = loadEmergencyContacts(wxPlatform);
  const filtered = list.filter(c => c.id !== id);
  saveEmergencyContacts(filtered, wxPlatform);
  return filtered;
}

function createEmergencyMessage(orderInfo, contactName) {
  const car = orderInfo && orderInfo.driverCarPlate ? `车牌[${orderInfo.driverCarPlate}]` : '网约车';
  const start = orderInfo && orderInfo.start ? orderInfo.start : '当前位置';
  const dest = orderInfo && orderInfo.dest ? orderInfo.dest : '目的地';
  return `【宜捷行出行安全求助】尊敬的${contactName}：乘客正在乘坐${car}从${start}前往${dest}，触发了紧急求助，请立即核实或协助拨打110！`;
}

module.exports = {
  loadSafetyConfig,
  saveSafetyConfig,
  loadEmergencyContacts,
  saveEmergencyContacts,
  addEmergencyContact,
  removeEmergencyContact,
  createEmergencyMessage,
  DEFAULT_CONFIG
};
