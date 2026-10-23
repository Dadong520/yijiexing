'use strict';
const {createApi,ORIGIN}=require('./api');
const copy=v=>JSON.parse(JSON.stringify(v));
function createStore(options={}) {
 const platform=options.wx || (typeof wx!=='undefined'?wx:null), api=options.api||createApi(platform),key=options.storageKey||'yijiexing.passenger.v1';
 let saved={};try{saved=platform.getStorageSync(key)||{};}catch(_){}
 let config=saved.config||{baseUrl:ORIGIN,access:''},token=saved.token||'',owner=saved.owner||'',pending=saved.pending||[],data=null,epoch=0,issued=0,applied=0,busy=0,error='';const listeners=new Set();let mutation=null;
 const persist=()=>{try{platform.setStorageSync(key,{config,token,owner,pending});}catch(_){throw new Error('本地重试记录保存失败，请先清理存储空间');}};
 const persistReset=()=>{try{persist();}catch(err){try{if(platform.removeStorageSync)platform.removeStorageSync(key);}catch(_){}throw err;}};
 const snapshot=()=>copy({config:{baseUrl:config.baseUrl,hasAccess:!!config.access},authenticated:!!token,data,busy:!!busy,error,pending:pending.filter(p=>p.owner===owner).map(({owner,...p})=>p)});
 const emit=()=>listeners.forEach(fn=>fn(snapshot()));
 const credentials=()=>({baseUrl:config.baseUrl,access:config.access,token});
 function invalidate(){mutation=null;epoch++;issued=0;applied=0;data=null;error='';}
 async function request(op,body,apply=true){const e=epoch,seq=++issued,c=credentials();busy++;emit();try{const result=await api.request(op,body,c);if(e!==epoch)throw Object.assign(new Error('会话已切换，请重新操作'),{stale:true});if(apply&&seq>=applied){data=copy(result);delete data.token;applied=seq;}error='';return result;}catch(err){if(e===epoch){error=err.message;if(err.status===401&&seq===issued){token='';owner='';invalidate();persistReset();}}throw err;}finally{busy--;emit();}}
 const store={snapshot,subscribe(fn){listeners.add(fn);fn(snapshot());return()=>listeners.delete(fn);},
 configure(next){if(!next||typeof next.access!=='string')throw new Error('请输入体验访问码');const baseUrl=(next.baseUrl||config.baseUrl||ORIGIN).replace(/\/$/,'');if(baseUrl!==ORIGIN)throw new Error('仅允许独立体验服务域名');if(baseUrl!==config.baseUrl||next.access!==config.access){token='';owner='';invalidate();config={baseUrl,access:next.access};persist();emit();}return Promise.resolve(snapshot());},
 sendCode(mobile){return request('code',{mobile},false);},
 async login(mobile,code){invalidate();token='';owner='';persistReset();emit();const e=epoch;const result=await request('login',{mobile,code},false);if(e!==epoch)throw new Error('登录会话已切换');if(!result.token||!result.passenger||!result.passenger.id)throw new Error('乘客登录响应无效');token=result.token;owner=result.passenger.id;data=copy(result);delete data.token;try{persist();}catch(err){token='';owner='';invalidate();emit();throw err;}emit();return snapshot();},
 refresh(){if(!token)return Promise.reject(new Error('请先登录体验账户'));const e=epoch;if(mutation)return mutation.catch(()=>{}).then(()=>{if(e!==epoch)throw new Error('会话已切换，请重新操作');return store.refresh();});return request('state',{});},
 confirmPayment(id){const o=data&&data.orders&&data.orders.find(x=>x.id===id);if(!o||(!Number.isSafeInteger(o.fare)||o.fare<0)||(!Number.isSafeInteger(o.fareVersion??0)||(o.fareVersion??0)<0))throw new Error('请先刷新并核对费用');const p={id,expectedFare:o.fare,fareVersion:o.fareVersion??0};if(o.kind==='NEGOTIATED'){if((!Number.isSafeInteger(o.quoteVersion)||o.quoteVersion<0))throw new Error('报价版本无效');p.amount=o.fare;p.quoteVersion=o.quoteVersion;}return Object.freeze(p);},
 async action(name,payload={},opts={}){if(mutation)throw new Error('操作正在确认，请稍后重试');if(!token)throw new Error('请先登录体验账户');if(typeof name!=='string'||!name.length)throw new Error('操作名称无效');
 const existing=pending.find(p=>p.owner===owner&&(opts.requestId?p.requestId===opts.requestId:true));
 let p;if(existing){if(!opts.requestId||existing.action!==name||JSON.stringify(existing.payload)!==JSON.stringify(payload))throw new Error('存在结果未确认的操作，请先使用原请求重试');p=existing;}else{if(opts.requestId)throw new Error('待重试请求不存在');if(name==='order.pay'){if((!Number.isSafeInteger(payload.expectedFare)||payload.expectedFare<0)||(!Number.isSafeInteger(payload.fareVersion)||payload.fareVersion<0))throw new Error('请先核对并确认费用版本');const o=data&&data.orders&&data.orders.find(o=>o.id===payload.id);if(o&&o.kind==='NEGOTIATED'&&(!Number.isSafeInteger(payload.quoteVersion)||payload.amount!==payload.expectedFare))throw new Error('请核对议价金额和版本');}p={owner,requestId:'mp-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)+Math.random().toString(36).slice(2),action:name,payload:copy(payload),status:'pending'};pending.push(p);try{persist();}catch(err){pending=pending.filter(x=>x!==p);emit();throw err;}}
 const e=epoch;
 // Install the barrier before invoking transport so refresh cannot overtake a mutation.
 let release;const barrier=new Promise(resolve=>{release=resolve;});mutation=barrier;
 try{const result=await request('action',{requestId:p.requestId,action:p.action,payload:p.payload});if(e===epoch){const before=pending;pending=pending.filter(x=>x!==p);try{persist();}catch(err){pending=before;p.status='uncertain';err.uncertain=true;throw err;}emit();}return result;}
 catch(err){if(e===epoch){if(err.uncertain||err.stale){p.status='uncertain';}else pending=pending.filter(x=>x!==p);try{persist();}catch(storageError){if(!pending.includes(p))pending.push(p);p.status='uncertain';storageError.uncertain=true;throw storageError;}emit();}throw err;}
 finally{if(mutation===barrier)mutation=null;release();}},
 retry(id){const p=pending.find(p=>p.owner===owner&&p.requestId===id);if(!p)return Promise.reject(new Error('待重试请求不存在'));return store.action(p.action,copy(p.payload),{requestId:id});},
 async logout(){const c=credentials();token='';owner='';invalidate();let storageError;try{persistReset();}catch(err){storageError=err;}emit();if(c.token)try{await api.request('logout',{},c);}catch(_){}if(storageError)throw storageError;return snapshot();}
 };return store;
}
const singleton=createStore();module.exports=singleton;module.exports.createStore=createStore;
