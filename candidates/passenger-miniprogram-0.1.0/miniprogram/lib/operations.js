'use strict';
const CLIENT='MINIPROGRAM';
function features(state={}) {
  const raw=state.operations&&state.operations.passengerFeatures&&state.operations.passengerFeatures.features||{};
  return Object.fromEntries(['instant','reservation','bidding','invoice'].map(k=>[k,typeof raw[k]==='boolean'?raw[k]:true]));
}
function invoiceEnabled(state,order={}){return Object.prototype.hasOwnProperty.call(order,'invoiceApplicationEnabled')?order.invoiceApplicationEnabled===true:features(state).invoice;}
function kindEnabled(state,kind){const key={STANDARD:'instant',RESERVED:'reservation',BID:'bidding'}[kind];return !!key&&features(state)[key];}
function companyView(raw){const c=raw||{};return Object.assign({},c,{name:c.name||'未配置公司',supportPhone:c.status==='CONFIGURED'&&typeof c.supportPhone==='string'?c.supportPhone:'',statusText:{CONFIGURED:(typeof c.supportPhone==='string'&&c.supportPhone.trim()?'已配置':'客服电话未配置'),UNCONFIGURED:'未配置',DISABLED:'公司已停用'}[c.status]||'未配置'});}
function createAnnouncementPresenter(){
  let active=false;
  return async function present({store,wx,isVisible}){
    const snapshot=store.snapshot(),owner=snapshot.data&&snapshot.data.passenger&&snapshot.data.passenger.id;
    if(active||!isVisible()||!snapshot.authenticated||snapshot.busy||!owner||!wx||typeof wx.showModal!=='function')return;
    const candidate=snapshot.data.operations&&snapshot.data.operations.announcements&&snapshot.data.operations.announcements[0];
    if(!candidate)return;
    active=true;
    try{
      const response=await store.action('ops.announcement.ack',{id:candidate.id,version:candidate.version,client:CLIENT});
      const latest=store.snapshot();
      if(!isVisible()||!latest.authenticated||!latest.data||!latest.data.passenger||latest.data.passenger.id!==owner)return;
      if(response.result&&response.result.firstDisplay===true)await new Promise(resolve=>wx.showModal({title:String(candidate.title||'公告'),content:String(candidate.content||''),showCancel:false,confirmText:'知道了',success:resolve,fail:resolve}));
    }catch(_){/* Keep a failed/uncertain action visible in the existing retry journal. */}
    finally{active=false;}
  };
}
const presentAnnouncements=createAnnouncementPresenter();
async function dialCompany(wx,company){
  const c=companyView(company);if(!c.supportPhone)throw Error(c.status==='DISABLED'?'公司已停用':'公司客服电话未配置');
  const confirm=await new Promise(resolve=>wx.showModal({title:'联系公司客服',content:c.name+'\n'+c.supportPhone+'\n确认后打开系统电话，由您决定是否拨出。',success:r=>resolve(r.confirm),fail:()=>resolve(false)}));
  if(confirm)await new Promise((resolve,reject)=>wx.makePhoneCall({phoneNumber:c.supportPhone,success:resolve,fail:()=>reject(Error('拨号未完成，可手动拨打公司电话'))}));
}
module.exports={CLIENT,features,invoiceEnabled,kindEnabled,companyView,createAnnouncementPresenter,presentAnnouncements,dialCompany};
