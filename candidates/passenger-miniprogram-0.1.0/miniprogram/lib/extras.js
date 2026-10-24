'use strict';

const PRIORITY_OPTIONS = [
  { label: '不加价', value: 0, desc: '普通派单' },
  { label: '+2元', value: 200, desc: '较快接单' },
  { label: '+5元', value: 500, desc: '优先派单' },
  { label: '+10元', value: 1000, desc: '极速应答' }
];

const TIP_OPTIONS = [
  { label: '¥2', value: 200 },
  { label: '¥5', value: 500 },
  { label: '¥8', value: 800 },
  { label: '¥10', value: 1000 }
];

function formatCents(cents) {
  if (!Number.isSafeInteger(cents) || cents < 0) return '0.00';
  return (cents / 100).toFixed(2);
}

module.exports = {
  PRIORITY_OPTIONS,
  TIP_OPTIONS,
  formatCents
};
