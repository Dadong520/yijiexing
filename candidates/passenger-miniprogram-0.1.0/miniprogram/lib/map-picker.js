const defaults = require('../config/location')

function normalize(raw, source) {
  if (!raw || !Number.isFinite(raw.latitude) || !Number.isFinite(raw.longitude) ||
      Math.abs(raw.latitude) > 90 || Math.abs(raw.longitude) > 180) {
    throw new Error('没有取得有效地图坐标，请重新选择地点')
  }
  const name = String(raw.name || raw.address || '').trim()
  if (!name) throw new Error('请选择有名称或地址的地点')
  return { name, address: String(raw.address || name), latitude: raw.latitude,
    longitude: raw.longitude, coordinateSystem: 'GCJ02', source }
}

function createPicker(platform, config, pluginLoader) {
  let pending = null
  let opening = false
  const plugin = () => pluginLoader('chooseLocation')
  function pickLocation({ purpose, location } = {}) {
    if (!purpose) return Promise.reject(new Error('未指定选择地点的用途'))
    if (opening || pending) return Promise.reject(new Error('请先完成当前地点选择'))
    opening = true
    return new Promise((resolve, reject) => {
      const fail = err => {
        opening = false; pending = null
        const text = (err && err.errMsg) || String(err || '')
        if (/cancel/i.test(text)) return resolve(null)
        reject(new Error('地图选点暂不可用，请检查小程序定位权限和账号配置；也可选择标注的体验地点。'))
      }
      if (config.mode === 'plugin') {
        if (!config.key || !config.referer) { opening = false; reject(new Error('腾讯地图插件尚未配置 Key 和应用名称')); return }
        try {
          plugin().setLocation(null)
          pending = { purpose }
          let url = 'plugin://chooseLocation/index?key=' + encodeURIComponent(config.key) + '&referer=' + encodeURIComponent(config.referer)
          if (location) {
            const point = normalize(Object.assign({name:'地图中心'}, location), 'tencent-plugin')
            url += '&location=' + encodeURIComponent(JSON.stringify({latitude:point.latitude, longitude:point.longitude}))
          }
          platform.navigateTo({url, success() { opening = false; resolve({pendingPlugin:true}) }, fail})
        } catch (err) { fail(err) }
      } else {
        if (!platform || typeof platform.chooseLocation !== 'function') { fail(); return }
        const options = {success(raw) {
          opening = false
          try { resolve(normalize(raw, 'wechat')) } catch (err) { reject(err) }
        }, fail}
        if (location) {
          try { const point = normalize(Object.assign({name:'地图中心'},location),'wechat'); options.latitude=point.latitude;options.longitude=point.longitude }
          catch(err) {opening=false;reject(err);return}
        }
        platform.chooseLocation(options)
      }
    })
  }
  function consumePickedLocation() {
    if (!pending || config.mode !== 'plugin') return null
    const request = pending; pending = null; opening = false
    const picker = plugin()
    const raw = picker.getLocation()
    // Clear even when cancelled. A second onShow must not reuse old results.
    picker.setLocation(null)
    return raw ? {purpose:request.purpose,location:normalize(raw,'tencent-plugin')} : null
  }
  return { pickLocation, consumePickedLocation }
}

let instance
function singleton() {
  if (!instance) instance = createPicker(typeof wx === 'undefined' ? {} : wx, defaults,
    name => requirePlugin(name))
  return instance
}
module.exports = { createPicker, normalize,
  pickLocation: options => singleton().pickLocation(options),
  consumePickedLocation: () => singleton().consumePickedLocation() }
