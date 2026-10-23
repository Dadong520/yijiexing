#!/usr/bin/env node
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
const root=path.resolve(__dirname,'..')
const app=JSON.parse(fs.readFileSync(path.join(root,'app.json'),'utf8'))
const project=JSON.parse(fs.readFileSync(path.join(root,'project.config.json'),'utf8'))
const failures=[]
function check(ok,message){if(!ok)failures.push(message)}
check(project.compileType==='miniprogram','Wrong project type')
check(project.miniprogramRoot==='./','Project root must be this directory')
check(app.requiredPrivateInfos.includes('chooseLocation'),'Missing chooseLocation privacy declaration')
check(app.permission['scope.userLocation'].desc.length>5,'Missing location purpose')
let jsCount=0
for(const route of app.pages){
  for(const ext of ['js','json','wxml','wxss'])check(fs.existsSync(path.join(root,route+'.'+ext)),route+'.'+ext+' missing')
  const template=fs.readFileSync(path.join(root,route+'.wxml'),'utf8')
  check(!/wx:(?:if|elif)="(?!\{\{)/.test(template),route+' has a constant conditional')
  JSON.parse(fs.readFileSync(path.join(root,route+'.json'),'utf8'))
}
function scan(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){
 if(['preview','tests','qa','node_modules'].includes(e.name))continue
 const p=path.join(dir,e.name)
 if(e.isDirectory())scan(p)
 else if(e.name.endsWith('.js')){new vm.Script(fs.readFileSync(p,'utf8'),{filename:p});jsCount++}
}}
scan(root)
const config=require('../config/location')
check(config.mode!=='plugin'||(config.key&&app.plugins&&app.plugins.chooseLocation),'Plugin mode must have config and app declaration')
check(config.mode==='plugin'||!app.plugins,'Unconfigured developer project must not require an approved plugin')
for(const item of app.tabBar.list)check(app.pages.includes(item.pagePath),'Unregistered tab route '+item.pagePath)
if(failures.length){console.error(failures.join('\n'));process.exitCode=1}
else console.log(JSON.stringify({pages:app.pages.length,parsedJavaScript:jsCount,configuration:'PASS',wechatCompilation:'NOT_RUN_REQUIRES_DEVELOPER_TOOLS',appId:project.appid==='touristappid'?'NOT_CONFIGURED':'CONFIGURED'}))
