const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const repo='/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027';
const root=path.join(repo,'candidates/passenger-miniprogram-0.1.0/miniprogram');
let submitted=[],toasts=[],fixture={passenger:{id:'QA-P',city:'420500'},orders:[]};
const store={subscribe(fn){fn({data:fixture,authenticated:true});return()=>{};},snapshot(){return {data:fixture,authenticated:true};},refresh(){return Promise.resolve();},async action(name,payload){submitted.push({name,payload});return {};}};
const common={console,Object,Number,Date,JSON,encodeURIComponent,decodeURIComponent,setInterval(){return 1;},clearInterval(){},wx:{showToast(x){toasts.push(x.title);},navigateTo(){}}};
const mod={exports:{}};
vm.runInNewContext(fs.readFileSync(path.join(root,'pages/shared.js'),'utf8'),{...common,module:mod,require(p){if(p==='../lib/store')return store;if(p==='../lib/presentation')return require(path.join(root,'lib/presentation'));if(p==='../lib/coordinates')return require(path.join(root,'lib/coordinates'));return {};}});
let globalData={};function load(name){let page;vm.runInNewContext(fs.readFileSync(path.join(root,'pages',name,'index.js'),'utf8'),{...common,require(p){return p==='../shared'?mod.exports:{consumePickedLocation(){return null;}};},Page(p){page=p;},getApp(){return {globalData};}});page.data={...page.data};page.setData=function(data){Object.assign(this.data,data);};return page;}
(async()=>{
 const home=load('home');home.onShow();
 home.data.origin={name:'公共起点',latitude:30.66,longitude:111.36};
 home.data.destination=null;home.data.destinationPending=true;
 await home.submit();assert.equal(submitted.length,0);assert.match(toasts.pop(),/请选择目的地/);
 home.data.destination={name:'公共终点',latitude:30.70,longitude:111.29};
 for(const kind of ['STANDARD','BID','RESERVED']){
  home.data.kind=kind;home.data.scheduledDate='2026-10-28';home.data.scheduledTime='12:00';
  await home.submit();const out=submitted.at(-1);assert.equal(out.name,'order.create');assert.equal(out.payload.destination,'公共终点');assert.equal(out.payload.destinationLat,30.70);assert.equal(out.payload.destinationLng,111.29);assert.equal('destinationPending' in out.payload,false);
 }
 globalData={repeatOrder:{origin:home.data.origin,destination:null}};const replay=load('home');replay.onShow();await replay.submit();assert.equal(submitted.length,3);assert.match(toasts.pop(),/请选择目的地/);replay.stop();home.stop();
 const detail=load('order-detail');detail.data.id='HISTORICAL-EXACT-ID';await detail.updateDestination({name:'历史补填公开地点',latitude:30.70,longitude:111.29,coordinateSystem:'WGS84'});const legacy=submitted.at(-1);assert.equal(legacy.name,'order.update');assert.equal(legacy.payload.id,'HISTORICAL-EXACT-ID');assert.equal(legacy.payload.destination,'历史补填公开地点');assert.equal(legacy.payload.destinationLat,30.70);
 const wxml=fs.readFileSync(path.join(root,'pages/home/index.wxml'),'utf8');assert(!wxml.includes('togglePending'));assert(!wxml.includes('destinationPending'));assert(wxml.includes('您要去哪里'));
 const android=fs.readFileSync(path.join(repo,'apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/BookingScreens.kt'),'utf8');const booking=android.slice(android.indexOf('fun BookingScreen('),android.indexOf('fun AddressDialog('));assert(!booking.includes('destinationPending'));assert(booking.includes('.put("destinationLat", coordinate(dLat, true))'));assert(booking.includes('.put("destinationLng", coordinate(dLng, false))'));
 const oldAndroid=fs.readFileSync(path.join(repo,'apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/OrderDetail.kt'),'utf8');assert(oldAndroid.includes('if (o.optBoolean("destinationPending"))'));assert(oldAndroid.includes('ActionButton("补充目的地并确认体验估价"'));assert(!oldAndroid.includes('稍后补充目的地可在接驾前或接驾时直接点击主按钮补充'));
 console.log('PASS: real shared.base client flow; missing/obsolete flag rejection, 3 full-destination types, legacy repeat blocks, legacy same-ID update, Android/WXML entry guards');
})().catch(e=>{console.error(e);process.exitCode=1;});
