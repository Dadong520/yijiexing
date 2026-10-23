#!/usr/bin/env node
// Only project-local files change. Never asks for or stores AppSecret.
const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '..')
const mode = process.argv[2] || 'native'
if (!['native','plugin'].includes(mode)) throw new Error('Usage: node scripts/configure-map.js native|plugin')
const config = {mode,key:'',referer:'宜捷行'}
const app = JSON.parse(fs.readFileSync(path.join(root,'app.json'),'utf8'))
if (mode === 'plugin') {
  config.key = process.env.YIJIE_MAP_KEY || ''
  config.referer = process.env.YIJIE_MAP_REFERER || '宜捷行'
  if (!/^[A-Za-z0-9-]{10,200}$/.test(config.key)) throw new Error('Set YIJIE_MAP_KEY to the public Tencent mini-program plugin Key; never use AppSecret')
  app.plugins = {chooseLocation:{version:'1.2.1',provider:'wx76a9a06e5b4e693e'}}
} else { delete app.plugins }
fs.writeFileSync(path.join(root,'app.json'),JSON.stringify(app,null,2)+'\n')
fs.writeFileSync(path.join(root,'config/location.js'),'module.exports = '+JSON.stringify(config,null,2)+'\n')
console.log('Map mode:',mode,'— configure plugin approval in WeChat account before importing plugin mode.')
