'use strict';
const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)],esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=v=>'¥'+(Number(v||0)/100).toFixed(2), pretty=v=>esc(JSON.stringify(v,(key,value)=>['paircode','pairuri','code_hash'].includes(key.toLowerCase())?undefined:value,2)),time=v=>v&&Number.isFinite(new Date(v).getTime())?esc(new Date(v).toLocaleString('zh-CN',{hour12:false,timeZone:'Asia/Shanghai'})):'未记录';
let session='',state={},page='overview',query='',status='',pageNo=1,loading=false;
const pages=[['overview','◈','经营概览'],['statistics','▥','经营报表'],['cancellations','▧','取消订单记录'],['refunds','¥','模拟退款记录'],['orders','▤','订单管理'],['orderSettlementsAdmin','¥','订单分账记录'],['drivers','♙','司机与车辆'],['passengers','♧','乘客管理'],['payments','¥','模拟账务'],['support','◇','评价投诉反馈'],['tariffsAdmin','⚙','区域运价管理'],['serviceFeesAdmin','¥','区域服务费管理'],['companySplitAccountsAdmin','¥','公司分账账户管理'],['companySplitLedgerAdmin','≡','公司分账账户明细'],['configs','⚙','城市与会员'],['operationDrafts','▦','历史草稿与人工记录'],['announcementsAdmin','◉','公告管理'],['companySupportAdmin','☎','公司与客服电话'],['passengerFeaturesAdmin','☷','乘客端功能配置'],['qualificationExpiryAdmin','♧','资质到期提醒'],['heatmapAdmin','◉','热力图阈值设置'],['driverWalletsAdmin','¥','司机钱包明细'],['driverDailyAdmin','¥','司机每日余额统计'],['serviceScoreRuleAdmin','★','服务分规则配置'],['serviceScoreStatsAdmin','♙','司机服务分统计'],['serviceScoreEntriesAdmin','≡','服务分明细记录'],['invoicesAdmin','▤','开票记录'],['invoiceReissuesAdmin','▤','发票重开申请'],['techInvoicesAdmin','¥','技术服务费开票'],['couponPoolAdmin','¥','优惠券资金池'],['couponCreateAdmin','★','制作优惠券'],['newUserCouponAdmin','♧','新用户注册'],['referralsAdmin','◈','拉新活动'],['pointsMallAdmin','★','积分商城'],['users','♙','账号与权限'],['audit','≡','操作日志']];
const labels={AVAILABLE:'待接单',WAIT_CONTINUE:'等待乘客继续叫车',BID_SENT:'已报价',PREPAY:'待预付',RESERVED:'预约待出发',PICKUP:'接驾中',ARRIVED:'已到达',TRIP:'行程中',WAIT_PAY:'待付款',DONE:'已完成',CANCELLED:'已取消',PENDING:'待处理',APPROVED:'已通过',REJECTED:'已驳回',SUCCESS:'模拟支付成功',FAILED:'失败',UNPAID:'未支付',REFUNDED:'已模拟退款',PROCESSING:'处理中',REPLIED:'已回复',RESOLVED:'已处理',STANDARD:'即时用车',BID:'竞价用车',SIMULATED:'模拟',NEGOTIATED:'线下议价',OFFERED:'已报价，待乘客确认',DISPUTED:'乘客提出价格异议',ACCEPTED:'乘客已确认报价',ACTIVE:'启用',DISABLED:'禁用',WAIT_FIRST_APPROVAL:'待一级审批',WAIT_SECOND_APPROVAL:'待二级审批',WAIT_APPROVAL:'待审批',SCHEDULED:'定时待生效',APPLIED:'已生效',SKIPPED:'已跳过',READY:'待下发',RECORDED:'已记录',SANDBOX_APPROVED:'模拟审核通过',SIMULATED_ISSUED:'模拟已开具',RED_FLUSHED:'已冲红',NOT_ISSUED:'待开具',SIGNED:'已签约(模拟)',UNSIGNED:'未签约',REWARDED:'已奖励',NO_RULE:'未启用规则',LIMIT_REACHED:'已达上限',UNUSED:'未使用',LOCKED:'订单占用',USED:'已使用',EXPIRED:'已过期',WARNING:'不足预警',FUSE:'熔断停领',PAID:'已结清'};
const label=v=>labels[v]||v||'—', badge=v=>`<span class="badge ${['DONE','SUCCESS','APPROVED','RESOLVED','REPLIED','ACTIVE','SIMULATED_ISSUED','SIGNED','REWARDED','UNUSED','PAID'].includes(v)?'good':['FAILED','REJECTED','DISABLED','RED_FLUSHED','FUSE'].includes(v)?'bad':['PENDING','WAIT_PAY','PREPAY','UNSIGNED','NOT_ISSUED','WARNING','LOCKED','NO_RULE','LIMIT_REACHED'].includes(v)?'warn':''}">${esc(label(v))}</span>`;
function toast(msg){$('#toast').textContent=msg;$('#toast').style.opacity=1;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('#toast').style.opacity=0,5000)}
async function api(path,body){const r=await backendFetch('/api/local/admin/v2/'+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json','X-Admin-Session':session},body:body?JSON.stringify(body):undefined});if(r.status===401&&path!=='login'){session='';state={};queryViews.clear();$('#app').hidden=true;$('#login').hidden=false;$('#loginError').textContent='管理会话已失效，请重新登录';throw Error('管理会话已失效，请重新登录')}let j;try{j=await r.json()}catch(e){if(isConnectionError(e))throw connectionError(e);throw Error('服务返回异常，请确认本机入口与云服务已启动后重试')}if(!r.ok||j.code!==0){const e=Error((r.status===403?'权限不足：':'')+(j.message||`请求失败 (${r.status})`));e.status=r.status;throw e;}return j.data}
function fail(e){$('#error').hidden=false;$('#error').textContent=e.message;toast(e.message)}
function can(p){const ps=state.user?.permissions||[];return ps.includes('*')||ps.includes(p)}
async function refresh(){if(loading)return;loading=true;$('#refreshBtn').disabled=true;$('#refreshBtn').textContent='正在同步…';$('#error').hidden=true;try{state=await api('state');$('#sync').textContent='最近同步 '+new Date().toLocaleString('zh-CN')+' · 点击刷新获取两端最新状态';await render()}catch(e){fail(e)}finally{loading=false;$('#refreshBtn').disabled=false;$('#refreshBtn').textContent='刷新数据'}}
const pendingRequests=new Map();
async function act(action,payload,requestId){const fingerprint=JSON.stringify({action,payload});const stableId=requestId||pendingRequests.get(fingerprint)||requestUUID();pendingRequests.set(fingerprint,stableId);const d=await api('action',{action,payload,requestId:stableId});pendingRequests.delete(fingerprint);state=d.state||d.admin||state;await refresh();if(d.result?.approval)toast('变更已进入二级审批，原配置暂不改变。审批号：'+d.result.approval.id);return d.result}
function head(title,desc,buttons=''){return `<div class="pagehead"><div><h1>${title}</h1><p>${desc}</p></div><div class="actions">${buttons}</div></div>`}
function table(headers,rows){return `<div class="tablewrap"><table><thead><tr>${headers.map(x=>`<th>${x}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`}
function details(obj){return '<dl class="details">'+Object.entries(obj).map(([k,v])=>`<dt>${esc(k)}</dt><dd>${v}</dd>`).join('')+'</dl>'}
function modal(title,body,buttons=[]){$('#modalTitle').textContent=title;$('#modalBody').innerHTML=body;$('#modalFoot').replaceChildren();for(const [name,cls,fn] of buttons){const b=document.createElement('button');b.textContent=name;b.className=cls;b.onclick=async()=>{b.disabled=true;try{await fn()}catch(e){toast(e.message);let err=$('#modalBody .modalerror');if(!err){err=document.createElement('p');err.className='error modalerror';err.setAttribute('role','alert');$('#modalBody').prepend(err)}err.textContent=e.message}finally{b.disabled=false}};$('#modalFoot').append(b)}applyPermissions();if(!$('#modal').open)$('#modal').showModal()}
const close=()=>$('#modal').close();
function confirmAction(title,action,payload,fields='',warning='该操作会写入共享数据并留下审计记录。请核对对象与当前状态。'){const requestId=requestUUID();modal(title,`<p>${esc(warning)}</p>${fields}<label>处理原因 / 回复内容<textarea id="reason" minlength="2" maxlength="1000" required placeholder="请填写具体原因，至少 2 个字符"></textarea></label><label><input id="confirmed" type="checkbox">我已核对并确认执行</label>`,[['返回','',close],['确认提交','primary',async()=>{const reason=$('#reason').value.trim();if(reason.length<2)throw Error('请填写至少 2 个字符的原因或回复');if(!$('#confirmed').checked)throw Error('请先勾选确认');const extra=typeof payload==='function'?payload():payload;const result=await act(action,{...extra,reason,reply:reason,confirmed:true},requestId);close();if(!result?.approval)toast('操作已保存，请在两端刷新查看最新结果')} ]])}
function navigate(p){page=p;query='';status='';pageNo=1;render();window.scrollTo(0,0)}
async function render(){const found=pages.find(x=>x[0]===page);$('#pageTitle').textContent=found[2];$('#identity').textContent=`${state.user?.username||''} · ${roleLabel(state.user?.role)}`;$$('nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));try{await ({overview,orders,orderSettlementsAdmin,drivers,passengers,payments,support,tariffsAdmin,serviceFeesAdmin,companySplitAccountsAdmin,companySplitLedgerAdmin,configs,users,audit,statistics,cancellations,refunds,operationDrafts,announcementsAdmin,companySupportAdmin,passengerFeaturesAdmin,qualificationExpiryAdmin,heatmapAdmin,driverWalletsAdmin,driverDailyAdmin,serviceScoreRuleAdmin,serviceScoreStatsAdmin,serviceScoreEntriesAdmin,invoicesAdmin,invoiceReissuesAdmin,techInvoicesAdmin,couponPoolAdmin,couponCreateAdmin,newUserCouponAdmin,referralsAdmin,pointsMallAdmin}[page])();applyPermissions()}catch(e){fail(e)}}
function dataset(key){return Array.isArray(state[key])?state[key]:[]}
function listing(key,title,desc,headers,row,opts={}){let all=opts.items||dataset(key);const statusKey=opts.statusKey||'status';const states=[...new Set(all.map(r=>r[statusKey]).filter(Boolean))];const filtered=all.filter(r=>(!query||JSON.stringify(r).toLowerCase().includes(query.toLowerCase()))&&(!status||r[statusKey]===status));const count=filtered.length,max=Math.max(1,Math.ceil(count/15));pageNo=Math.min(pageNo,max);$('#content').innerHTML=head(title,desc,`<button id="export">导出全部数据</button>${opts.buttons||''}`)+`<section class="panel"><div class="filters"><input id="search" aria-label="搜索" placeholder="搜索编号、姓名、手机号或内容" value="${esc(query||'')}"><select id="filterStatus" aria-label="状态筛选"><option value="">全部状态</option>${states.map(s=>`<option value="${esc(s)}" ${s===status?'selected':''}>${esc(label(s))}</option>`).join('')}</select><button id="searchBtn">查询</button><button id="reset">重置</button></div>${count?table(headers,filtered.slice((pageNo-1)*15,pageNo*15).map(row)):'<div class="empty">暂无符合条件的数据<br><small>调整筛选条件，或在 App 创建业务后刷新。</small></div>'}<div class="pagination"><span>共 ${count} 条 · 每页 15 条</span><button id="prev" ${pageNo===1?'disabled':''}>上一页</button><span>${pageNo} / ${max}</span><button id="next" ${pageNo===max?'disabled':''}>下一页</button></div></section>`;$('#searchBtn').onclick=()=>{query=$('#search').value.trim();status=$('#filterStatus').value;pageNo=1;render()};$('#search').onkeydown=e=>{if(e.key==='Enter')$('#searchBtn').click()};$('#filterStatus').onchange=()=>$('#searchBtn').click();$('#reset').onclick=()=>navigate(page);$('#prev').onclick=()=>{pageNo--;render()};$('#next').onclick=()=>{pageNo++;render()};$('#export').onclick=()=>exportCSV(key,filtered);if(!['orders','drivers','passengers','payments','support','audit'].includes(key))$('#export').hidden=true;return filtered}
async function exportCSV(key){try{if(!can('export'))throw Error('当前账号无导出权限');const r=await backendFetch('/api/local/admin/v2/export?dataset='+encodeURIComponent(key),{headers:{'X-Admin-Session':session}});if(!r.ok){let e;try{e=await r.json()}catch(err){if(isConnectionError(err))throw connectionError(err);throw Error('导出服务返回异常，请检查本机入口与云服务后重试')}throw Error(e.message||'导出失败')}const a=document.createElement('a');a.href=URL.createObjectURL(await r.blob());a.download=`宜捷行-${key}-${new Date().toISOString().slice(0,10)}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);toast('全部数据已导出，操作已记录审计')}catch(e){toast(connectionError(e).message)}}

function queryChoiceLabel(v){return ({ORDER:'订单模拟费用',WITHDRAWAL:'模拟提现',NONE:'无退款',HAS_REFUND:'有模拟退款',PASSENGER:'乘客',DRIVER:'司机',ADMIN:'后台',HISTORY:'历史轮',CURRENT:'当前轮',CLOSED:'已结束',AWAITING_PASSENGER:'等待乘客续叫'}[v]) || label(sourceLabel(v));}
const queryViews = new Map();
let queryTicket = 0;

function queryParams(key, excludePage = false) {
  const view = queryViews.get(key) || { page: 1, pageSize: 20, applied: {} };
  const sp = new URLSearchParams();
  sp.set('dataset', key);
  if (!excludePage) {
    sp.set('page', String(view.page || 1));
    sp.set('pageSize', String(view.pageSize || 20));
  }
  const applied = view.applied || {};
  for (const [k, v] of Object.entries(applied)) {
    if (v !== '' && v !== null && v !== undefined && k !== 'page' && k !== 'pageSize' && k !== 'dataset') {
      sp.set(k, String(v).trim());
    }
  }
  return sp;
}

function renderSummaryCards(summary, definition, dateFiltered=false) {
  if (!summary && !definition) return '';
  const cards = [];
  const add = (lbl, val) => cards.push(`<div class="summary-card" style="padding:10px;border:1px solid #e2e8f0;border-radius:6px;background:#f8fafc;min-width:120px;"><div style="font-size:12px;color:#64748b;">${esc(lbl)}</div><div style="font-size:18px;font-weight:bold;color:#0f172a;">${val}</div></div>`);
  if (typeof summary?.rowCount === 'number') add('总匹配数', esc(String(summary.rowCount)));
  if (typeof summary?.orderCount === 'number') add('订单数', esc(String(summary.orderCount)));
  if (typeof summary?.completedCount === 'number') add('完成数', esc(String(summary.completedCount)));
  if (typeof summary?.cancelledCount === 'number') add('取消数', esc(String(summary.cancelledCount)));
  if (typeof summary?.activeCount === 'number') add('活跃/进行中', esc(String(summary.activeCount)));
  if (typeof summary?.paidFare === 'number') add('已完成模拟收款', money(summary.paidFare));
  if (typeof summary?.refundAmount === 'number') add('模拟退款', money(summary.refundAmount));
  if (typeof summary?.withdrawalAmount === 'number') add('模拟提现', money(summary.withdrawalAmount));
  if (typeof summary?.amount === 'number' && typeof summary?.paidFare !== 'number') add('总金额', money(summary.amount));
  if (typeof summary?.invoiceAmount === 'number') add('开票金额合计', money(summary.invoiceAmount));
  if (typeof summary?.balance === 'number') add('钱包余额合计', money(summary.balance));
  if (typeof summary?.pendingAmount === 'number') add('待入账合计', money(summary.pendingAmount));
  if (typeof summary?.withdrawnAmount === 'number') add('已提现合计', money(summary.withdrawnAmount));
  if (typeof summary?.withdrawingAmount === 'number') add('提现中合计', money(summary.withdrawingAmount));
  if (typeof summary?.orderFee === 'number') add('订单费用合计', money(summary.orderFee));
  if (typeof summary?.paidAmount === 'number') add('乘客实付合计', money(summary.paidAmount));
  if (typeof summary?.couponAmount === 'number') add('优惠券抵扣合计', money(summary.couponAmount));
  if (typeof summary?.driverAmount === 'number') add('司机分账合计', money(summary.driverAmount));
  if (typeof summary?.companyCommission === 'number') add('公司佣金合计', money(summary.companyCommission));
  if (typeof summary?.platformCommission === 'number') add('平台佣金合计', money(summary.platformCommission));

  const defHtml = definition ? `<div class="filter-summary-definition" style="font-size:12px;color:#475569;margin-top:6px;">口径：${esc(String(definition).replaceAll('共享passenger_orders权威记录','共享订单记录').replaceAll('paidFare仅为DONE且仍paid订单的fare','模拟收款仅统计已完成且仍已付款订单的费用').replaceAll('refundAmount','累计模拟退款').replaceAll('CANCELLED/WAIT_CONTINUE','已取消或等待乘客续叫'))} (UTC+8，同条件全匹配汇总)</div>` : '';
  const missingHtml = (summary?.missingDateCount > 0) ? `<div style="font-size:12px;color:#b45309;margin-top:4px;">${esc(String(summary.missingDateCount))} 条记录时间缺失或无效，${dateFiltered?'本次已按日期条件排除':'启用日期筛选时将被排除'}</div>` : '';

  return `<div class="filter-summary" style="margin-bottom:14px;"><div class="summary-cards" style="display:flex;flex-wrap:wrap;gap:10px;">${cards.join('')}</div>${defHtml}${missingHtml}</div>`;
}

function renderAppliedChips(applied, draft, view = {}) {
  const chips = [];
  let isDirty = false;
  for (const [k, v] of Object.entries(applied || {})) {
    if (v !== '' && v !== null && v !== undefined) {
      chips.push(`<span class="filter-chip" style="display:inline-block;padding:2px 8px;background:#e0f2fe;color:#0369a1;border-radius:12px;font-size:12px;margin-right:6px;margin-bottom:4px;">${esc(view.schema?.find(f=>f.name===k)?.label || k)}: ${esc(queryChoiceLabel(String(v)))}</span>`);
    }
  }
  for (const [k, v] of Object.entries(draft || {})) {
    if ((v || '') !== (applied[k] || '')) { isDirty = true; break; }
  }
  const dirtyNotice = isDirty ? '<span style="color:#d97706;font-size:12px;margin-left:8px;">(草稿尚未提交查询)</span>' : '';
  return `<div class="filter-chips-bar" style="margin:6px 0;">已应用过滤：${chips.length ? chips.join('') : '<span style="color:#94a3b8;font-size:12px;">无</span>'}${dirtyNotice}</div>`;
}

async function queryListing(key, title, desc, headers, row, opts = {}) {
  const container = $('#content');
  if (!container) return [];

  let view = queryViews.get(key);
  if (!view) {
    view = { page: 1, pageSize: 20, applied: {}, draft: {}, schema: null, options: null, summary: null, definition: null };
    queryViews.set(key, view);
  }

  const curTicket = ++queryTicket;
  const curNavPage = page;

  const headerBtns = [
    opts.buttons || '',
    `<button type="button" class="btn export-btn" ${can('export') ? '' : 'disabled title="无导出权限"'} style="margin-left:8px;">导出筛选结果</button>`
  ].join('');

  container.innerHTML = `
    ${head(title, desc, headerBtns)}
    <div id="queryError" class="query-error" style="display:none;color:#dc2626;background:#fee2e2;padding:10px;border-radius:6px;margin-bottom:12px;"></div>
    <div id="filterSection"></div>
    <div id="summarySection"></div>
    <div id="tableSection"><div class="query-loading" style="padding:24px;text-align:center;color:#64748b;">正在加载数据...</div></div>
    <div id="paginationSection"></div>
  `;

  const errEl = $('#queryError');
  const filterSec = $('#filterSection');
  const summarySec = $('#summarySection');
  const tableSec = $('#tableSection');
  const pagerSec = $('#paginationSection');

  $('.export-btn')?.addEventListener('click', () => exportFilteredCSV(key));

  const showErr = (msg, canRetry = true) => {
    if (errEl) {
      errEl.style.display = 'block';
      errEl.innerHTML = `<span>${esc(msg)}</span>` + (canRetry ? ` <button type="button" class="btn retry-btn" style="padding:2px 8px;margin-left:8px;font-size:12px;">重试</button>` : '');
      errEl.querySelector('.retry-btn')?.addEventListener('click', () => render());
    }
  };
  const clearErr = () => { if (errEl) { errEl.style.display = 'none'; errEl.innerHTML = ''; } };

  if (view.applied.startDate && view.applied.endDate && view.applied.startDate > view.applied.endDate) {
    showErr('开始日期不能晚于结束日期', false);
    tableSec.innerHTML = '<div style="padding:24px;text-align:center;color:#94a3b8;">查询条件有误，请调整后重试</div>';
    return [];
  }

  let resData = null, queryError = '';
  view.ready=false;$('.export-btn').disabled=true;
  try {
    const sp = queryParams(key, false);
    resData = await api('query?' + sp.toString());

  } catch (e) {
    if (curTicket !== queryTicket || curNavPage !== page) return null;
    queryError = e.message || '查询请求失败';
    if (!view.lastResponse) { tableSec.innerHTML = ''; showErr(queryError, true); return []; }
    resData = view.lastResponse;
  }

  if (curTicket !== queryTicket || curNavPage !== page) return null;

  if(!queryError){view.lastResponse=resData;view.ready=true;}
  $('.export-btn').disabled=!can('export')||!view.ready;
  clearErr();
  const items = !queryError && Array.isArray(resData?.items) ? resData.items : [];
  const total = Number(resData?.total) || 0;
  const curPage = Number(resData?.page) || view.page;
  const curSize = Number(resData?.pageSize) || view.pageSize;
  view.page = curPage;
  view.pageSize = curSize;
  view.schema = resData?.schema || view.schema || [];
  view.options = resData?.options || view.options || {};
  view.summary = resData?.summary || null;
  view.definition = resData?.definition || null;

  summarySec.innerHTML = queryError ? '' : renderSummaryCards(view.summary, view.definition,Boolean(view.applied.startDate||view.applied.endDate));

  const schema = [...(view.schema || [])];
  const hasField = f => schema.some(s => s.name === f);
  if (!hasField('search')) schema.unshift({ name: 'search', label: '综合搜索', type: 'string', availability: 'AVAILABLE' });
  if (!hasField('startDate')) schema.push({ name: 'startDate', label: '开始日期', type: 'date', availability: 'AVAILABLE' });
  if (!hasField('endDate')) schema.push({ name: 'endDate', label: '结束日期', type: 'date', availability: 'AVAILABLE' });

  const filterFields = schema.filter(f => f.name !== 'page' && f.name !== 'pageSize' && f.name !== 'dataset');
  const gridHtml = filterFields.map(f => {
    const isAvail = f.availability === 'AVAILABLE';
    const fieldOpts = view.options[f.name];
    const curVal = view.draft[f.name] !== undefined ? view.draft[f.name] : (view.applied[f.name] || '');
    const disAttr = isAvail ? '' : 'disabled';
    const reasonText = !isAvail && f.reason ? ` (${esc(f.reason)})` : '';
    let ctrl = '';

    if (Array.isArray(fieldOpts) && isAvail) {
      const optsHtml = [
        '<option value="">全部</option>',
        ...fieldOpts.map(o => `<option value="${esc(String(o.value))}" ${String(curVal) === String(o.value) ? 'selected' : ''}>${esc(f.name==='city'?cityLabel(o.value):queryChoiceLabel(o.label || o.value))}</option>`)
      ].join('');
      ctrl = `<select id="q-${esc(f.name)}" name="${esc(f.name)}" class="form-control" ${disAttr} style="width:100%;box-sizing:border-box;">${optsHtml}</select>`;
    } else if (f.name === 'startDate' || f.name === 'endDate' || f.type === 'date') {
      ctrl = `<input type="date" id="q-${esc(f.name)}" name="${esc(f.name)}" class="form-control" value="${esc(curVal)}" ${disAttr} style="width:100%;box-sizing:border-box;" />`;
    } else {
      ctrl = `<input type="text" id="q-${esc(f.name)}" name="${esc(f.name)}" class="form-control" maxlength="100" placeholder="${isAvail ? esc(f.label) : esc(f.reason || '未实现')}" value="${esc(curVal)}" ${disAttr} style="width:100%;box-sizing:border-box;" />`;
    }

    return `<div class="filter-item" style="display:flex;flex-direction:column;gap:4px;">
      <label for="q-${esc(f.name)}" style="font-size:12px;color:${isAvail ? '#475569' : '#526371'};font-weight:500;">${esc(f.label)}${reasonText}</label>
      ${ctrl}
    </div>`;
  }).join('');

  const sizes = [15, 20, 50, 100];
  const sizeSelectHtml = `<select name="pageSize" class="form-control" style="width:auto;margin-left:8px;">${sizes.map(s => `<option value="${s}" ${s === view.pageSize ? 'selected' : ''}>${s} 条/页</option>`).join('')}</select>`;

  filterSec.innerHTML = `
    <form id="queryForm" class="filter-form" style="margin-bottom:14px;background:#ffffff;padding:14px;border:1px solid #e2e8f0;border-radius:6px;">
      <div class="filter-grid" style="display:grid;gap:12px;margin-bottom:12px;">
        ${gridHtml}
      </div>
      <div class="filter-actions" style="display:flex;align-items:center;justify-content:space-between;border-top:1px solid #f1f5f9;padding-top:10px;">
        <div style="display:flex;align-items:center;">
          <button type="submit" class="primary query-submit-btn">查询</button>
          <button type="button" class="btn query-reset-btn" style="margin-left:8px;">重置</button>
          ${sizeSelectHtml}
        </div>
        ${renderAppliedChips(view.applied, view.draft, view)}
      </div>
    </form>
  `;

  const formEl = $('#queryForm');
  formEl.addEventListener('input', e => {
    if (e.target.name && e.target.name !== 'pageSize') {
      view.draft[e.target.name] = e.target.value;
      const chips = formEl.querySelector('.filter-chips-bar'); if(chips) chips.outerHTML = renderAppliedChips(view.applied, view.draft, view);
    }
  });

  formEl.querySelector('select[name="pageSize"]')?.addEventListener('change', e => {
    view.pageSize = Number(e.target.value) || 20;
    view.page = 1;
    render();
  });

  formEl.addEventListener('submit', e => {
    e.preventDefault();
    clearErr();
    const fd = new FormData(formEl);
    const newApplied = {};
    for (const [k, v] of fd.entries()) {
      if (k === 'pageSize') continue;
      const s = String(v).trim();
      if (s) newApplied[k] = s;
    }
    if (newApplied.startDate && newApplied.endDate && newApplied.startDate > newApplied.endDate) {
      showErr('开始日期不能晚于结束日期', false);
      return;
    }
    view.applied = newApplied;
    view.draft = { ...newApplied };
    view.page = 1;
    render();
  });

  formEl.querySelector('.query-reset-btn')?.addEventListener('click', () => {
    clearErr();
    view.applied = {};
    view.draft = {};
    view.page = 1;
    render();
  });

  if (items.length === 0) {
    tableSec.innerHTML = '<div style="padding:32px;text-align:center;color:#64748b;background:#f8fafc;border-radius:6px;border:1px dashed #cbd5e1;">未查询到符合条件的记录</div>';
  } else {
    const rowsHtml = items.map(it => {const cells=row(it);return Array.isArray(cells)?'<tr>'+cells.map(v=>'<td>'+v+'</td>').join('')+'</tr>':cells;});
    tableSec.innerHTML = table(headers, rowsHtml);
  }

  const totalPages = Math.max(1, Math.ceil(total / view.pageSize));
  const hasPrev = view.page > 1;
  const hasNext = view.page < totalPages;

  pagerSec.innerHTML = `
    <div class="pagination" style="display:flex;align-items:center;justify-content:space-between;margin-top:14px;padding:8px 0;font-size:13px;color:#475569;">
      <div>共 <strong>${total}</strong> 条记录，第 <strong>${view.page}</strong> / <strong>${totalPages}</strong> 页</div>
      <div style="display:flex;gap:6px;">
        <button type="button" class="btn prev-page-btn" ${hasPrev ? '' : 'disabled'}>上一页</button>
        <button type="button" class="btn next-page-btn" ${hasNext ? '' : 'disabled'}>下一页</button>
      </div>
    </div>
  `;

  pagerSec.querySelector('.prev-page-btn')?.addEventListener('click', () => {
    if (hasPrev) {
      view.page--;
      render();
    }
  });

  pagerSec.querySelector('.next-page-btn')?.addEventListener('click', () => {
    if (hasNext) {
      view.page++;
      render();
    }
  });

  if(queryError) { tableSec.innerHTML = ''; pagerSec.innerHTML=''; showErr(queryError,true); }
  applyPermissions();
  return items;
}

async function exportFilteredCSV(key) {
  if(!queryViews.get(key)?.ready){toast('请先完成查询后导出已应用条件的结果');return;}
  if (!can('export')) {
    toast('无导出权限 (VIEWER 角色禁止导出)');
    return;
  }
  const params = queryParams(key, true);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 25000);

  const exportBtns = $$('.export-btn');
  exportBtns.forEach(b => { b.disabled = true; b.dataset.prevText = b.textContent; b.textContent = '导出中...'; });

  try {
    const headers = { 'Accept': 'text/csv, application/json' };
    if (session) headers['X-Admin-Session'] = session;

    const res = await fetch('/api/local/admin/v2/export?' + params.toString(), {
      method: 'GET',
      headers,
      signal: controller.signal
    });
    clearTimeout(timer);

    const contentType = res.headers.get('content-type') || '';
    if (!res.ok || !contentType.toLowerCase().includes('text/csv')) {
      let errMsg = `导出失败 HTTP ${res.status}`;
      try {
        const errJson = await res.json();
        errMsg = errJson.message || errJson.error || errMsg;
      } catch (_) {}
      toast(errMsg);
      return;
    }

    const blob = await res.blob();
    const downloadUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = downloadUrl;
    a.download = `${key}_export_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000);
    toast('已导出全部匹配记录；金额列单位为分，操作已记入审计');
  } catch (err) {
    clearTimeout(timer);
    if (err.name === 'AbortError') {
      toast('导出超时，请缩小筛选范围后重试');
    } else {
      toast(err.message || '导出网络错误');
    }
  } finally {
    exportBtns.forEach(b => {
      b.disabled = !can('export');
      if (b.dataset.prevText) b.textContent = b.dataset.prevText;
    });
  }
}


async function statistics() {
  const headers = ['日期', '订单总数', '已完成', '已取消', '进行中', '模拟实付金额', '模拟退款金额', '操作'];
  const rows = await queryListing('statistics', '按日经营统计', '按订单创建日（UTC+8）聚合统计，金额为系统模拟记账', headers, (item) => {
    const d = item.date && item.date !== 'UNKNOWN' ? esc(item.date) : '时间未记录';
    const total = esc(String(item.orderCount ?? 0));
    const completed = esc(String(item.completedCount ?? 0));
    const cancelled = esc(String(item.cancelledCount ?? 0));
    const active = esc(String(item.activeCount ?? 0));
    const paid = money(item.paidFare ?? 0) + ' (模拟)';
    const refund = money(item.refundAmount ?? 0) + ' (模拟)';
    const btn = `<button class="btn btn-sm btn-outline-secondary stat-detail-btn" data-id="${esc(String(item.id || item.date))}">明细</button>`;
    return [d, total, completed, cancelled, active, paid, refund, btn];
  });
  if (!rows) return;
  $$('.stat-detail-btn').forEach(btn => {
    btn.onclick = () => {
      const id = btn.getAttribute('data-id');
      const item = rows.find(r => String(r.id || r.date) === id);
      if (!item) return;
      const html = details({
        '统计日期': item.date && item.date !== 'UNKNOWN' ? esc(item.date) : '时间未记录',
        '订单总数': esc(String(item.orderCount ?? 0)),
        '已完成单数': esc(String(item.completedCount ?? 0)),
        '已取消单数': esc(String(item.cancelledCount ?? 0)),
        '进行中单数': esc(String(item.activeCount ?? 0)),
        '模拟实付总额': money(item.paidFare ?? 0) + ' (模拟记账)',
        '模拟退款总额': money(item.refundAmount ?? 0) + ' (模拟记账)'
      }) + rawRecord(item);
      modal('日度统计明细', html, [['关闭','',close]]);
    };
  });
}

async function cancellations() {
  const headers = ['订单', '起终点', '参与账号', '取消方', '取消时间', '结果', '操作'];
  const typeMap = { PASSENGER: '乘客', DRIVER: '司机', ADMIN: '后台' };
  const rows = await queryListing('cancellations', '订单取消记录', '记录取消方、原因及后续状态，不作为多次取消流水', headers, (item) => {
    const orderCell = `<div>${esc(item.id || item.orderId || '-')}</div><div class="small text-muted">${esc(kindLabel(item.kind))} | ${esc(cityLabel(item.city))}</div>`;
    const routeCell = `<div class="small">起: ${esc(item.origin || '-')}</div><div class="small">终: ${esc(item.destination || '-')}</div>`;
    const partyCell = `<div class="small">客: ${esc(item.passengerName || '')} ${esc(item.passengerMobile || '-')}</div><div class="small">司: ${esc(item.driverName || '')} ${esc(item.driverMobile || '-')}</div>`;
    const canceller = typeMap[item.cancellationType] ? typeMap[item.cancellationType] : (item.cancellationType ? esc(item.cancellationType) : '未记录');
    const cancelReason = item.cancelReason ? esc(item.cancelReason) : '未记录';
    const typeCell = `<div>${canceller}</div><div class="small text-muted" title="${cancelReason}">因: ${cancelReason}</div>`;
    const timeCell = item.cancelledAt ? time(item.cancelledAt) : '未记录';
    const isPending = item.cancellationStatus === 'AWAITING_PASSENGER' || item.cancellationStatus === 'WAIT_CONTINUE';
    const statusText = isPending ? '待乘客确认(进行中)' : (item.cancellationStatus === 'CLOSED' ? '已终止' : esc(item.cancellationStatus || '已处理'));
    const statusBadge = badge(statusText, isPending ? 'warning' : 'secondary');
    const orderId = String(item.id || item.orderId || '');
    const btn = `<button class="btn btn-sm btn-outline-primary cancel-detail-btn" data-order-link="${esc(orderId)}">详情</button>`;
    return [orderCell, routeCell, partyCell, typeCell, timeCell, statusBadge, btn];
  });
  if (!rows) return;
  $$('.cancel-detail-btn').forEach(btn => {
    btn.onclick = () => {
      const orderId = btn.getAttribute('data-order-link');
      const item = rows.find(r => String(r.id || r.orderId) === orderId);
      const orders = dataset('orders');
      if (orders && orders.some(o => String(o.id) === orderId)) {
        orderDetail(orderId);
      } else if (item) {
        const info = details({
          '记录编号': esc(String(item.id || '-')),
          '关联订单': esc(String(item.orderId || item.id || '-')),
          '取消方': item.cancellationType in typeMap ? typeMap[item.cancellationType] : (item.cancellationType ? esc(item.cancellationType) : '未记录'),
          '取消原因': item.cancelReason ? esc(item.cancelReason) : '未记录',
          '取消时间': item.cancelledAt ? time(item.cancelledAt) : '未记录',
          '当前处理状态': esc(item.cancellationStatus || '未记录'),
          '乘客信息': esc(`${item.passengerName || ''} (${item.passengerMobile || '-'})`),
          '司机信息': esc(`${item.driverName || ''} (${item.driverMobile || '-'})`)
        }) + rawRecord(item);
        modal('取消记录只读详情', info, [['关闭','',close]]);
      }
    };
  });
}

async function refunds() {
  const headers = ['记录编号', '关联订单', '乘客与司机', '退款轮次与归属', '模拟金额', '发生时间', '操作'];
  const rows = await queryListing('refunds', '模拟退款记录', '每轮退款独立呈列，系统记账不代表真实资金原路退回', headers, (item) => {
    const idCell = esc(String(item.id || '-'));
    const orderCell = `<div>${esc(String(item.orderId || '-'))}</div>`;
    const partyCell = `<div class="small">客: ${esc(String(item.accountId || '-'))}</div><div class="small">司: ${esc(String(item.driverId || '-'))}</div>`;
    const roundTxt = `第${(Number(item.roundIndex) || 0) + 1}轮`;
    const srcTxt = item.refundSource === 'HISTORY' ? '历史轮' : (item.refundSource === 'CURRENT' ? '当前轮' : esc(item.refundSource || '-'));
    const roundCell = `<div>${esc(roundTxt)}</div><div class="small text-muted">${esc(srcTxt)}</div>`;
    const amtCell = money(item.amount ?? 0) + ' (模拟)';
    const payMode = item.paymentMode ? esc(item.paymentMode) : '未记录';
    const timeCell = `<div>${item.refundedAt ? time(item.refundedAt) : '未记录'}</div><div class="small text-muted">方式: ${payMode}</div>`;
    const orderId = String(item.orderId || '');
    const btn = `<button class="btn btn-sm btn-outline-primary refund-detail-btn" data-id="${esc(String(item.id))}" data-order-link="${esc(orderId)}">详情</button>`;
    return [idCell, orderCell, partyCell, roundCell, amtCell, timeCell, btn];
  });
  if (!rows) return;
  $$('.refund-detail-btn').forEach(btn => {
    btn.onclick = () => {
      const id = btn.getAttribute('data-id');
      const orderId = btn.getAttribute('data-order-link');
      const item = rows.find(r => String(r.id) === id);
      const orders = dataset('orders');
      if (item) {
        const info = details({
          '退款记录ID': esc(String(item.id || '-')),
          '关联订单': esc(String(item.orderId || '-')),
          '乘客账号': esc(String(item.accountId || '-')),
          '司机账号': esc(String(item.driverId || '-')),
          '退款轮次': `第${(Number(item.roundIndex) || 0) + 1}轮`,
          '轮次来源': item.refundSource === 'HISTORY' ? '历史轮' : (item.refundSource === 'CURRENT' ? '当前轮' : esc(item.refundSource || '-')),
          '模拟退款金额': money(item.amount ?? 0) + ' (模拟记账)',
          '支付/退款方式': item.paymentMode ? esc(item.paymentMode) : '未记录',
          '退款时间': item.refundedAt ? time(item.refundedAt) : '未记录'
        }) + rawRecord(item);
        modal('退款记录只读详情', info, [['关闭','',close],...(orders.some(o=>String(o.id)===orderId)?[['查看关联订单','',()=>orderDetail(orderId)]]:[])]);
      }
    };
  });
}


function overview() {
  function getList(key) {
    try {
      if (!Array.isArray(state[key])) return null;
      var d = dataset(key);
      return Array.isArray(d) ? d : null;
    } catch (e) {
      return null;
    }
  }

  var orders = getList('orders');
  var drivers = getList('drivers');
  var passengers = getList('passengers');
  var support = getList('support');

  var ordersLoaded = orders !== null;
  var totalOrders = ordersLoaded ? orders.length : '—';

  // 4个实际全量核心指标
  var inTransitCount = ordersLoaded
    ? orders.filter(function (o) {
        return o && ['PICKUP', 'ARRIVED', 'TRIP'].indexOf(o.stage) !== -1;
      }).length
    : '—';

  var waitPayCount = ordersLoaded
    ? orders.filter(function (o) {
        return o && o.stage === 'WAIT_PAY';
      }).length
    : '—';

  var donePaidOrders = ordersLoaded
    ? orders.filter(function (o) {
        return o && o.stage === 'DONE' && Boolean(o.paid);
      })
    : [];

  var simulatedRevenue = ordersLoaded
    ? money(
        donePaidOrders.reduce(function (sum, o) {
          return sum + Number((o && o.fare) || 0);
        }, 0)
      )
    : '—';

  // 完成与取消作为补充指标
  var doneCount = ordersLoaded
    ? orders.filter(function (o) {
        return o && o.stage === 'DONE';
      }).length
    : '—';

  var cancelledCount = ordersLoaded
    ? orders.filter(function (o) {
        return o && o.stage === 'CANCELLED';
      }).length
    : '—';

  // 待办与两端账户统计
  var driversLoaded = drivers !== null;
  var supportLoaded = support !== null;
  var passengersLoaded = passengers !== null;

  var pendingCertsCount = driversLoaded
    ? drivers.reduce(function (sum, d) {
        return (
          sum +
          ((d && d.certificates) || []).filter(function (c) {
            return c && (c.status === 'PENDING' || !!c.pending);
          }).length
        );
      }, 0)
    : '—';

  var pendingSupportCount = supportLoaded
    ? support.filter(function (s) {
        return s && s.status === 'PENDING';
      }).length
    : '—';

  var totalDriversCount = driversLoaded ? drivers.length : '—';
  var totalPassengersCount = passengersLoaded ? passengers.length : '—';

  // 订单状态分布
  var stageRowsHtml = '';
  if (ordersLoaded && orders.length > 0) {
    var stageCounts = Object.create(null);
    orders.forEach(function (o) {
      var s = (o && o.stage) || 'UNKNOWN';
      stageCounts[s] = (stageCounts[s] || 0) + 1;
    });

    var orderStages = [
      'REQUESTED',
      'MATCHING',
      'ACCEPTED',
      'ARRIVED',
      'PICKUP',
      'TRIP',
      'WAIT_PAY',
      'DONE',
      'CANCELLED'
    ];
    var seen = Object.create(null);
    var stages = [];
    orderStages.forEach(function (s) {
      if (stageCounts[s] !== undefined) {
        stages.push(s);
        seen[s] = true;
      }
    });
    Object.keys(stageCounts).forEach(function (s) {
      if (!seen[s]) {
        stages.push(s);
      }
    });

    stageRowsHtml = stages
      .map(function (s) {
        var cnt = stageCounts[s] || 0;
        var pct = orders.length ? ((cnt / orders.length) * 100).toFixed(1) : '0';
        return (
          '<div class="barrow">' +
          '<span class="bar-label">' +
          esc(label(s)) +
          '</span>' +
          '<div class="bar"><i style="width:' +
          pct +
          '%"></i></div>' +
          '<b class="bar-val">' +
          cnt +
          ' <small>(' +
          pct +
          '%)</small></b>' +
          '</div>'
        );
      })
      .join('');
  } else if (ordersLoaded) {
    stageRowsHtml = '<div class="empty">暂无共享订单数据</div>';
  } else {
    stageRowsHtml = '<div class="empty muted">暂无权限或订单数据未加载</div>';
  }

  // 最新 5 单（按 createdAt 倒序）
  var latestOrdersHtml = '';
  if (ordersLoaded && orders.length > 0) {
    var sorted = orders
      .slice()
      .sort(function (a, b) {
        var ta = a && a.createdAt ? new Date(a.createdAt).getTime() : 0;
        var tb = b && b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return tb - ta;
      })
      .slice(0, 5);

    var rows = sorted.map(function (o) {
      return (
        '<tr>' +
        '<td><span class="mono">' +
        esc((o && o.id) || '') +
        '</span></td>' +
        '<td>' +
        esc((o && o.origin) || '-') +
        ' <span class="arrow">→</span> ' +
        esc((o && o.destination) || '-') +
        '</td>' +
        '<td>' +
        badge(o ? o.stage : '') +
        '</td>' +
        '<td>' +
        orderAmount(o) +
        '</td>' +
        '<td>' +
        time(o ? o.createdAt : '') +
        '</td>' +
        '</tr>'
      );
    });

    latestOrdersHtml = table(
      ['订单编号', '行程路线', '订单状态', '费用', '创建时间'],
      rows
    );
  } else if (ordersLoaded) {
    latestOrdersHtml =
      '<div class="empty">暂无共享订单，请从乘客 App 发起用车。</div>';
  } else {
    latestOrdersHtml =
      '<div class="empty muted">暂无权限查看共享订单列表</div>';
  }

  var detailsData = {
    '待审核证件':
      pendingCertsCount === '—' ? '—' : esc(String(pendingCertsCount)) + ' 项',
    '待处理反馈':
      pendingSupportCount === '—'
        ? '—'
        : esc(String(pendingSupportCount)) + ' 条',
    '注册司机':
      totalDriversCount === '—' ? '—' : esc(String(totalDriversCount)) + ' 人',
    '注册乘客':
      totalPassengersCount === '—'
        ? '—'
        : esc(String(totalPassengersCount)) + ' 人'
  };

  $('#content').innerHTML =
    head('运营工作台', '全量共享订单与两端业务实时运营大盘') +
    '<div class="stats stats-grid">' +
    '<div class="stat">' +
    '<span class="stat-tag">共享订单总数</span>' +
    '<div class="stat-num">' +
    (totalOrders === '—' ? '<span class="na">—</span>' : esc(String(totalOrders))) +
    '</div>' +
    '<div class="stat-desc">全部时间 · 持久化共享订单</div>' +
    '<div class="stat-sub">已完成: <b>' +
    (doneCount === '—' ? '—' : esc(String(doneCount))) +
    '</b> · 取消: <b>' +
    (cancelledCount === '—' ? '—' : esc(String(cancelledCount))) +
    '</b></div>' +
    '</div>' +
    '<div class="stat stat-highlight">' +
    '<span class="stat-tag">运输中行程</span>' +
    '<div class="stat-num">' +
    (inTransitCount === '—'
      ? '<span class="na">—</span>'
      : esc(String(inTransitCount))) +
    '</div>' +
    '<div class="stat-desc">运输中 (PICKUP / ARRIVED / TRIP)</div>' +
    '<div class="stat-sub">在途运力实时履约中</div>' +
    '</div>' +
    '<div class="stat">' +
    '<span class="stat-tag">待收款订单</span>' +
    '<div class="stat-num">' +
    (waitPayCount === '—'
      ? '<span class="na">—</span>'
      : esc(String(waitPayCount))) +
    '</div>' +
    '<div class="stat-desc">行程结束待结算 (WAIT_PAY)</div>' +
    '<div class="stat-sub">两端订单待账务确认</div>' +
    '</div>' +
    '<div class="stat stat-accent">' +
    '<span class="stat-tag">已完成模拟实收</span>' +
    '<div class="stat-num">' +
    (simulatedRevenue === '—'
      ? '<span class="na">—</span>'
      : simulatedRevenue) +
    '</div>' +
    '<div class="stat-desc">已完成模拟实收（不是总账净额）</div>' +
    '<div class="stat-sub">已完成 (DONE) 且已付款 (paid) 合计</div>' +
    '</div>' +
    '</div>' +
    '<div class="split">' +
    '<section class="panel">' +
    '<div class="panel-head">' +
    '<h3>订单状态分布</h3>' +
    '<span class="panel-badge">生命周期分布</span>' +
    '</div>' +
    '<div class="panel-body">' +
    stageRowsHtml +
    '</div>' +
    '</section>' +
    '<section class="panel">' +
    '<div class="panel-head">' +
    '<h3>待办事项与服务概况</h3>' +
    '<span class="panel-badge">协同提醒</span>' +
    '</div>' +
    '<div class="panel-body">' +
    details(detailsData) +
    '<div class="quick-nav">' +
    '<span class="quick-title">快捷只读导航</span>' +
    '<div class="quick-btns">' +
    '<button class="btn-quick" id="btnGoOrders">订单中心 →</button>' +
    '<button class="btn-quick" id="btnGoDrivers">司机管理 →</button>' +
    '<button class="btn-quick" id="btnGoSupport">投诉反馈 →</button>' +
    '<button class="btn-quick" id="btnGoPayments">模拟账务 →</button>' +
    '</div>' +
    '</div>' +
    '<p class="foot-note">统计口径：全部时间共享订单，不计旧合成演示订单。模拟账务为体验记录，不能作为真实营收。</p>' +
    '</div>' +
    '</section>' +
    '</div>' +
    '<section class="panel table-panel">' +
    '<div class="panel-head">' +
    '<h3>最新共享订单</h3>' +
    '<span class="panel-badge">按创建时间倒序（前 5 单）</span>' +
    '</div>' +
    '<div class="table-wrap">' +
    latestOrdersHtml +
    '</div>' +
    '</section>';

  function bind(id, fn) {
    var el = $('#' + id);
    if (el) el.onclick = fn;
  }

  bind('btnGoOrders', function () {
    navigate('orders');
  });
  bind('btnGoDrivers', function () {
    navigate('drivers');
  });
  bind('btnGoSupport', function () {
    navigate('support');
  });
  bind('btnGoPayments', function () {
    navigate('payments');
  });
  bind('goSupport', function () {
    navigate('support');
  });
}
async function orders(){const rows=await queryListing('orders','订单管理','乘客实际创建的共享订单 · 编号、状态、金额与两端保持一致',['订单 / 类型','起终点','乘客 / 司机','车辆 / 城市','状态','费用 / 支付','时间','操作'],o=>`<tr><td><b>${esc(o.id)}</b><small>${esc(kindLabel(o.kind))}</small></td><td>${esc(o.origin)}<small>→ ${esc(o.destination)}</small></td><td>${esc(o.passengerName||o.passengerId||o.passenger?.id)}<small>${esc(o.passengerMobile||'电话未记录')}</small><small>${esc(o.driverName||o.driver?.name||o.driverId||'未选择司机')} · ${esc(o.driverMobile||'')}</small></td><td>${esc(o.plate||'未记录')}<small>${esc(cityLabel(o.city))}</small></td><td>${badge(o.stage)}</td><td>${orderAmount(o)}<small>${label(o.paymentStatus)}</small></td><td>${time(o.createdAt)}</td><td><button data-order="${esc(o.id)}">查看详情</button></td></tr>`,{statusKey:'stage'});if(!rows)return;$$('[data-order]').forEach(b=>b.onclick=()=>orderDetail(b.dataset.order))}
async function orderDetail(id){try{state=await api('state')}catch(e){toast(e.message);return}const o=dataset('orders').find(x=>x.id===id);if(!o){toast('该订单暂无可查看记录，请刷新列表');return}const cf=o.cancelFee?`${money(o.cancelFee.amount)} · ${badge(o.cancelFee.status)}`:'无';modal('订单详情 · '+id,(o.stage==='WAIT_CONTINUE'?'<p class="notice">权威订单状态：等待乘客继续叫车。原司机已取消，其司机端显示已取消；乘客可继续叫车或取消本次订单。此时暂未匹配新的司机。</p>':'')+(o.stage==='WAIT_PAY'&&Number.isInteger(o.fareVersion)?'<p class="notice">行程已结束，等待乘客付款。该单不占用司机的新接单资格；该乘客结清前不能再次叫车。调价后需重新确认金额与费用版本。</p>':'')+details({'费用版本':Number.isInteger(o.fareVersion)?'V'+o.fareVersion:'当前服务未提供','费用口径':o.destinationPending===true?'目的地待补充，费用待定，不代表免费。导出保留后端原始金额分值。':'运价费 + 区域服务费 - 优惠券抵扣；金额单位为元。','订单类型':esc(kindLabel(o.kind)),'订单状态':badge(o.stage),'行程':esc(o.origin)+' → '+esc(o.destination),'乘客':esc(o.passengerId||o.passenger?.id),'司机':esc(o.driver?.name||o.driverId||'未选择'),'应付金额':orderAmount(o),'优惠券':o.couponLabel?`${esc(o.couponLabel)}（抵扣 ${money(o.couponAmount||0)}）`:'未使用','空车费':cf,'支付':badge(o.paymentStatus)+' · 模拟账务','取消原因':esc(o.cancelReason),'创建时间':time(o.createdAt)})+`<div class="toolbar">${!['TRIP','WAIT_PAY','DONE','CANCELLED'].includes(o.stage)?'<button id="cancelOrder" class="danger">人工取消</button>':''}${o.stage==='WAIT_PAY'&&!o.paid?'<button id="adjustFare">调整体验费用</button>':''}${o.stage==='DONE'&&o.paid&&!o.refundAmount?'<button id="refund">模拟退款</button>':''}</div>${orderProgress(o)}${feeDetails(o)}${o.remark?`<h3>乘客备注</h3><p class="record-text">${esc(o.remark)}</p>`:''}${quotationDetails(o)}${distanceConsentDetails(o)}<h3 style="margin-top:28px">乘客评价</h3><p>${o.evaluation?`${'★'.repeat(o.evaluation.rating)} · ${esc(o.evaluation.content)}`:'尚未评价'}</p><h3>状态与操作时间线</h3><div class="timeline">${(o.events||[]).map(eventRow).join('')||'暂无事件'}</div><details><summary>完整共享订单记录</summary><pre class="code">${pretty(o)}</pre></details>`,[['刷新订单','',()=>orderDetail(id)],['关闭','',close]]);if($('#cancelOrder'))$('#cancelOrder').onclick=()=>confirmAction('人工取消订单','order.cancel',()=>({id,expectedStage:o.stage,expectedFare:o.fare,fareVersion:o.fareVersion??0,responsible:Boolean($('#cancelResponsible')?.checked)}),'<label><input id="cancelResponsible" type="checkbox">有责取消（若司机已接单且该区域配置了空车费，按有责规则向乘客计收或从模拟预付中抵扣空车费；已占用优惠券原路退回）</label>',`订单 ${id} 将取消；已模拟预付金额按服务端规则处理，不能恢复原状态。`);if($('#adjustFare'))$('#adjustFare').onclick=()=>confirmAction('调整体验费用','order.adjustFare',()=>({id,expectedStage:o.stage,expectedFare:o.fare,fareVersion:o.fareVersion??0,amount:cents($('#fare').value)}),`<label>新金额（元）<input id="fare" type="number" min="0.01" step="0.01" value="${(o.fare/100).toFixed(2)}"></label>`);if($('#refund'))$('#refund').onclick=()=>confirmAction('模拟全额退款','order.refund',{id,expectedStage:o.stage,expectedFare:o.fare,fareVersion:o.fareVersion??0},'',`模拟退还 ${orderAmount(o)}；若金额或版本已变化，须刷新后重新确认。不发生真实资金交易。`) }
function cents(v){if(!/^\d+(\.\d{1,2})?$/.test(v))throw Error('金额须为非负数且最多两位小数');return Math.round(Number(v)*100)}
async function drivers(){const rows=await queryListing('drivers','司机与车辆','共享司机资料、车辆信息与资质审核',['司机','手机 / 城市','车辆','会员','资质','操作'],d=>`<tr><td><b>${esc(d.name)}</b><small>${esc(d.id)}</small></td><td>${esc(d.mobile)}<small>${esc(cityLabel(d.city))}</small></td><td>${esc(d.plate||d.vehicle?.plate||d.profile?.plate)}<small>${esc(d.vehicleModel||d.vehicle?.model||d.profile?.vehicleModel||'')}</small></td><td>${esc(memberLevelLabel(d.currentLevel))}</td><td>${(d.certificates||[]).filter(c=>c.pending||c.status==='PENDING').length} 项待审</td><td><button data-driver="${esc(d.id)}">资料与审核</button></td></tr>`);if(!rows)return;$$('[data-driver]').forEach(b=>b.onclick=()=>driverDetail(b.dataset.driver))}
function driverDetail(id){
  const d=dataset('drivers').find(x=>x.id===id);if(!d){toast('司机资料未同步，请刷新');return;}
  const certs=d.certificates||[];
  modal('司机资料 · '+d.name,details({'司机编号':esc(id),'手机':esc(d.mobile),'城市':esc(cityLabel(d.city)),'模拟钱包':money(d.balance)})+
    '<h3>有效材料与待审新材料</h3><p class="notice">新材料审核中，旧有效材料继续使用。通过审核才替换；驳回保留旧材料。过期禁止新增接单，在途订单可完成。</p>'+certs.map((c,i)=>`<section class="panel"><h3>${esc(c.title||c.name||c.type)} · ${operationsName(c.validity)||badge(c.status)}</h3><p>当前材料：${esc(c.validFrom)} 至 ${esc(c.validUntil)} · ${badge(c.status)} · V${esc(c.version??0)}</p><p>${esc(c.rejectionReason||c.reply||'')}</p>${c.pending?`<p class="notice">新材料待审：${esc(c.pending.validFrom)} 至 ${esc(c.pending.validUntil)} · ${time(c.pending.submittedAt)}</p>`:''}<div class="toolbar"><button data-preview="${i}" data-source="ACTIVE" data-side="front">当前有效材料</button>${c.pending?`<button data-preview="${i}" data-source="REVIEW" data-side="front">待审新材料</button>`:''}${c.type==='license'?`<button data-preview="${i}" data-source="${c.pending?'REVIEW':'ACTIVE'}" data-side="back">${c.pending?'待审':'有效'}驾驶证背面</button>`:''}</div><div id="preview-${i}"></div>${(c.pending||c.status==='PENDING')?`<button data-cert="${i}" data-approve="true" ${can('certificate.audit')?'':'disabled'}>通过新材料</button> <button data-cert="${i}" data-approve="false" ${can('certificate.audit')?'':'disabled'}>驳回补充</button>`:''}</section>`).join(''),[['关闭','',close]]);
  $$('[data-preview]').forEach(b=>b.onclick=async()=>{
    b.disabled=true;const target=$('#preview-'+b.dataset.preview);target.textContent='正在读取授权附件…';
    try{const c=certs[Number(b.dataset.preview)],result=await api('certificate-file?driverId='+encodeURIComponent(id)+'&type='+encodeURIComponent(c.type)+'&side='+b.dataset.side+'&source='+b.dataset.source);
      if(!/^image\/(png|jpeg|webp|gif)$/.test(result.mimeType))throw Error('附件不是可预览图片');
      target.replaceChildren();const label=document.createElement('p');label.textContent=operationsCertificateImageLabel(result);target.append(label);const img=document.createElement('img');img.src='data:'+result.mimeType+';base64,'+result.imageBase64;img.alt=label.textContent;img.style.cssText='max-width:100%;max-height:450px;object-fit:contain';target.append(img);
    }catch(e){target.textContent=e.message;}finally{b.disabled=false;}
  });
  $$('[data-cert]').forEach(b=>b.onclick=()=>{
    const c=certs[Number(b.dataset.cert)],approved=b.dataset.approve==='true',requestId=requestUUID();
    modal(approved?'审核通过新材料':'驳回新材料','<p>请确认已查看本次候选材料。不会自动通过其他证件。</p><label>审核说明<textarea id="certificateReason" maxlength="500"></textarea></label>',[['返回','',close],['确认审核','primary',async()=>{
      const reason=$('#certificateReason').value.trim();if(!approved&&reason.length<2)throw Error('驳回时请填写至少2个字的原因');
      await operationsWrite('certificate.audit',{driverId:id,type:c.type,approved,...(reason?{reason}:{}),...(c.pending?{expectedVersion:c.version,submissionId:c.pending.submissionId}:{})},requestId);close();toast('审核结果已保存，请刷新司机端查看');
    }]]);
  });
}
async function passengers(){const rows=await queryListing('passengers','乘客管理','乘客账号资料及关联共享订单',['乘客','手机','城市','共享订单','操作'],p=>`<tr><td>${esc(p.name||p.profile?.name)}<small>${esc(p.id)}</small></td><td>${esc(p.mobile)}</td><td>${esc(cityLabel(p.city||p.profile?.city))}</td><td>${dataset('orders').filter(o=>o.passengerId===p.id).length}</td><td><button data-passenger="${esc(p.id)}">查看资料</button></td></tr>`);if(!rows)return;$$('[data-passenger]').forEach(b=>b.onclick=()=>{const p=dataset('passengers').find(x=>x.id===b.dataset.passenger);modal('乘客资料',details({'乘客姓名':esc(p.name||p.profile?.name),'乘客编号':esc(p.id),'手机号码':esc(p.mobile),'所在城市':esc(p.cityName||cityLabel(p.city||p.profile?.city)),'注册时间':time(p.createdAt),'最近更新':time(p.updatedAt)})+`<h3>关联订单</h3>${dataset('orders').filter(o=>o.passengerId===p.id).map(o=>`<section class="panel"><h3>${esc(kindLabel(o.kind))} · ${badge(o.stage)}</h3><p>${esc(o.origin)} → ${esc(o.destination)}</p>${details({'订单编号':esc(o.id),'订单金额':orderAmount(o),'创建时间':time(o.createdAt),'支付状态':badge(o.paymentStatus)})}</section>`).join('')||'<p class="muted">暂无共享订单</p>'}${rawRecord(p)}`,[['关闭','',close]])})}
async function payments(){const rows=await queryListing('payments','模拟账务','共享订单费用为当前状态台账；无终点订单费用待定，导出保留原始金额分值；不涉及真实资金交易',['记录编号','订单 / 司机','类型','金额','状态','时间','操作'],p=>`<tr><td>${esc(p.id||p.orderId)}</td><td>${esc(p.orderId||p.driverId)}</td><td>${esc(p.recordType==='WITHDRAWAL'?'模拟提现':'订单模拟费用')}</td><td>${paymentAmount(p)}</td><td>${badge(p.status)}</td><td>${time(p.createdAt||p.at)}</td><td><button data-payment="${esc(p.id||p.orderId)}">查看记录</button></td></tr>`);if(!rows)return;$$('[data-payment]').forEach(b=>b.onclick=()=>{const p=rows.find(x=>(x.id||x.orderId)===b.dataset.payment);modal('模拟账务记录',`<p class="notice">此记录为独立体验环境中的模拟账务，不发生真实支付、退款或银行打款。</p>`+details({'记录编号':esc(p.id||p.orderId),'记录类型':esc(p.recordType==='WITHDRAWAL'?'模拟提现':'订单模拟费用'),'关联订单':esc(p.orderId),'乘客账号':esc(accountLabel(p.passengerId,'passengers')),'司机账号':esc(accountLabel(p.driverId,'drivers')),'订单或提现金额':paymentAmount(p),'当前状态':badge(p.status),'累计模拟退款':money(p.refundAmount),'当前轮模拟退款':p.currentRefundAmount==null?'当前服务未提供分项':money(p.currentRefundAmount),'历史轮模拟退款':p.historicalRefundAmount==null?'当前服务未提供分项':money(p.historicalRefundAmount),'支付时间':time(p.paidAt),'退款时间':time(p.refundedAt),'创建时间':time(p.createdAt||p.at),'审核时间':time(p.auditedAt),'处理意见':esc(p.reply||p.reason)})+refundDetails(p)+rawRecord(p),[['关闭','',close],...(p.recordType==='WITHDRAWAL'&&p.status==='PENDING'&&can('wallet.withdrawal.audit')?[['审核通过','primary',()=>confirmAction('模拟提现审核通过','wallet.withdrawal.audit',{driverId:p.driverId,id:p.withdrawalId||p.id,approved:true},'','仅记录模拟审核通过，不发起银行转账。')],['驳回提现','danger',()=>confirmAction('驳回模拟提现','wallet.withdrawal.audit',{driverId:p.driverId,id:p.withdrawalId||p.id,approved:false},'','驳回后返回对应模拟余额。')]]:[])])})}

async function support(){const rows=await queryListing('support','评价投诉与反馈','处理结果保存后两端刷新可查 · 评价随订单保留',['记录 / 来源','关联账号 / 订单','内容','状态','回复','操作'],s=>`<tr><td>${esc(s.id)}<small>${esc(sourceLabel(s.source))}</small></td><td>${esc(accountLabel(s.accountId,s.source?.startsWith('DRIVER')?'drivers':'passengers'))}<small>${esc(s.orderId||'')}</small></td><td title="${esc(s.content)}">${esc((s.content||s.reason||'').slice(0,45))}</td><td>${badge(s.status)}</td><td>${esc((s.reply||'').slice(0,35))}</td><td><button data-support="${esc(s.rowId||s.id)}">查看与处理</button></td></tr>`);if(!rows)return;$$('[data-support]').forEach(b=>b.onclick=()=>{const s=rows.find(x=>(x.rowId||x.id)===b.dataset.support);modal('投诉 / 反馈详情',`${!can('support.reply')?'<p class="notice">当前角色仅可查看，无投诉反馈处理权限。</p>':''}${details({'记录编号':esc(s.id),'来源类型':esc(sourceLabel(s.source)),'处理状态':badge(s.status),'关联账号':esc(s.source==='ORDER_COMPLAINT'?accountLabel(dataset('orders').find(o=>o.id===s.ownerId)?.passengerId,'passengers'):accountLabel(s.ownerId,s.source?.startsWith('DRIVER')?'drivers':'passengers')),'关联订单':esc(s.orderId||(s.source==='ORDER_COMPLAINT'?s.ownerId:null)),'提交时间':time(s.createdAt),'问题类型':esc(s.reason),'评价星级':s.rating?esc(s.rating)+' / 5':'—'})}<h3>${s.source==='EVALUATION'?'评价内容':'问题内容'}</h3><p class="record-text">${esc(s.content||'未填写补充内容')}</p>${supportAttachments(s)}<h3>处理回复</h3><p class="record-text">${esc(s.reply||'尚未回复')}</p>${details({'处理人':esc(s.handledBy||s.repliedBy),'处理时间':time(s.repliedAt||s.handledAt)})}${rawRecord(s)}`,[['关闭','',close],...(s.source==='EVALUATION'||!can('support.reply')?[]:[['处理并回复','primary',()=>confirmAction('处理投诉与反馈','support.reply',{id:s.id,source:s.source,ownerId:s.ownerId})]])]);loadSupportAttachments(s)})}
function configs(){listing('cities','城市与会员配置','城市字段继承：城市 → 大区 → 全国；价格和权益使用两端同源配置',['城市','大区','体系','普通年价','VIP年价','来源','操作'],c=>`<tr><td>${esc(c.name)}<small>${esc(c.id)}</small></td><td>${esc(c.regionName)}</td><td>${badge(c.systemEnabled?'ACTIVE':'DISABLED')}</td><td>${money(c.normalPrice)}</td><td>${money(c.vipPrice)}</td><td>${esc(c.source)}</td><td><button data-city="${esc(c.id)}">配置</button></td></tr>`,{buttons:'<button id="tripDistanceRules">接送距离规则</button><button id="national">全国会员权益</button><button id="regions">大区配置</button><button id="batchConfig">批量城市配置</button><button id="approvalsList">二级审批</button><button id="scheduledList">定时任务</button><button id="batchesList">执行记录</button>'});$$('[data-city]').forEach(b=>b.onclick=()=>configForm('city',b.dataset.city));$('#batchConfig').onclick=batchForm;$('#approvalsList').onclick=()=>workflowList('approvals');$('#scheduledList').onclick=()=>workflowList('scheduled');$('#batchesList').onclick=()=>workflowList('batches');$('#tripDistanceRules').onclick=()=>tripDistanceForm();$('#tripDistanceRules').disabled=!can('config.write');$('#national').onclick=()=>configForm('national','CN');$('#regions').onclick=()=>{const regions=dataset('configs').filter(c=>c.scope==='region');modal('大区配置',regions.length?regions.map(c=>`<p><button data-region="${esc(c.target)}">${esc(c.target==='CENTRAL'?'华中大区':c.target==='SOUTH'?'华南大区':c.target)} · V${c.version}</button></p>`).join(''):'<p>没有可配置的大区记录</p>',[['关闭','',close]]);$$('[data-region]').forEach(b=>b.onclick=()=>configForm('region',b.dataset.region))}}
function configForm(scope,target){const c=dataset('configs').find(x=>x.scope===scope&&x.target===target);if(!c){toast('缺少此配置的数据或读取权限');return}const value=c.draft||c.published,city=dataset('cities').find(x=>x.id===target),national=dataset('configs').find(x=>x.scope==='national')?.published||{},levels=value.levels||{};const effective=(code)=>({...national.levels?.[code],...city?.levels?.find(x=>x.id===code),...levels[code]});const names={login:'登录',online:'上线',bank:'银行卡',nearby:'近距离开单',allArea:'全区域接单',inApp:'应用提醒',floating:'浮窗提醒',voice:'语音播报',wallet:'钱包',withdraw:'模拟提现',password:'交易密码',map:'地图',invoice:'发票入口',instant:'即时订单',reservation:'预约订单',dispatch:'调度',offlineNegotiation:'线下议价',bidding:'竞价',vehicleChange:'更换车辆',vipBadge:'VIP标识',heatmap:'热力图',ranking:'排名',blacklist:'黑名单',support:'客服',update:'版本更新'};modal('配置编辑 · '+(city?.name||(scope==='region'?'大区 '+target:'全国会员权益')),`<p class="muted">发布 V${c.version} / 编辑 R${c.revision}。保存草稿不会影响 App，发布后刷新生效。</p>${scope==='national'?'':`<label><input id="systemEnabled" type="checkbox" ${(value.systemEnabled??city?.systemEnabled??national.systemEnabled??true)?'checked':''}>启用本${scope==='region'?'大区':'城市'}会员等级体系</label>`}<div class="grid2"><label>体验天数<input id="trialDays" type="number" min="1" max="365" value="${value.trialDays??national.trialDays??30}"></label><label>体验完成单上限<input id="trialOrders" type="number" min="1" max="10000" value="${value.trialOrders??national.trialOrders??15}"></label></div>${(scope==='national'?['TRIAL','NORMAL','VIP']:['NORMAL','VIP']).map(code=>{const l=effective(code);return `<section class="panel"><h3>${code==='TRIAL'?'体验司机权益':code==='VIP'?'VIP会员':'普通会员'}</h3>${code==='TRIAL'?'<p class="muted">免费体验，不设置收费价格；有效期按体验天数及完成单量上限判断。</p>':`<label>年度价格（元）<input data-price="${code}" type="number" min="0.01" step="0.01" value="${(Number(l.price||0)/100).toFixed(2)}"></label>`}${scope==='national'?`<div class="grid2">${Object.entries(l.rights||{}).map(([k,v])=>`<label><input type="checkbox" data-right="${code}:${k}" ${v?'checked':''}>${esc(names[k]||k)}</label>`).join('')}</div>`:''}${code==='TRIAL'?'':`<p class="muted">售卖状态：${l.saleEnabled===false?'停售':'开放'}。停售须多级审批，未审批不得改变已有会员权益。</p>`}</section>`}).join('')}<label>修改原因<textarea id="configReason"></textarea></label><details><summary>完整配置（只读）</summary><pre class="code">${pretty(value)}</pre></details>`,[['关闭','',close],['保存草稿','primary',async()=>{const reason=$('#configReason').value.trim();if(reason.length<2)throw Error('请填写修改原因');const patch={trialDays:Number($('#trialDays').value),trialOrders:Number($('#trialOrders').value),levels:{}};if($('#systemEnabled'))patch.systemEnabled=$('#systemEnabled').checked;$$('[data-price]').forEach(el=>{(patch.levels[el.dataset.price]??={}).price=cents(el.value)});$$('[data-right]').forEach(el=>{const [code,key]=el.dataset.right.split(':');((patch.levels[code]??={}).rights??={})[key]=el.checked});await act('config.draft',{scope,target,expectedRevision:c.revision,patch,reason});close();toast('草稿已保存，请重新打开配置核对并发布')}],...(c.draft?[['发布已保存草稿','primary',()=>confirmAction('发布配置草稿','config.publish',{scope,target,expectedRevision:c.revision,subscriptionPolicy:'KEEP_UNTIL_EXPIRY'},'','将提交上次保存的草稿，当前未保存的修改不提交。开启或调价可直接发布；关闭体系会进入二级审批，原配置暂不生效、存量会员权益保留至到期。')]]:[])])}

function users(){listing('users','账号与权限','角色权限由服务器校验，界面隐藏或禁用不替代接口鉴权',['用户名','角色','状态','更新时间','操作'],u=>`<tr><td>${esc(u.username)}</td><td>${esc(roleLabel(u.role))}<small>${esc(u.role)}</small></td><td>${badge(u.enabled===false?'DISABLED':'ACTIVE')}</td><td>${time(u.updatedAt||u.createdAt)}</td><td><button data-user="${esc(u.username)}">编辑权限</button></td></tr>`,{buttons:'<button id="newUser" class="primary">新增账号</button><button id="roles">角色权限表</button>'});$('#newUser').onclick=()=>userForm();$$('[data-user]').forEach(b=>b.onclick=()=>userForm(dataset('users').find(u=>u.username===b.dataset.user)));$('#roles').onclick=()=>modal('角色权限表',roleCards(),[['关闭','',close]])}
function userForm(u={}){const roles=Array.isArray(state.roles)?state.roles.map(r=>typeof r==='string'?r:r.id||r.name):Object.keys(state.roles||{});modal(u.username?'编辑管理账号':'新增管理账号',`<label>用户名<input id="userName" value="${esc(u.username||'')}" ${u.username?'readonly':''}></label><label>密码${u.username?'（留空保持原密码）':''}<input type="password" id="userPassword" autocomplete="new-password"></label><label>角色<select id="userRole">${roles.map(r=>`<option value="${esc(r)}" ${r===u.role?'selected':''}>${esc(roleLabel(r))}（${esc(r)}）</option>`).join('')}</select></label><label><input id="userDisabled" type="checkbox" ${u.enabled===false?'checked':''}>禁用账号</label><label>修改原因<textarea id="userReason"></textarea></label>`,[['关闭','',close],['保存账号','primary',async()=>{const username=$('#userName').value.trim(),password=$('#userPassword').value,reason=$('#userReason').value.trim();if(!username||reason.length<2)throw Error('用户名和至少两个字符的修改原因必填');if(!u.username&&password.length<12)throw Error('密码至少 12 位');await act('user.save',{username,password,role:$('#userRole').value,enabled:!$('#userDisabled').checked,reason,confirmed:true});close();toast('账号已保存')} ]])}
async function audit(){const rows=await queryListing('audit','操作日志','由服务端记录操作者、动作与变更前后数据',['编号','操作者','动作','时间','操作'],a=>`<tr><td>${esc(a.id)}</td><td>${esc(a.actor)}</td><td>${esc(a.action)}</td><td>${time(a.at||a.createdAt||a.created)}</td><td><button data-audit="${esc(a.id)}">变更详情</button></td></tr>`);if(!rows)return;$$('[data-audit]').forEach(b=>b.onclick=()=>modal('操作审计',`<pre class="code">${pretty(rows.find(a=>String(a.id)===b.dataset.audit))}</pre>`,[['关闭','',close]]))}
const navPaths={overview:'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',orders:'M8 4H5v17h14V4h-3M8 2h8v5H8zM8 11h8M8 15h6',drivers:'m3 15 2-8h14l2 8v5h-3v-3H6v3H3zM5 11h14M7 14h1M16 14h1',passengers:'M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8M2 21v-2a6 6 0 0 1 12 0v2M17 4a4 4 0 0 1 0 8M17 15a5 5 0 0 1 5 5',payments:'M3 5h17v16H3zM3 5V3h14M15 11h6v5h-6z',support:'M4 4h16v12H9l-5 5zM8 8h8M8 12h5',configs:'M4 6h16M4 12h16M4 18h16M8 3v6M16 9v6M10 15v6',users:'m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6zM8 12l3 3 5-6',audit:'M8 3h12v18H4V7zM8 3v5H4M8 12h8M8 16h8'};
navPaths.operationDrafts=navPaths.configs;navPaths.statistics=navPaths.overview;navPaths.cancellations=navPaths.orders;navPaths.refunds=navPaths.payments;
for(const name of ['announcementsAdmin','companySupportAdmin','passengerFeaturesAdmin','qualificationExpiryAdmin','heatmapAdmin','tariffsAdmin','serviceScoreRuleAdmin'])navPaths[name]=navPaths.configs;
for(const name of ['orderSettlementsAdmin','serviceFeesAdmin','companySplitAccountsAdmin','companySplitLedgerAdmin','driverWalletsAdmin','driverDailyAdmin','techInvoicesAdmin','couponPoolAdmin'])navPaths[name]=navPaths.payments;
for(const name of ['invoicesAdmin','invoiceReissuesAdmin','serviceScoreEntriesAdmin'])navPaths[name]=navPaths.audit;
navPaths.serviceScoreStatsAdmin=navPaths.drivers;navPaths.newUserCouponAdmin=navPaths.passengers;navPaths.referralsAdmin=navPaths.passengers;
navPaths.pointsMallAdmin='M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z';navPaths.couponCreateAdmin=navPaths.pointsMallAdmin;
const navGroups=[['工作台',['overview','statistics']],['业务运营',['orders','orderSettlementsAdmin','cancellations','drivers','passengers','payments','refunds','support']],['运价与分账',['tariffsAdmin','serviceFeesAdmin','companySplitAccountsAdmin','companySplitLedgerAdmin']],['平台运维',['announcementsAdmin','companySupportAdmin','passengerFeaturesAdmin','heatmapAdmin']],['司机与服务分',['driverWalletsAdmin','driverDailyAdmin','qualificationExpiryAdmin','serviceScoreRuleAdmin','serviceScoreStatsAdmin','serviceScoreEntriesAdmin']],['开票管理',['invoicesAdmin','invoiceReissuesAdmin','techInvoicesAdmin']],['营销管理',['couponPoolAdmin','couponCreateAdmin','newUserCouponAdmin','referralsAdmin','pointsMallAdmin']],['配置与治理',['configs','operationDrafts','users','audit']]];
$('#nav').innerHTML=navGroups.map(([title,keys])=>`<div class="nav-section">${title}</div>`+keys.map(key=>{const item=pages.find(p=>p[0]===key);return `<button data-page="${key}"><span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"><path d="${navPaths[key]}"></path></svg></span>${esc(item[2])}</button>`}).join('')).join('');$$('[data-page]').forEach(b=>b.onclick=()=>navigate(b.dataset.page));$('#closeModal').onclick=close;$('#refreshBtn').onclick=refresh;$('#logoutBtn').onclick=async()=>{try{await api('logout',{})}catch(e){toast(e.message)}session='';state={};queryViews.clear();pendingRequests.clear();queryTicket++;$('#password').value='';$('#app').hidden=true;$('#login').hidden=false;close()};$('#loginForm').onsubmit=async e=>{e.preventDefault();$('#loginBtn').disabled=true;$('#loginError').textContent='';try{const d=await api('login',{username:$('#username').value.trim(),password:$('#password').value});session=d.session;$('#password').value='';state=await api('state');$('#login').hidden=true;$('#app').hidden=false;render();window.scrollTo(0,0);$('#sync').textContent='登录成功 · 点击刷新获取两端最新数据'}catch(e){session='';$('#loginError').textContent=e.message}finally{$('#loginBtn').disabled=false}};

function applyPermissions(){const rules={'#export':'export','#cancelOrder':'order.cancel','#adjustFare':'order.adjustFare','#refund':'order.refund','[data-cert]':'certificate.audit','#newUser':'users.manage','[data-user]':'users.manage','[data-city]':'config.write','#national':'config.write','#regions':'config.write','#batchConfig':'config.write'};for(const [selector,permission] of Object.entries(rules))$$(selector).forEach(b=>{b.disabled=!can(permission);if(b.disabled)b.title='当前角色无此操作权限'});if(page==='users'&&!can('users.manage'))$('#content').innerHTML=head('账号与权限','只有超级管理员可以查看和管理账号')+'<div class="panel empty">权限不足：当前账号无管理账号权限。</div>';}

function requestUUID(){return crypto.randomUUID?crypto.randomUUID():Array.from(crypto.getRandomValues(new Uint8Array(16)),b=>b.toString(16).padStart(2,'0')).join('')}

function workflowList(kind){const title={approvals:'配置二级审批',scheduled:'定时发布任务',batches:'批次执行记录'}[kind];if(!Array.isArray(state[kind])){modal(title,'<p class="notice">当前服务版本尚未提供此列表，请等待后台管理扩展部署后刷新。不能据此判断没有待处理记录。</p>',[['关闭','',close]]);return}const rows=dataset(kind);let body=kind==='scheduled'?'<p class="notice">定时任务在服务收到请求时补执行。为保持低费用不新增常驻调度，无访问期间不保证准点执行。</p>':kind==='approvals'?'<p class="notice">申请人与两名审批人必须不同，均使用实际登录账号。只有第二级通过后才改变生效配置。</p>':'';body+=rows.length?table(['编号','申请人 / 目标','状态','时间','操作'],rows.map(r=>`<tr><td>${esc(r.id)}</td><td>${esc(r.requestedBy||r.actor||r.policy)}<small>${esc((r.changes||[]).map(x=>x.target).join('、'))}</small></td><td>${badge(r.status)}</td><td>${time(r.effectiveAt||r.createdAt)}</td><td><button data-workflow="${esc(r.id)}">查看详情</button></td></tr>`)):'<div class="empty">暂无此类记录</div>';modal(title,body,[['关闭','',close]]);$$('[data-workflow]').forEach(b=>b.onclick=()=>workflowDetail(kind,rows.find(r=>r.id===b.dataset.workflow)))}
function workflowDetail(kind,r){const actions=[['返回列表','',()=>workflowList(kind)]];const actor='ADMIN:'+state.user?.username;const eligible=can('config.write')&&r.requestedBy!==actor&&!(r.decisions||[]).some(d=>d.actor===actor);if(kind==='approvals'&&['WAIT_FIRST_APPROVAL','WAIT_SECOND_APPROVAL'].includes(r.status)&&eligible){for(const [name,decision] of [['驳回申请','REJECT'],['通过本级审批','APPROVE']])actions.push([name,decision==='REJECT'?'danger':'primary',()=>confirmAction(name,'approval.decide',{id:r.id,decision},'','请核对变更及存量权益处理方案。服务器将校验申请人、两级审批人不同，并校验配置版本未被其他操作修改。')])}if(kind==='scheduled'&&r.status==='PENDING'&&can('config.write'))actions.push(['取消任务','danger',()=>confirmAction('取消定时发布','schedule.cancel',{id:r.id},'','仅取消尚未执行的定时任务，已发布配置保持当前版本。')]);modal({approvals:'审批详情',scheduled:'定时任务详情',batches:'批次逐城结果'}[kind]+' · '+r.id,`${kind==='approvals'&&!eligible?'<p class="notice">当前账号无配置权限，或是申请人 / 已参与审批，不能处理本级审批。请由另一位有配置权限的管理员登录处理。</p>':''}${details({'状态':badge(r.status),'原因':esc(r.reason),'存量权益方案':esc(r.subscriptionPolicy||'—')})}<pre class="code">${pretty(r)}</pre>`,actions)}
function batchForm(){let preview=null,executionKey=requestUUID();const cities=dataset('cities');modal('批量城市配置',`<p class="notice">先预览逐城影响，再确认执行。覆盖只作用于选择的字段；价格不会改变既有会员购买快照。</p><h3>目标城市</h3><div class="grid2">${cities.map(c=>`<label><input type="checkbox" data-batch-city="${esc(c.id)}">${esc(c.name)} · ${esc(c.regionName)}</label>`).join('')}</div><label>覆盖策略<select id="batchPolicy"><option value="SKIP_EXISTING">跳过已有已发布覆盖的城市</option><option value="OVERWRITE_ALL">覆盖所选城市的指定字段</option><option value="SWITCH_ONLY">只修改售卖开关，保留价格</option></select></label><div class="grid2"><label>普通年度价格（元）<input id="batchNormal" type="number" min="0.01" step="0.01"></label><label>VIP年度价格（元）<input id="batchVip" type="number" min="0.01" step="0.01"></label><label>普通等级售卖<select id="batchNormalSale"><option value="KEEP">保持不变</option><option value="true">开放</option><option value="false">停售，需二级审批</option></select></label><label>VIP等级售卖<select id="batchVipSale"><option value="KEEP">保持不变</option><option value="true">开放</option><option value="false">停售，需二级审批</option></select></label></div><p class="muted">价格留空保持不变。仅开关策略不读取价格。停售整批进入二级审批，保留存量会员权益至到期。</p><label>指定生效时间（空为立即）<input id="batchEffective" type="datetime-local"></label><label>下发原因<textarea id="batchReason" maxlength="500"></textarea></label><label><input type="checkbox" id="batchConfirm">我确认所选城市、字段及影响；涉及停售时提交审批并保留存量权益至到期</label><div id="batchPreview"></div>`,[['关闭','',close],['预览逐城影响','',async()=>{const payload=readBatch();const result=await act('batch.preview',payload);preview={payload,result:result.preview};executionKey=requestUUID();const p=preview.result;$('#batchPreview').innerHTML=`<p class="notice">目标 ${p.uniqueCityCount} 城，实际变更 ${p.affectedCities} 城。${p.requiresApproval?'涉及停售，整批转二级审批。':'不涉及停售审批。'}下列在册示例人数不用于实际经营统计。</p>`+table(['城市','原有覆盖','处理结果','将重新开放'],p.results.map(r=>`<tr><td>${esc(r.cityName)}</td><td>${r.customized?'是':'否'}</td><td>${badge(r.status)}</td><td>${r.reopening?'是':'否'}</td></tr>`));toast('预览完成，核对后点击确认执行')}],['确认执行','primary',async()=>{if(!preview)throw Error('请先预览影响');const payload=readBatch();if(JSON.stringify(payload)!==JSON.stringify(preview.payload))throw Error('字段已变化，请重新预览');if(!preview.result.affectedCities)throw Error('目标均已跳过，没有可以下发的变更');const result=await act('batch.execute',{...payload,expectedRevisions:Object.fromEntries(preview.result.changes.map(c=>[c.target,c.revision])),idempotencyKey:executionKey});close();toast(result.batch.status==='WAIT_APPROVAL'?'批次已提交二级审批，生效配置未改变':result.batch.status==='SCHEDULED'?'批次已排入定时任务，尚未生效':'批次已执行，请查看逐城执行结果')}]]);$('#batchPolicy').onchange=()=>{const off=$('#batchPolicy').value==='SWITCH_ONLY';$('#batchNormal').disabled=off;$('#batchVip').disabled=off;preview=null;$('#batchPreview').textContent='策略已变化，请重新预览'};
function readBatch(){const reason=$('#batchReason').value.trim();if(reason.length<2)throw Error('请填写至少两字下发原因');if(!$('#batchConfirm').checked)throw Error('请勾选影响确认');const payload={cities:$$('[data-batch-city]:checked').map(e=>e.dataset.batchCity),policy:$('#batchPolicy').value,patch:{levels:{}},reason,confirmed:true,subscriptionPolicy:'KEEP_UNTIL_EXPIRY'};if(!payload.cities.length)throw Error('请至少选择一个目标城市');for(const [code,id] of [['NORMAL','Normal'],['VIP','Vip']]){const value={};if(payload.policy!=='SWITCH_ONLY'&&$('#batch'+id).value)value.price=cents($('#batch'+id).value);const sale=$('#batch'+id+'Sale').value;if(sale!=='KEEP')value.saleEnabled=sale==='true';if(Object.keys(value).length)payload.patch.levels[code]=value}if(!Object.keys(payload.patch.levels).length)throw Error('请至少填写一个价格或修改一项售卖开关');if($('#batchEffective').value){const date=new Date($('#batchEffective').value);if(!Number.isFinite(date.getTime())||date<=new Date())throw Error('生效时间须晚于当前时间');payload.effectiveAt=date.toISOString();if(Object.values(payload.patch.levels).some(l=>l.saleEnabled===false))throw Error('停售须二级审批后立即生效，请清除指定生效时间')}return payload}}

function kindLabel(value){return {STANDARD:'即时用车',RESERVED:'预约用车',BID:'竞价用车',NEGOTIATED:'线下议价'}[value]||label(value)}
function sourceLabel(value){return {ORDER_COMPLAINT:'乘客订单投诉',PASSENGER_FEEDBACK:'乘客意见反馈',DRIVER_FEEDBACK:'司机意见反馈',DRIVER_COMPLAINT:'司机投诉',EVALUATION:'乘客订单评价'}[value]||value||'—'}
function cityLabel(id){return dataset('cities').find(c=>c.id===id)?.name||id||'—'}
function accountLabel(id,key){if(!id)return '—';const a=dataset(key).find(x=>x.id===id);return a?(a.name||a.profile?.name||a.mobile||id)+' · '+id:id}
function rawRecord(value){return '<details><summary>原始记录（诊断用）</summary><pre class="code">'+pretty(value)+'</pre></details>'}

function eventRow(e){const eventNames={PASSENGER_NEGOTIATION_CLAIMED:'乘客确认配对议价订单',PASSENGER_PRICE_DISPUTE:'乘客提出价格异议',DRIVER_REQUOTE:'司机重新报价',PASSENGER_CREATED:'乘客创建订单',PASSENGER_SELECTDRIVER:'乘客选择司机',PASSENGER_PAY:'乘客发起模拟支付',PASSENGER_CANCEL:'乘客取消订单',PASSENGER_UPDATE:'乘客修改订单',PASSENGER_CONTINUE:'乘客决定是否继续叫车',PASSENGER_EVALUATE:'乘客提交评价',PASSENGER_COMPLAINT:'乘客提交投诉',PASSENGER_BLOCKDRIVER:'乘客屏蔽司机',PASSENGER_UNBLOCKDRIVER:'乘客取消屏蔽司机',PASSENGER_REMARK:'乘客更新备注',DRIVER_ACCEPT:'司机接单',DRIVER_BID:'司机提交报价',DRIVER_ADVANCE:'司机推进行程',DRIVER_WAYPOINT:'司机确认途经点',DRIVER_CANCEL:'司机取消订单',DRIVER_COMPLAINT:'司机提交投诉',ADMIN_CANCEL:'管理员取消订单',ADMIN_ADJUSTFARE:'管理员调整体验费用',ADMIN_REFUND:'管理员模拟退款',ADMIN_COMPLAINT_REPLIED:'管理员回复订单投诉'};const before=e.fromStage??e.from,after=e.toStage??e.to;const transition=(before!=null||after!=null)?`<small>状态：${before==null?'未记录':esc(label(before))} → ${after==null?'未记录':esc(label(after))}</small>`:'';return `<div title="${esc(e.type)}">${esc(eventNames[e.type]||'业务事件')}<small>${time(e.at||e.createdAt)}${e.actor?' · 操作人：'+esc(e.actor):''}</small>${transition}${e.reason?'<small>原因：'+esc(e.reason)+'</small>':''}</div>`}
function refundDetails(p){if(!Array.isArray(p.refundRecords))return '';return '<h3>模拟退款记录</h3><p class="muted">累计金额包含当前轮及继续叫车前的历史轮退款。每轮分别记录，不按金额去重；不发生真实资金交易。</p>'+(p.refundRecords.length?table(['记录归属','模拟退款金额','关联司机','退款时间'],p.refundRecords.map(r=>`<tr><td>${r.source==='CURRENT'?'当前轮退款':r.source==='HISTORY'?'历史轮退款':'退款记录'}</td><td>${money(r.amount)}</td><td>${esc(accountLabel(r.driverId,'drivers'))}</td><td>${time(r.at)}</td></tr>`)):'<p class="muted">暂无模拟退款记录</p>')}

function isConnectionError(error){return error?.name==='TypeError'||error?.name==='TimeoutError'||error?.name==='AbortError'}
function connectionError(error){if(error?.name==='TimeoutError'||error?.name==='AbortError')return Error('后台响应超时，请检查本机入口和网络后重试；若刚提交过操作，请先刷新核对结果，重试时保留原操作内容。');if(error?.name==='TypeError')return Error('无法连接后台，请检查本机入口是否运行及网络连接后点击刷新重试；若刚提交过操作，请先核对结果。');return error}
async function backendFetch(url,options={}){try{return await fetch(url,{...options,signal:AbortSignal.timeout(25000)})}catch(error){throw connectionError(error)}}

function quotationDetails(o){if(o.kind==='NEGOTIATED'){const quotes=Array.isArray(o.quoteHistory)?o.quoteHistory:[],disputes=Array.isArray(o.priceDisputes)?o.priceDisputes:[];return '<h3 style="margin-top:28px">线下议价记录</h3>'+details({'当前报价版本':o.quoteVersion==null?'未记录':'V'+esc(o.quoteVersion),'当前议价状态':badge(o.negotiationStatus),'当前报价金额':orderAmount(o)})+(quotes.length?table(['报价版本','报价金额','报价方','报价时间'],quotes.map(q=>`<tr><td>V${esc(q.version)}</td><td>${money(q.amount)}</td><td>${esc(q.actor==='DRIVER'?'司机':q.actor==='PASSENGER'?'乘客':q.actor==='ADMIN'?'管理员':q.actor)}</td><td>${time(q.createdAt)}</td></tr>`)):'<p class="muted">服务未提供历史报价记录，当前报价以订单金额为准。</p>')+'<h3 style="margin-top:24px">乘客价格异议</h3>'+(disputes.length?disputes.map(d=>`<section class="panel"><h3>针对报价 V${esc(d.quoteVersion)}</h3><p class="record-text">${esc(d.content)}</p><small>提出时间：${time(d.createdAt)}</small></section>`).join(''):'<p class="muted">暂无价格异议</p>')}return '<h3 style="margin-top:28px">司机报价</h3>'+((o.bids||[]).length?table(['司机','车辆','报价','时间'],o.bids.map(b=>`<tr><td>${esc(b.name||b.driverId)}</td><td>${esc(b.plate)} / ${esc(b.vehicleModel)}</td><td>${money(b.amount)}</td><td>${time(b.createdAt)}</td></tr>`)):'<p class="muted">暂无报价记录</p>')}

function roleLabel(role){return {SUPER_ADMIN:'超级管理员',OPERATOR:'运营人员',FINANCE:'财务人员',VIEWER:'只读观察员',CONFIG_ADMIN:'配置管理员'}[role]||role||'未登录'}
function roleCards(){const permissionNames={'orders.read':'查看共享订单','drivers.read':'查看司机及车辆资料','passengers.read':'查看乘客资料','payments.read':'查看模拟账务','support.read':'查看评价、投诉与反馈','config.read':'查看城市、会员及审批配置','statistics.read':'查看经营统计','audit.read':'查看操作日志','users.manage':'管理后台账号与角色','order.cancel':'人工取消未出发订单','order.adjustFare':'调整待支付订单费用','order.refund':'执行模拟全额退款','support.reply':'处理并回复投诉反馈','certificate.audit':'审核司机资质','wallet.withdrawal.audit':'审核模拟提现','config.write':'修改配置及处理审批任务','export':'导出数据'};const roles=Array.isArray(state.roles)?state.roles:Object.entries(state.roles||{}).map(([id,value])=>({id,permissions:Array.isArray(value)?value:value.permissions||[]}));return '<p class="muted">以下权限以服务器当前返回的角色定义为准。角色名称仅用于说明，不改变接口鉴权。</p>'+roles.map(r=>`<section class="panel"><h3>${esc(roleLabel(r.id))} <small>${esc(r.id)}</small></h3><div class="grid2">${(r.permissions||[]).map(permission=>`<p title="${esc(permission)}">✓ ${esc(permissionNames[permission]||permission)}</p>`).join('')||'<p class="muted">服务器未返回该角色的权限明细</p>'}</div></section>`).join('')+rawRecord(state.roles)}

function memberLevelLabel(value){return {TRIAL:'体验司机',NORMAL:'普通司机',VIP:'VIP司机',EXPIRED:'已到期'}[value]||value||'未记录'}

function orderAmount(order){return order?.destinationPending===true?'费用待定（目的地待补充）':money(order?.fare)}
function paymentAmount(record){if(record.type==='模拟提现'||record.recordType==='WITHDRAWAL')return money(record.amount??record.fare);const order=record.orderId?dataset('orders').find(o=>o.id===record.orderId):null;return record.destinationPending===true||order?.destinationPending===true?'费用待定（目的地待补充）':money(record.amount??record.fare)}

function distanceReasonLabel(reason) {
  if (reason==='OUTSIDE_DISTANCE') return '实际距离超过本次允许范围';
  if (reason==='CONSENT_EXPIRED') return '乘客确认已超过有效期';
  if (reason==='ORDER_CONTEXT_CHANGED') return '订单阶段、司机或目的地已变化';
  if (reason==='DESTINATION_INVALID_OR_SYNTHETIC') return '目的地坐标缺失、无效或为演示坐标';
  const prefix=reason?.startsWith('DRIVER_LOCATION_')?'司机':reason?.startsWith('PASSENGER_LOCATION_')?'乘客':null;
  const suffix=prefix?reason.replace(/^(DRIVER|PASSENGER)_LOCATION_/, ''):'';
  const problems={MISSING:'尚未上传当前定位',STALE:'定位已过旧',POOR_ACCURACY:'定位精度不足',INVALID:'定位数据无效'};
  return prefix&&problems[suffix]?prefix+problems[suffix]:'其他定位核验原因，请展开快照查看';
}

function distanceConsentDetails(o) {
  const rows = [o.distanceConsent, ...(o.distanceConsentHistory || [])].filter(Boolean);
  const seen = new Set();
  const names = {PENDING:'等待乘客同意',APPROVED:'乘客已同意',REJECTED:'乘客已拒绝',EXPIRED:'确认已过期',INVALIDATED:'确认已失效',SUPERSEDED:'已被新请求替代'};
  return '<h3 style="margin-top:24px">接送距离与乘客确认</h3><p class="muted">距离由服务端按定位计算；超过阈值或定位缺失、过旧时须由本订单乘客同意。未同意前不推进订单。</p>' + (rows.filter(x=>!seen.has(x.id)&&seen.add(x.id)).map(x=>'<section class="panel">'+details({'确认编号':esc(x.id),'确认事项':x.type==='PICKUP'?'接到乘客':'送达乘客','处理状态':esc(names[x.status]||x.status),'实测直线距离':Number.isFinite(x.distanceMeters)?esc(Math.round(x.distanceMeters))+' 米':'定位不足，未计算','本次阈值':esc(x.thresholdMeters)+' 米','触发原因':esc(distanceReasonLabel(x.reason)),'创建时间':time(x.createdAt),'有效期至':time(x.expiresAt),'原订单阶段':badge(x.expectedStage),'同意后阶段':badge(x.nextStage)})+'<details><summary>定位及规则快照</summary><pre class="code">'+pretty(x)+'</pre></details></section>').join('') || '<p class="muted">尚无需要乘客确认的接送请求。</p>');
}

function tripDistanceForm(initialScope='national', initialTarget='CN') {
  if (!can('config.write')) { toast('权限不足：当前账号无配置修改权限'); return; }
  const configs = dataset('configs'), cities = dataset('cities');
  const titleFor = c => c.scope==='national'?'全国默认':c.scope==='city'?'城市 · '+(cities.find(x=>x.id===c.target)?.name||c.target):'大区 · '+(c.target==='CENTRAL'?'华中大区':c.target==='SOUTH'?'华南大区':c.target);
  const initial = configs.findIndex(c=>c.scope===initialScope&&c.target===initialTarget);
  let current;
  const fieldRows = [['pickupMaxMeters','接到乘客免确认距离'],['dropoffMaxMeters','送达乘客免确认距离']];
  const selectedConfig = () => configs[Number($('#tdTarget').value)];
  modal('接送距离规则',`<p class="notice">接客与送达分别设置。默认 300 米，支持 50–5000 米整数；超距或定位不可用时，只有该订单乘客同意才能推进。此处不会修改会员价格或权益。</p><label>配置范围<select id="tdTarget">${configs.map((c,i)=>`<option value="${i}" ${i===Math.max(0,initial)?'selected':''}>${esc(titleFor(c))}</option>`).join('')}</select></label><div id="tdFields"></div><label>修改原因<textarea id="tdReason" minlength="2" maxlength="500" placeholder="请说明本次调整原因"></textarea></label><details><summary>核对当前完整草稿与已发布配置</summary><pre id="tdRaw" class="code"></pre></details>`,[
    ['关闭','',close],
    ['保存距离草稿','primary',async()=>{
      const c=selectedConfig(), reason=$('#tdReason').value.trim();
      if(reason.length<2||reason.length>500)throw Error('修改原因须为2–500个字符');
      const tripDistance={}, removePaths=[];
      for(const [key,name] of fieldRows){
        if(c.scope!=='national'&&$('#tdInherit-'+key).checked){removePaths.push('tripDistance.'+key);continue;}
        const raw=$('#td-'+key).value;
        if(!/^\d+$/.test(raw)||Number(raw)<50||Number(raw)>5000)throw Error(name+'须为50–5000米整数');
        tripDistance[key]=Number(raw);
      }
      await act('config.draft',{scope:c.scope,target:c.target,expectedRevision:c.revision,patch:{tripDistance},removePaths,reason});
      close();toast('距离草稿已保存，尚未生效；请重新打开核对完整草稿后发布');
    }],
    ['发布已保存完整草稿','primary',()=>{
      const c=selectedConfig();if(!c.draft)throw Error('此范围没有已保存草稿，请先保存并重新打开');
      confirmAction('发布接送距离所在配置','config.publish',{scope:c.scope,target:c.target,expectedRevision:c.revision,subscriptionPolicy:'KEEP_UNTIL_EXPIRY'},'<details open><summary>即将发布的完整草稿</summary><pre class="code">'+pretty(c.draft)+'</pre></details>','将发布此范围已保存的整份草稿（可能包含此前其他修改），当前表单未保存的输入不提交。请核对全部字段。生效后新接送请求使用新阈值，既有待确认请求继续使用其创建时快照。');
    }]
  ]);
  function draw() {
    const c=selectedConfig();current=c;
    const value=c.draft||c.published||{}, own=value.tripDistance||{};
    const national=configs.find(x=>x.scope==='national')?.published?.tripDistance||{};
    const city=cities.find(x=>x.id===c.target), region=configs.find(x=>x.scope==='region'&&x.target===city?.region)?.published?.tripDistance||{};
    const inherited=c.scope==='city'?{pickupMaxMeters:300,dropoffMaxMeters:300,...national,...region}:{pickupMaxMeters:300,dropoffMaxMeters:300,...national};
    const effective=city?.tripDistanceRules;
    $('#tdFields').innerHTML='<p class="muted">'+esc(titleFor(c))+' · 已发布 V'+esc(c.version)+' / 编辑 R'+esc(c.revision)+(c.draft?' · 存在未发布草稿':'')+'</p><div class="grid2">'+fieldRows.map(([key,name])=>{
      const inherit=c.scope!=='national'&&!Object.prototype.hasOwnProperty.call(own,key), number=own[key]??inherited[key];
      return `<section><label>${esc(name)}（米）<input id="td-${key}" type="number" min="50" max="5000" step="1" value="${esc(number)}" ${inherit?'disabled':''}></label>${c.scope==='national'?'':`<label><input type="checkbox" id="tdInherit-${key}" ${inherit?'checked':''}>沿用上级规则（当前 ${esc(inherited[key])} 米）</label>`}${effective?`<small>本城市当前已发布生效值：${esc(effective[key])} 米</small>`:''}</section>`;
    }).join('')+'</div><p class="muted">定位有效期120秒，精度要求100米以内；乘客确认有效期180秒。直线距离不代表道路里程，系统未提供GPS防伪认证。</p>';
    $('#tdRaw').textContent=JSON.stringify({published:c.published,draft:c.draft},null,2);
    for(const [key] of fieldRows){const checkbox=$('#tdInherit-'+key);if(checkbox)checkbox.onchange=()=>{$('#td-'+key).disabled=checkbox.checked;};}
  }
  $('#tdTarget').onchange=draw; draw();
}

let opsCategory = '', opsModule = '', opsShowArchived = false;
const opsScopeNames = {NATIONAL:'全国',REGION:'大区',CITY:'城市',CUSTOM:'公司或其他对象'};
function opsData(){return state.operationDrafts || {catalog:[],drafts:[],records:[],draftOnly:true};}
function opsState(value){return `<span class="badge ${value==='ARCHIVED'?'':'warn'}">${value==='ARCHIVED'?'已归档':value==='RECORDED'?'人工记录':'配置草稿'}</span>`;}
function operationDrafts(){
  if(!can('config.read')){$('#content').innerHTML=head('业务配置与记录','')+'<section class="panel">当前账号无业务配置查看权限。</section>';return;}
  const data=opsData(), catalog=Array.isArray(data.catalog)?data.catalog:[];
  if(!catalog.length){$('#content').innerHTML=head('业务配置与记录','服务尚未提供配置草稿，请稍后刷新。');return;}
  const categories=[...new Set(catalog.map(x=>x.category))];
  if(opsCategory&&!categories.includes(opsCategory))opsCategory='';
  const available=catalog.filter(x=>!opsCategory||x.category===opsCategory);
  if(!available.some(x=>x.id===opsModule))opsModule=available[0]?.id||'';
  const module=catalog.find(x=>x.id===opsModule);
  const visible=rows=>(rows||[]).filter(x=>x.module===opsModule&&(opsShowArchived||x.status!=='ARCHIVED'));
  const drafts=visible(data.drafts), records=visible(data.records);
  $('#content').innerHTML=head('业务配置与记录',esc(data.policyLabel||'配置保存为草稿，人工记录不执行扣款、扣分或发奖。'))+
    `<section class="panel"><div class="grid2"><label>业务分类<select id="opsCategory"><option value="">全部分类</option>${categories.map(c=>`<option value="${esc(c)}" ${c===opsCategory?'selected':''}>${esc(c)}</option>`).join('')}</select></label><label>功能模块<select id="opsModule">${available.map(x=>`<option value="${esc(x.id)}" ${x.id===opsModule?'selected':''}>${esc(x.name)}</option>`).join('')}</select></label></div><p class="notice">${esc(module?.notice||'本模块当前提供配置草稿和人工记录；规则确认后再安排生效能力。')}</p><label><input type="checkbox" id="opsArchived" ${opsShowArchived?'checked':''}>显示已归档内容</label></section>`+
    `<section class="panel"><div class="toolbar"><h2>配置草稿 · ${esc(module?.name)}</h2><button id="opsNewDraft" ${can('config.write')?'':'disabled'}>新建草稿</button></div>${drafts.length?table(['名称','适用范围','状态 / 版本','更新时间','操作'],drafts.map(d=>`<tr><td>${esc(d.name)}</td><td>${esc(opsScopeNames[d.scope]||d.scope)} · ${esc(d.target)}</td><td>${opsState(d.status)} · V${esc(d.version)}</td><td>${time(d.updatedAt)}</td><td><button data-ops-draft="${esc(d.id)}">${can('config.write')&&d.status!=='ARCHIVED'?'查看与编辑':'查看'}</button></td></tr>`)):'<div class="empty">暂无配置草稿</div>'}</section>`+
    `<section class="panel"><div class="toolbar"><h2>人工记录</h2><button id="opsNewRecord" ${can('config.write')?'':'disabled'}>新增记录</button></div><p class="muted">人工记录用于留存处理过程，不代表资金到账、正式开票或处罚执行。</p>${records.length?table(['记录标题','来源 / 状态','发生 / 保存时间','关联订单','操作'],records.map(r=>`<tr><td>${esc(r.title)}</td><td>人工录入 · ${opsState(r.status)}</td><td>${r.occurredAt?time(r.occurredAt):'发生时间未填写'}<small>保存于 ${time(r.createdAt)}</small></td><td>${esc(r.orderId||'未关联')}</td><td><button data-ops-record="${esc(r.id)}">查看记录</button></td></tr>`)):'<div class="empty">暂无人工记录</div>'}</section>`;
  $('#opsCategory').onchange=e=>{opsCategory=e.target.value;opsModule='';operationDrafts();};
  $('#opsModule').onchange=e=>{opsModule=e.target.value;operationDrafts();};
  $('#opsArchived').onchange=e=>{opsShowArchived=e.target.checked;operationDrafts();};
  $('#opsNewDraft').onclick=()=>opsDraftForm();$('#opsNewRecord').onclick=()=>opsRecordForm();
  $$('[data-ops-draft]').forEach(b=>b.onclick=()=>opsDraftForm((data.drafts||[]).find(x=>x.id===b.dataset.opsDraft)));
  $$('[data-ops-record]').forEach(b=>b.onclick=()=>opsRecordDetail((data.records||[]).find(x=>x.id===b.dataset.opsRecord)));
}
function opsDraftForm(draft){
  const editable=can('config.write')&&draft?.status!=='ARCHIVED';
  const module=opsData().catalog.find(x=>x.id===(draft?.module||opsModule));
  if(!module)return;
  if(!editable){
    modal('配置草稿 · '+(draft?.name||''),details({'模块':esc(module.name),'名称':esc(draft?.name),'范围':esc(opsScopeNames[draft?.scope]||draft?.scope),'适用对象':esc(draft?.target),'状态':opsState(draft?.status),'版本':esc(draft?.version),'规则说明':esc(draft?.description),'更新人':esc(draft?.updatedBy),'更新时间':time(draft?.updatedAt)})+table(['参数','草拟值','单位 / 说明'],(draft?.parameters||[]).map(p=>`<tr><td>${esc(p.name)}</td><td>${esc(p.value)}</td><td>${esc(p.unit)}</td></tr>`)),[['关闭','',close]]);return;
  }
  const parameters=(draft?.parameters||[]).map(p=>({...p}));
  const requestId=requestUUID();
  modal(draft?'编辑配置草稿':'新建配置草稿',`<p class="notice">${esc(module.name)} · 仅保存草稿，当前订单、金额、服务分与积分不因此变化。</p><label>草稿名称<input id="opName" maxlength="120" value="${esc(draft?.name||'')}"></label><div class="grid2"><label>适用范围<select id="opScope">${Object.entries(opsScopeNames).map(([id,name])=>`<option value="${id}" ${id===(draft?.scope||'NATIONAL')?'selected':''}>${name}</option>`).join('')}</select></label><label>适用对象 / 区域名称<input id="opTarget" maxlength="120" value="${esc(draft?.target||'CN')}"></label></div><label>规则说明<textarea id="opDescription" maxlength="2000">${esc(draft?.description||'')}</textarea></label><h3>草拟参数</h3><p class="muted">可填写比例、金额、次数或适用条件；参数只作草稿保存。</p><div id="opParameters"></div><button id="opAddParameter">添加参数</button>`,[['返回','',close],...(draft?[['归档草稿','danger',()=>opsArchive('draft',draft)]]:[]),['保存草稿','primary',async()=>{
    const payload={module:module.id,name:$('#opName').value.trim(),scope:$('#opScope').value,target:$('#opTarget').value.trim(),description:$('#opDescription').value.trim(),parameters:parameters.map(p=>({name:p.name.trim(),value:p.value.trim(),unit:p.unit.trim()}))};
    if(!payload.name||!payload.target)throw Error('请填写草稿名称和适用对象');
    if(draft){payload.id=draft.id;payload.expectedVersion=draft.version;}
    await act('ops.draft.save',payload,requestId);close();toast('配置草稿已保存，尚未生效');
  }]]);
  function renderTarget(){
    const scope=$('#opScope').value, current=$('#opTarget').value;
    if(scope==='CUSTOM'){$('#opTarget').outerHTML=`<input id="opTarget" maxlength="120" value="${esc(current)}">`;return;}
    const choices=scope==='NATIONAL'?[{id:'CN',name:'全国'}]:scope==='CITY'?(state.cities||[]).map(c=>({id:c.id,name:c.name})):(state.configs||[]).filter(c=>c.scope==='region').map(c=>({id:c.target,name:({CENTRAL:'华中大区',SOUTH:'华南大区'})[c.target]||c.name||c.target}));
    $('#opTarget').outerHTML=`<select id="opTarget">${choices.map(c=>`<option value="${esc(c.id)}" ${c.id===current?'selected':''}>${esc(c.name)}</option>`).join('')}</select>`;
  }
  $('#opScope').onchange=renderTarget;renderTarget();
  function renderParameters(){
    $('#opParameters').innerHTML=parameters.map((p,i)=>`<div class="panel"><div class="grid2"><label>参数名称<input data-op-param="${i}" data-field="name" maxlength="80" value="${esc(p.name)}"></label><label>草拟值<input data-op-param="${i}" data-field="value" maxlength="500" value="${esc(p.value)}"></label></div><label>单位 / 说明<input data-op-param="${i}" data-field="unit" maxlength="40" value="${esc(p.unit)}"></label><button data-op-remove="${i}">移除此参数</button></div>`).join('');
    $$('[data-op-param]').forEach(x=>x.oninput=()=>{parameters[Number(x.dataset.opParam)][x.dataset.field]=x.value;});
    $$('[data-op-remove]').forEach(x=>x.onclick=()=>{parameters.splice(Number(x.dataset.opRemove),1);renderParameters();});
  }
  $('#opAddParameter').onclick=()=>{if(parameters.length>=40){toast('最多保存40个草拟参数');return;}parameters.push({name:'',value:'',unit:''});renderParameters();};
  renderParameters();
}
function opsArchive(kind,item){
  if(!can('config.write'))throw Error('当前账号无修改权限');
  const requestId=requestUUID();
  modal('归档'+(kind==='draft'?'草稿':'人工记录'),`<p>确认归档“${esc(item.name||item.title)}”？归档后仍保留历史内容和审计记录。</p>`,[['返回','',close],['确认归档','primary',async()=>{await act('ops.'+kind+'.archive',{id:item.id,expectedVersion:item.version},requestId);close();toast('已归档，可勾选显示已归档内容查看');}]]);
}
function opsRecordForm(){
  if(!can('config.write'))throw Error('当前账号无修改权限');
  const module=opsData().catalog.find(x=>x.id===opsModule), requestId=requestUUID();if(!module)return;
  modal('新增人工记录',`<p class="notice">${esc(module.name)} · 人工留存，不代表到账、开票或处罚执行。</p><label>记录标题<input id="opRecordTitle" maxlength="120"></label><label>处理说明<textarea id="opRecordDescription" maxlength="2000"></textarea></label><label>发生时间（北京时间，留空表示未填写）<input id="opOccurredAt" type="datetime-local"></label><div class="grid2"><label>关联订单编号（可选）<input id="opOrderId" maxlength="120"></label><label>关联司机编号（可选）<input id="opDriverId" maxlength="120"></label><label>关联乘客编号（可选）<input id="opPassengerId" maxlength="120"></label></div>`,[['返回','',close],['保存人工记录','primary',async()=>{
    const payload={module:module.id,title:$('#opRecordTitle').value.trim(),description:$('#opRecordDescription').value.trim()};
    for(const [key,id] of [['orderId','opOrderId'],['driverId','opDriverId'],['passengerId','opPassengerId']]){const value=$('#'+id).value.trim();if(value)payload[key]=value;}
    const raw=$('#opOccurredAt').value;if(raw){const date=new Date((raw.length===16?raw+':00':raw)+'+08:00');if(!Number.isFinite(date.getTime()))throw Error('请填写有效的发生时间');payload.occurredAt=date.toISOString();}
    if(!payload.title||!payload.description)throw Error('请填写记录标题与处理说明');
    await act('ops.record.add',payload,requestId);close();toast('人工记录已保存');
  }]]);
}
function opsRecordDetail(record){
  if(!record)return;
  modal('人工记录 · '+record.title,`<p class="notice">${esc(record.notice||'人工记录不代表到账、开票或处罚执行。')}</p>`+details({'记录编号':esc(record.id),'来源':'人工录入','标题':esc(record.title),'状态':opsState(record.status),'处理说明':esc(record.description),'关联订单':esc(record.orderId||'未关联'),'关联司机':esc(record.driverId||'未关联'),'关联乘客':esc(record.passengerId||'未关联'),'发生时间':time(record.occurredAt),'记录人':esc(record.createdBy),'保存时间':time(record.createdAt),'版本':esc(record.version)}),[['关闭','',close],...(can('config.write')&&record.status!=='ARCHIVED'?[['归档人工记录','danger',()=>opsArchive('record',record)]]:[])]);
}


// Formal operations pages are separate from the historical free-text drafts.
function operationsPageUnavailable(title) {
  $('#content').innerHTML=head(title,'正式业务配置；历史草稿继续在“历史草稿与人工记录”中查看。')+
    '<section class="panel"><p class="notice">尚未同步正式配置，请刷新后重试。历史草稿不会自动发布。</p></section>';
}

function operationsLocalTime(value){
  if(!value)return '';
  const date=new Date(value);
  if(!Number.isFinite(date.getTime()))return '';
  return new Date(date.getTime()+8*3600000).toISOString().slice(0,16);
}
function operationsISO(value){
  if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))throw Error('请填写完整的北京时间');
  const date=new Date(value+':00+08:00');
  if(!Number.isFinite(date.getTime())||operationsLocalTime(date.toISOString())!==value)throw Error('日期或时间无效');
  return date.toISOString();
}
function operationsAnnouncementEditor(initial, save){
  const row=initial||{};
  modal(row.id?'编辑公告':'新建公告',`<p class="notice">公告仅向配置受众在登录后展示，每用户每公告版本一次，可关闭。不是微信订阅或系统离线推送。</p><label>公告标题<input id="oaTitle" maxlength="120" value="${esc(row.title||'')}"></label><label>公告内容<textarea id="oaContent" maxlength="2000">${esc(row.content||'')}</textarea></label><div class="grid2"><label>开始时间（北京时间）<input id="oaStart" type="datetime-local" value="${operationsLocalTime(row.startAt)}"></label><label>结束时间（北京时间）<input id="oaEnd" type="datetime-local" value="${operationsLocalTime(row.endAt)}"></label></div><div id="oaTargetFields"></div>`,[['返回','',close],['保存草稿','primary',async()=>{
    const value={title:$('#oaTitle').value.trim(),content:$('#oaContent').value.trim(),startAt:operationsISO($('#oaStart').value),endAt:operationsISO($('#oaEnd').value)};
    if(!value.title||!value.content)throw Error('请填写公告标题与内容');
    if(value.startAt>=value.endAt)throw Error('结束时间须晚于开始时间');
    await save(value,false);close();toast('公告草稿已保存，尚未发布');
  }],['保存并发布','primary',async()=>{
    const value={title:$('#oaTitle').value.trim(),content:$('#oaContent').value.trim(),startAt:operationsISO($('#oaStart').value),endAt:operationsISO($('#oaEnd').value)};
    if(!value.title||!value.content)throw Error('请填写公告标题与内容');
    if(value.startAt>=value.endAt)throw Error('结束时间须晚于开始时间');
    await save(value,true);close();toast('公告已发布，有效期内符合地区和受众的乘客登录后会看到弹窗');
  }]]);
}

const operationsFeatureLabels={instant:'即时叫车',reservation:'预约叫车',bidding:'司机竞价',invoice:'模拟开票申请'};
const operationsStatus={DRAFT:'草稿',PUBLISHED:'已发布',WITHDRAWN:'已撤回',CONFIGURED:'已配置',UNCONFIGURED:'未配置',DISABLED:'已停用',VALID:'有效',EXPIRING:'60天内到期',EXPIRED:'已过期',NOT_EFFECTIVE:'尚未生效',LONG_TERM:'长期有效',UNKNOWN:'有效期待补全',NONE:'无待审',PENDING:'待审核',REJECTED:'已驳回',APPROVED:'审核通过'};
function operationalData(){return state.operations||null;}
function operationsName(value){return esc(operationsStatus[value]||value||'未提供');}
function operationsCertificateImageLabel(result){
  if(result.source==='REVIEW'||result.source==='PENDING')return '待审核新材料';
  if(result.source==='ACTIVE')return '当前有效材料';
  return result.submissionId?'待审核新材料':'材料来源未确认';
}
async function operationsWrite(action,payload,requestId){
  try{return await act(action,payload,requestId);}catch(e){
    if(e.status===409){close();await refresh();toast('记录或版本已变化，已刷新。请重新打开并核对后提交。');}
    throw e;
  }
}
function operationsConfirm(title,action,payload){
  const requestId=requestUUID();
  modal(title,'<p>请核对当前内容和版本。确认后会更新正式业务配置并留存审计记录。</p>',[['返回','',close],['确认','primary',async()=>{await operationsWrite(action,payload,requestId);close();toast('操作成功，已同步最新状态');}]]);
}
function operationsScopeFields(scope='NATIONAL',target='CN'){
  return `<div class="grid2"><label>适用层级<select id="ovScope">${[['NATIONAL','全国'],['REGION','大区'],['CITY','城市']].map(([id,name])=>`<option value="${id}" ${scope===id?'selected':''}>${name}</option>`).join('')}</select></label><label>适用地区<select id="ovTarget"></select></label></div>`;
}
function bindOperationsScope(scope,target){
  function draw(){
    const type=$('#ovScope').value;
    const rows=type==='NATIONAL'?[{id:'CN',name:'全国'}]:type==='CITY'?(state.cities||[]):(state.configs||[]).filter(c=>c.scope==='region').map(c=>({id:c.target,name:c.name||({CENTRAL:'华中大区',SOUTH:'华南大区'})[c.target]||c.target}));
    $('#ovTarget').innerHTML=rows.map(c=>`<option value="${esc(c.id)}" ${c.id===target?'selected':''}>${esc(c.name||c.id)}</option>`).join('');
  }
  $('#ovScope').value=scope;$('#ovScope').onchange=draw;draw();
}
function announcementsAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('公告管理');
  const rows=ops.announcements||[];
  $('#content').innerHTML=head('公告管理','正式公告与历史草稿分开管理。有效期为北京时间，开始时间包含、结束时间不包含。',`<button id="newAnnouncement" class="primary" ${can('config.write')?'':'disabled'}>新建公告</button>`)+
    '<p class="notice">每个用户每个已发布版本只展示一次，可关闭。只在登录后应用内读取；没有外部订阅或离线推送。</p><section class="panel">'+
    (rows.length?table(['标题 / 地区','发布状态 / 版本','有效期','受众','操作'],rows.map(r=>{const d=r.draft||{},p=r.published;return `<tr><td><b>${esc(d.title)}</b><small>${esc(opsScopeNames[d.scope]||d.scope)} · ${esc(d.target)}</small>${p&&p.version!==r.version?'<small>草稿修改未发布，旧发布内容仍按原有效期展示</small>':''}</td><td>${operationsName(r.status)} · V${esc(r.version)}${p?`<small>发布版本 V${esc(p.version)}</small>`:''}</td><td>${time(d.startAt)}<br>${time(d.endAt)}</td><td>${(d.audiences||[]).map(x=>esc(x==='MINIPROGRAM'?'乘客小程序':'乘客安卓')).join('、')}</td><td><button data-ann-edit="${esc(r.id)}">${can('config.write')?'编辑草稿':'查看内容'}</button> <button data-ann-publish="${esc(r.id)}" ${can('config.write')?'':'disabled'}>发布当前草稿</button> <button data-ann-withdraw="${esc(r.id)}" ${can('config.write')&&r.status==='PUBLISHED'?'':'disabled'}>撤回</button></td></tr>`;})):'<div class="empty">暂无公告。新建草稿并发布后，符合地区和受众的用户才会收到。</div>')+'</section>';
  $('#newAnnouncement').onclick=()=>announcementEdit();
  for(const [attr,fn] of [['ann-edit',announcementEdit],['ann-publish',r=>operationsConfirm('发布公告','ops.announcement.publish',{id:r.id,expectedVersion:r.version})],['ann-withdraw',r=>operationsConfirm('撤回公告','ops.announcement.withdraw',{id:r.id,expectedVersion:r.version})]])
    $$(`[data-${attr}]`).forEach(b=>b.onclick=()=>fn(rows.find(x=>x.id===b.getAttribute('data-'+attr))));
}
function announcementEdit(row){
  if(!can('config.write')){if(row){const d=row.draft||{},p=row.published;modal('查看公告',details({'草稿标题':esc(d.title),'草稿内容':esc(d.content),'开始时间':time(d.startAt),'结束时间':time(d.endAt),'版本':esc(row.version),'发布标题':esc(p?.title||'尚未发布'),'发布内容':esc(p?.content||'尚未发布')}),[['关闭','',close]]);}return;}
  const d=row?.draft||{},requestId=requestUUID(),publishId=requestUUID();
  operationsAnnouncementEditor({...d,id:row?.id},async (value,publish)=>{
    const audiences=$$('[data-audience]:checked').map(x=>x.value);
    if(!audiences.length)throw Error('请至少选择一个公告受众');
    const saved=await operationsWrite('ops.announcement.save',{...value,scope:$('#ovScope').value,target:$('#ovTarget').value,audiences,...(row?{id:row.id,expectedVersion:row.version}:{})},requestId);
    const record=saved?.result?.announcement||saved?.announcement;
    if(publish){if(!record)throw Error('草稿已保存，但未取得版本，请在列表中点击“发布当前草稿”');await operationsWrite('ops.announcement.publish',{id:record.id,expectedVersion:record.version},publishId);}
  });
  $('#oaTargetFields').innerHTML=operationsScopeFields(d.scope||'NATIONAL',d.target||'CN')+'<fieldset><legend>公告受众</legend>'+[['MINIPROGRAM','乘客小程序'],['PASSENGER_ANDROID','乘客安卓']].map(([id,name])=>`<label><input type="checkbox" data-audience value="${id}" ${(d.audiences||['MINIPROGRAM','PASSENGER_ANDROID']).includes(id)?'checked':''}>${name}</label>`).join('')+'</fieldset>';
  bindOperationsScope(d.scope||'NATIONAL',d.target||'CN');
}
function companySupportAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('公司与客服电话');
  const companies=ops.companies||[],bindings=ops.driverCompanies||[],splits=ops.companySplitAccounts||[];
  $('#content').innerHTML=head('公司与客服电话','维护出租车公司客服电话、地图服务费、平台/公司佣金比例及开票税号；绑定公司的司机须在所属公司完成分账账户模拟签约后方可上线接单。',`<button id="newCompany" class="primary" ${can('config.write')?'':'disabled'}>新增公司</button>`)+
    '<section class="panel"><h2>公司名册</h2>'+(companies.length?table(['公司','客服电话','地图服务费 / 分账比例','开票抬头 / 商户税号','分账签约 / 状态','操作'],companies.map(c=>{const sp=splits.find(s=>s.companyId===c.id),rate=c.platformRate??50;return `<tr><td><b>${esc(c.name)}</b><small>${esc(c.id)}</small></td><td>${esc(c.supportPhone||'未配置')}</td><td>${money(c.mapServiceFee||0)}/单<small>平台 ${esc(rate)}% · 公司 ${esc(100-rate)}%</small></td><td>${esc(c.invoiceTitle||c.name)}<small>${esc(c.taxNumber||'未维护税号')}</small></td><td>${badge(sp?.status==='SIGNED'?'SIGNED':'UNSIGNED')}<small>${c.enabled?'启用':'停用'} · V${esc(c.version)}</small></td><td><button data-company-edit="${esc(c.id)}" ${can('config.write')?'':'disabled'}>编辑</button></td></tr>`;})):'<div class="empty">暂无公司，请先维护公司和客服电话。</div>')+'</section>'+
    '<section class="panel"><h2>司机公司归属</h2><p class="muted">变更只影响后续新单，不改写历史订单公司；可解除绑定。</p>'+table(['司机','当前公司','绑定版本','操作'],(state.drivers||[]).map(d=>{const b=bindings.find(x=>x.driverId===d.id);return `<tr><td>${esc(d.name)}<small>${esc(d.id)}</small></td><td>${esc(companies.find(c=>c.id===b?.companyId)?.name||'未绑定')}</td><td>${esc(b?.version||0)}</td><td><button data-driver-company="${esc(d.id)}" ${can('config.write')?'':'disabled'}>设置归属</button></td></tr>`;}))+'</section>';
  $('#newCompany').onclick=()=>companyEdit();
  $$('[data-company-edit]').forEach(b=>b.onclick=()=>companyEdit(companies.find(c=>c.id===b.dataset.companyEdit)));
  $$('[data-driver-company]').forEach(b=>b.onclick=()=>{
    const driverId=b.dataset.driverCompany,binding=bindings.find(x=>x.driverId===driverId),requestId=requestUUID();
    modal('设置司机公司归属',`<p>${esc((state.drivers||[]).find(x=>x.id===driverId)?.name)} · ${esc(driverId)}</p><label>归属公司<select id="companyBinding"><option value="">解除绑定 / 未配置</option>${companies.filter(c=>c.enabled).map(c=>`<option value="${esc(c.id)}" ${binding?.companyId===c.id?'selected':''}>${esc(c.name)}</option>`).join('')}</select></label>`,[['返回','',close],['保存归属','primary',async()=>{await operationsWrite('ops.driverCompany.set',{driverId,companyId:$('#companyBinding').value||null,expectedVersion:binding?.version||0},requestId);close();toast('司机公司归属已保存');}]]);
  });
}
function companyEdit(c){
  const requestId=requestUUID();
  const initRate=c?.platformRate??50,initMap=((c?(c.mapServiceFee??0):50)/100).toFixed(2);
  modal(c?'编辑公司':'新增公司',`<div class="grid2"><label>公司名称<input id="ocName" maxlength="120" value="${esc(c?.name||'')}"></label><label>客服电话（可留空）<input id="ocPhone" type="tel" maxlength="30" value="${esc(c?.supportPhone||'')}"></label><label>地图服务费（元/单，可为0）<input id="ocMapFee" type="number" min="0" max="1000" step="0.01" value="${esc(initMap)}"></label><label>平台佣金比例（%，整数 0-100）<input id="ocPlatformRate" type="number" min="0" max="100" step="1" value="${esc(initRate)}"><small id="ocCompanyRateHint">出租车公司佣金比例：${100-Number(initRate)}%</small></label><label>开票抬头（技术服务费购买方，选填）<input id="ocInvoiceTitle" maxlength="100" value="${esc(c?.invoiceTitle||'')}"></label><label>商户税号（15/18/20位大写字母数字，分账开户及开票必填）<input id="ocTaxNumber" maxlength="20" value="${esc(c?.taxNumber||'')}"></label></div><label><input id="ocEnabled" type="checkbox" ${c?.enabled!==false?'checked':''}>启用公司</label><p class="notice">维护商户税号后，可在“公司分账账户管理”办理模拟绑卡签约。停用保留历史归属，不能当作真实客服已接通。</p>`,[['返回','',close],['保存','primary',async()=>{
    const name=$('#ocName').value.trim(),phone=$('#ocPhone').value.trim(),mapFee=cents($('#ocMapFee').value.trim()),rateRaw=$('#ocPlatformRate').value.trim(),invoiceTitle=$('#ocInvoiceTitle').value.trim(),taxNumber=$('#ocTaxNumber').value.trim().toUpperCase();
    if(!name)throw Error('请填写公司名称');if(phone&&!/^[+0-9][0-9+ -]{0,29}$/.test(phone))throw Error('客服电话仅支持数字、加号、短横线和空格');
    if(mapFee>100000)throw Error('地图服务费不能超过1000元');
    if(!/^\d{1,3}$/.test(rateRaw)||Number(rateRaw)>100)throw Error('平台佣金比例须为0至100的整数');
    if(taxNumber&&!/^(?:[0-9A-Z]{15}|[0-9A-Z]{18}|[0-9A-Z]{20})$/.test(taxNumber))throw Error('商户税号须为空或15、18、20位大写字母数字');
    await operationsWrite('ops.company.save',{name,supportPhone:phone||null,enabled:$('#ocEnabled').checked,mapServiceFee:mapFee,platformRate:Number(rateRaw),invoiceTitle,taxNumber,...(c?{id:c.id,expectedVersion:c.version}:{})},requestId);close();toast('公司信息与分账规则已保存');
  }]]);
  if($('#ocPlatformRate'))$('#ocPlatformRate').oninput=()=>{const n=Number($('#ocPlatformRate').value);$('#ocCompanyRateHint').textContent=Number.isFinite(n)&&n>=0&&n<=100?`出租车公司佣金比例：${100-Math.round(n)}%`:'请输入0-100整数';};
}
function passengerFeaturesAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('乘客端功能配置');
  const configs=ops.passengerFeatureConfigs||[];
  $('#content').innerHTML=head('乘客端功能配置','全国→大区→城市逐字段继承，未配置默认开启。保存草稿不会影响乘客，发布后限制新操作；历史记录继续可查。',`<button id="newPassengerFeatures" class="primary" ${can('config.write')?'':'disabled'}>新增地区配置</button>`)+
    '<section class="panel">'+(configs.length?table(['地区','草稿','已发布','版本','操作'],configs.map((c,i)=>`<tr><td>${esc(opsScopeNames[c.scope]||c.scope)} · ${esc(c.target)}</td><td>${featureSummary(c.draft)}</td><td>${c.published?featureSummary(c.published.features):'尚未发布'}</td><td>V${esc(c.version)}${c.published?` / 发布V${esc(c.published.version)}`:''}</td><td><button data-feature-edit="${i}" ${can('config.write')?'':'disabled'}>编辑草稿</button> <button data-feature-publish="${i}" ${can('config.write')?'':'disabled'}>发布</button></td></tr>`)):'<div class="empty">尚无正式地区配置，默认沿用原功能。</div>')+'</section>';
  $('#newPassengerFeatures').onclick=()=>passengerFeaturesEdit();
  $$('[data-feature-edit]').forEach(b=>b.onclick=()=>passengerFeaturesEdit(configs[Number(b.dataset.featureEdit)]));
  $$('[data-feature-publish]').forEach(b=>b.onclick=()=>{const c=configs[Number(b.dataset.featurePublish)];operationsConfirm('发布乘客功能配置','ops.passengerFeatures.publish',{scope:c.scope,target:c.target,expectedVersion:c.version});});
}
function featureSummary(features={}){return Object.entries(operationsFeatureLabels).map(([k,n])=>`${esc(n)}：${features[k]===true?'开启':features[k]===false?'关闭':'继承'}`).join('<br>');}
function passengerFeaturesEdit(c){
  const requestId=requestUUID(),publishId=requestUUID();
  modal('编辑地区功能草稿',operationsScopeFields(c?.scope||'NATIONAL',c?.target||'CN')+'<p>选择“继承”不在本层覆盖；四项全部继承可恢复上层配置。</p>'+Object.entries(operationsFeatureLabels).map(([k,n])=>`<label>${esc(n)}<select data-feature-key="${k}">${[['inherit','继承'],['true','开启'],['false','关闭']].map(([v,t])=>`<option value="${v}" ${v===(typeof c?.draft?.[k]==='boolean'?String(c.draft[k]):'inherit')?'selected':''}>${t}</option>`).join('')}</select></label>`).join(''),[['返回','',close],['保存草稿','primary',async()=>{
    const scope=$('#ovScope').value,target=$('#ovTarget').value,existing=(operationalData().passengerFeatureConfigs||[]).find(x=>x.scope===scope&&x.target===target),features={};
    $$('[data-feature-key]').forEach(x=>{if(x.value!=='inherit')features[x.dataset.featureKey]=x.value==='true';});
    if(!c&&existing)throw Error('此地区已有配置，请返回列表打开现有配置编辑');
    await operationsWrite('ops.passengerFeatures.save',{scope,target,features,expectedVersion:c?.version||0},requestId);close();toast('功能草稿已保存，发布后生效');
  }],['保存并发布','primary',async()=>{
    const scope=$('#ovScope').value,target=$('#ovTarget').value,existing=(operationalData().passengerFeatureConfigs||[]).find(x=>x.scope===scope&&x.target===target),features={};
    $$('[data-feature-key]').forEach(x=>{if(x.value!=='inherit')features[x.dataset.featureKey]=x.value==='true';});
    if(!c&&existing)throw Error('此地区已有配置，请返回列表打开现有配置编辑');
    const saved=await operationsWrite('ops.passengerFeatures.save',{scope,target,features,expectedVersion:c?.version||0},requestId);
    const record=saved?.result?.passengerFeatureConfig||saved?.passengerFeatureConfig;
    if(!record)throw Error('草稿已保存，但未取得版本，请在列表中点击“发布”');
    await operationsWrite('ops.passengerFeatures.publish',{scope,target,expectedVersion:record.version},publishId);close();toast('已发布，乘客端刷新后按新配置展示功能入口');
  }]]);bindOperationsScope(c?.scope||'NATIONAL',c?.target||'CN');
  if(c){$('#ovScope').disabled=true;$('#ovTarget').disabled=true;}
}
function qualificationExpiryAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('资质到期提醒');
  const alerts=ops.qualificationAlerts||[];
  $('#content').innerHTML=head('资质到期提醒',`按服务端北京时间日期 ${esc(ops.serverDate||'未提供')} 计算。提前60天提醒；待审有效旧证继续用，审核通过才替换，过期阻新单且在途可完成。`)+
    '<section class="panel">'+(alerts.length?table(['司机 / 证件','当前有效期','剩余天数 / 状态','新材料审核','操作'],alerts.map(a=>`<tr><td>${esc((state.drivers||[]).find(d=>d.id===a.driverId)?.name||a.driverId)}<small>${esc(({insurance:'车辆保单',license:'驾驶证',qualification:'从业资格证'})[a.type]||a.type)}</small></td><td>${esc(a.validUntil||'待补全')}</td><td>${a.daysRemaining==null?'未知':esc(a.daysRemaining)} · ${operationsName(a.validity)}</td><td>${a.pending?'新材料待审核':operationsName(a.reviewStatus)}</td><td><button data-expiry-driver="${esc(a.driverId)}">资料与审核</button></td></tr>`)):'<div class="empty">当前没有60天内到期、过期或有效期待补全的证件。</div>')+'</section>';
  $$('[data-expiry-driver]').forEach(b=>b.onclick=()=>driverDetail(b.dataset.expiryDriver));
}


/* ---- 修改意见3.0：订单进度与费用明细、投诉图片、开票记录、司机钱包、热力阈值、积分商城 ---- */
function orderProgress(o){
  const t=o.timeline;if(!t||!Array.isArray(t.steps))return '';
  const steps=t.steps.map((s,i)=>`<li class="${s.done?'done':''}"><span class="step-dot">${s.done?'✓':i+1}</span><b>${esc(s.label)}</b><small>${s.at?time(s.at):'未发生'}</small>${s.mileage?`<small class="step-mileage">${esc(s.mileage)}</small>`:''}</li>`).join('');
  const cancelled=t.cancelled?`<p class="notice">订单已取消：${esc(queryChoiceLabel(t.cancelled.by)||'未记录取消方')} · ${esc(t.cancelled.reason||'未填写原因')}${t.cancelled.at?' · '+time(t.cancelled.at):''}</p>`:'';
  return `<h3 style="margin-top:28px">订单进度（${esc(t.completedSteps)} / ${t.steps.length}）</h3><ol class="progress-steps">${steps}</ol>${cancelled}<p class="muted" style="font-size:12px">${esc(t.notice||'')}</p>`;
}
function feeDetails(o){
  const f=o.feeBreakdown;if(!f||!Array.isArray(f.lines))return '';
  const amount=l=>l.amount==null?'待定':money(l.amount)+(l.note?` <small style="display:inline">（${esc(l.note)}）</small>`:'');
  const adjustments=(f.adjustments||[]).map(a=>`<tr><td>后台调价 V${esc(a.fareVersion)}</td><td>${money(a.before)} → ${money(a.after)}<small>${esc(a.reason)} · ${time(a.at)}</small></td></tr>`).join('');
  return `<h3>费用明细</h3><div class="tablewrap"><table class="fee-table"><tbody>${f.lines.map(l=>`<tr class="${['payable','paid'].includes(l.key)?'fee-strong':''}"><td>${esc(l.label)}</td><td>${amount(l)}</td></tr>`).join('')}${adjustments}</tbody></table></div><p class="muted" style="font-size:12px">${esc(f.notice||'')}</p>`;
}
function supportAttachmentIds(s){
  if(s.source==='ORDER_COMPLAINT')return (s.attachments||[]).map(a=>a.id).filter(Boolean);
  if(s.source==='DRIVER_COMPLAINT'||s.source==='DRIVER_FEEDBACK')return (s.attachmentFileIds||[]).filter(Boolean);
  return [];
}
function supportAttachments(s){
  const ids=supportAttachmentIds(s);
  if(!['ORDER_COMPLAINT','DRIVER_COMPLAINT','DRIVER_FEEDBACK'].includes(s.source))return '';
  return `<h3>上传图片（${ids.length}）</h3>`+(ids.length?`<div class="attachment-grid">${ids.map(id=>`<figure data-attachment="${esc(id)}"><div class="attachment-frame">加载中…</div><figcaption>${esc(id)}</figcaption></figure>`).join('')}</div>`:'<p class="muted">提交人未上传图片。</p>');
}
async function loadSupportAttachments(s){
  for(const id of supportAttachmentIds(s)){
    const frame=$$('[data-attachment]').find(x=>x.dataset.attachment===id)?.querySelector('.attachment-frame');if(!frame)continue;
    try{
      const file=await api('support-file?'+new URLSearchParams({source:s.source,ownerId:s.ownerId,fileId:id}));
      if(!['image/png','image/jpeg'].includes(file.mimeType))throw Error('不支持的图片格式');
      const img=new Image();img.alt='投诉图片 '+id;img.src=`data:${file.mimeType};base64,${file.imageBase64}`;
      img.onclick=()=>{const w=$('#attachmentZoom');if(w){w.querySelector('img').src=img.src;w.hidden=false;}};
      frame.replaceChildren(img);
    }catch(e){frame.textContent='图片读取失败：'+e.message;}
  }
  if(!$('#attachmentZoom')&&supportAttachmentIds(s).length){const z=document.createElement('div');z.id='attachmentZoom';z.hidden=true;z.innerHTML='<img alt="放大查看">';z.onclick=()=>z.hidden=true;$('#modalBody').appendChild(z);}
}
async function invoicesAdmin(){
  const rows=await queryListing('invoices','开票记录','乘客行程车费与司机会员费开票记录。均为模拟电子回执（未连接税务平台），支持乘客行程回执模拟冲红与重开。',['发票请求流水号','申请来源','手机号','申请时间','发票抬头类型','发票抬头','税号','金额(元)','电子邮箱','开票时间','状态','所属公司','操作'],r=>[
    `${esc(r.id)}<small>${esc(r.invoiceKind)}${r.reissueOf?' · 重开自 '+esc(r.reissueOf):''}</small>`,esc(r.source),esc(r.mobile||'—'),time(r.createdAt),r.titleType==='COMPANY'?'企业':'个人',`<b>${esc(r.title)}</b>`,esc(r.taxNumber||'—'),money(r.amount),esc(r.email||'—'),time(r.issuedAt),`${badge(r.invoiceStatus)}${r.reissueStatus?`<small>重开申请：${esc(label(r.reissueStatus))}</small>`:''}`,esc(r.companyName||'未归属'),`<button data-invoice="${esc(r.id)}">详情与处理</button>`]);
  if(!rows)return;
  $$('[data-invoice]').forEach(b=>b.onclick=()=>{const r=rows.find(x=>x.id===b.dataset.invoice);const linked=r.role==='PASSENGER'&&dataset('orders').some(o=>o.id===r.orderId);
    const canHandle=r.role==='PASSENGER'&&r.invoiceStatus==='SIMULATED_ISSUED'&&can('config.write');
    modal('开票记录详情','<p class="notice">模拟回执，未连接税务开票平台，不可作为正式税务报销凭证；冲红与重开会同步更新乘客端回执记录。</p>'+details({'申请编号':esc(r.id),'开票类型':esc(r.invoiceKind),'申请方':esc(queryChoiceLabel(r.role))+' · '+esc(r.accountName||'')+' · '+esc(r.accountId),'手机号':esc(r.mobile),'关联订单':esc(r.orderId),'发票抬头':esc(r.title),'抬头类型':r.titleType==='COMPANY'?'企业':'个人','税号':esc(r.taxNumber||'—'),'接收邮箱':esc(r.email||'未填写'),'开票金额':money(r.amount),'状态':badge(r.invoiceStatus),'冲红信息':r.redFlushedAt?`${time(r.redFlushedAt)} · ${esc(r.redFlushReason||'')}`:'未冲红','关联重开单':esc(r.reissuedAs||r.reissueOf||'无'),'重开申请状态':r.reissueStatus?badge(r.reissueStatus):'无','申请时间':time(r.createdAt)}),[['关闭','',close],...(linked?[['查看关联订单','',()=>orderDetail(r.orderId)]]:[]),...(canHandle?[['模拟冲红','danger',()=>invoiceRedFlushModal(r)],['冲红并重开','primary',()=>invoiceReissueModal(r)]]:[])]);});
}
async function driverWalletsAdmin(){
  await queryListing('driverWallets','司机钱包明细','查看司机钱包余额、待入账、提现与绑卡情况。金额均为模拟账务。',['账户号','司机姓名','联系方式','钱包余额(元)','待入账金额(元)','已提现金额(元)','提现中金额(元)','上次提现时间','绑定银行卡数'],r=>[
    `${esc(r.accountNo||r.accountNoLabel)}<small>${esc(r.driverId)}</small>`,esc(r.name),esc(r.mobile),`<b>${money(r.balance)}</b>`,money(r.pendingAmount),money(r.withdrawnAmount),money(r.withdrawingAmount),r.lastWithdrawalAt?time(r.lastWithdrawalAt):'从未提现',esc(r.bankCardCount)]);
}
async function driverDailyAdmin(){
  await queryListing('driverDailyBalances','司机每日余额统计','按日查看司机钱包的收入、支出与日终余额。金额均为模拟账务。',['日期','司机姓名','联系方式','当日收入(元)','当日支出(元)','日终余额(元)'],r=>[esc(r.date),`${esc(r.name)}<small>${esc(r.driverId)}</small>`,esc(r.mobile),money(r.income),money(Math.abs(r.expense)),`<b>${money(r.closingBalance)}</b>`]);
}
function heatmapAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('热力图阈值设置');
  const configs=ops.heatmapThresholds||[],cities=state.cities||[];
  const show=l=>l?`低 ≥${esc(l.low)} · 中 ≥${esc(l.mid)} · 高 ≥${esc(l.high)}`:'<span class="muted">未配置（不渲染）</span>';
  $('#content').innerHTML=head('热力图阈值设置','按区县配置。客流热力取近60分钟订单起点，每60分钟更新；司机热力取在线司机实时定位。约500米网格内数量达到阈值才渲染。')+
    '<p class="notice">某区域两类阈值均未配置时，司机端显示普通地图，不做热力渲染。</p><section class="panel">'+
    table(['区域','客流热力阈值（单/格）','司机热力阈值（人/格）','版本','操作'],cities.map(c=>{const h=configs.find(x=>x.city===c.id)||{};return `<tr><td>${esc(c.name)}<small>${esc(c.id)}</small></td><td>${show(h.passenger)}</td><td>${show(h.driver)}</td><td>V${esc(h.version||0)}</td><td><button data-heat="${esc(c.id)}" ${can('config.write')?'':'disabled'}>设置阈值</button></td></tr>`;}))+'</section>';
  $$('[data-heat]').forEach(b=>b.onclick=()=>{
    const city=cities.find(c=>c.id===b.dataset.heat),h=configs.find(x=>x.city===city.id)||{},requestId=requestUUID();
    const group=(key,name,l)=>`<fieldset><legend>${name}</legend><div class="grid2"><label>低（开始渲染）<input type="number" min="1" step="1" data-heat-field="${key}.low" value="${esc(l?.low??'')}"></label><label>中<input type="number" min="1" step="1" data-heat-field="${key}.mid" value="${esc(l?.mid??'')}"></label><label>高<input type="number" min="1" step="1" data-heat-field="${key}.high" value="${esc(l?.high??'')}"></label></div></fieldset>`;
    modal('设置热力阈值 · '+city.name,'<p>三项全部留空表示该类型不渲染；填写时须为递增的正整数。</p>'+group('passenger','客流热力（近60分钟订单起点数）',h.passenger)+group('driver','司机热力（在线司机数）',h.driver),[['返回','',close],['保存并生效','primary',async()=>{
      const read=key=>{const v=['low','mid','high'].map(k=>$$('[data-heat-field]').find(x=>x.dataset.heatField===key+'.'+k).value.trim());if(v.every(x=>x===''))return null;if(v.some(x=>!/^[1-9]\d{0,5}$/.test(x)))throw Error('阈值须为正整数；不需要时请三项全部留空');const n=v.map(Number);if(!(n[0]<n[1]&&n[1]<n[2]))throw Error('阈值须满足 低 < 中 < 高');return {low:n[0],mid:n[1],high:n[2]};};
      await operationsWrite('ops.heatmap.save',{city:city.id,expectedVersion:h.version||0,passenger:read('passenger'),driver:read('driver')},requestId);close();toast('热力阈值已保存，司机端刷新后生效');
    }]]);
  });
}
let mallTab='products';
const mallAudience={PASSENGER:'乘客',DRIVER:'司机'},mallStatus={PENDING_SHIPMENT:'待发货',SHIPPED:'已发货',COMPLETED:'已完成'};
function pointsMallAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('积分商城');
  const products=ops.mallProducts||[],redemptions=ops.mallRedemptions||[],rules=ops.pointsRules||[],accounts=ops.pointsAccounts||[];
  const tabs=[['products','商品管理'],['redemptions',`兑换记录（待发货 ${redemptions.filter(r=>r.status==='PENDING_SHIPMENT').length}）`],['rules','积分规则'],['accounts','积分明细统计']];
  let body='';
  if(mallTab==='products')body=products.length?table(['商品','适用对象','类型','兑换积分','剩余可兑 / 已兑','状态','操作'],products.map(p=>`<tr><td><b>${esc(p.name)}</b><small>${esc(p.description||'')}</small></td><td>${esc(mallAudience[p.audience])}</td><td>${p.type==='PHYSICAL'?'实物（需发货）':'虚拟权益'}</td><td>${esc(p.points)}</td><td>${esc(p.stock)} / ${esc(p.redeemed||0)}</td><td>${p.enabled?'<span class="badge good">上架</span>':'<span class="badge">下架</span>'}</td><td><button data-mall-product="${esc(p.id)}" ${can('config.write')?'':'disabled'}>编辑</button></td></tr>`)):'<div class="empty">暂无商品。新增并上架后，乘客端 / 司机端“积分商城”即可兑换。</div>';
  if(mallTab==='redemptions')body=redemptions.length?table(['兑换单','商品','兑换人','积分','收货信息','状态 / 时间','操作'],redemptions.map(r=>`<tr><td>${esc(r.id)}</td><td>${esc(r.productName)}<small>${r.type==='PHYSICAL'?'实物':'虚拟'}</small></td><td>${esc(mallAudience[r.audience])}<small>${esc(r.ownerId)}</small></td><td>${esc(r.points)}</td><td>${r.receiver?`${esc(r.receiver.name)} ${esc(r.receiver.mobile)}<small>${esc(r.receiver.address)}</small>`:'—'}</td><td>${esc(mallStatus[r.status]||r.status)}<small>兑换 ${time(r.createdAt)}${r.shippedAt?' · 发货 '+time(r.shippedAt):''}</small>${r.shipNote?`<small>${esc(r.shipNote)}</small>`:''}</td><td><button data-mall-ship="${esc(r.id)}" ${can('config.write')&&r.status==='PENDING_SHIPMENT'?'':'disabled'}>登记发货</button></td></tr>`)):'<div class="empty">暂无兑换记录。</div>';
  if(mallTab==='rules')body=table(['适用对象','触发条件与发放规则','版本 / 状态','操作'],rules.map(r=>`<tr><td><b>${esc(mallAudience[r.audience])}</b></td><td>${pointsRuleSummary(r)}</td><td>${r.enabled?'<span class="badge good">启用</span>':'<span class="badge">停用</span>'} · V${esc(r.version||0)}</td><td><button data-mall-rule="${esc(r.audience)}" ${can('config.write')?'':'disabled'}>配置规则</button></td></tr>`))+'<p class="muted" style="font-size:12px">乘客支持“完成订单”与“支付金额（每实付整元）”；司机支持“日间订单（05:30-23:00）”与“夜间订单（23:00-05:30）”（按下单北京时间）。阶梯模式统一按当月对应完单序号分档（最多5档，每月重置）。</p>';
  if(mallTab==='accounts')body=accounts.length?table(['账号','对象','手机号','当前积分','累计获得','最近变动'],accounts.map(a=>`<tr><td>${esc(a.name||'—')}<small>${esc(a.ownerId)}</small></td><td>${esc(mallAudience[a.audience])}</td><td>${esc(a.mobile)}</td><td><b>${esc(a.balance)}</b></td><td>${esc(a.total)}</td><td>${a.entries[0]?`${esc(a.entries[0].title)} ${a.entries[0].delta>0?'+':''}${esc(a.entries[0].delta)}<small>${time(a.entries[0].date)}</small>`:'—'}</td></tr>`)):'<div class="empty">需同时具备司机与乘客读取权限才能查看积分统计。</div>';
  $('#content').innerHTML=head('积分商城','管理司机 / 乘客可兑换商品、兑换发货与积分规则。积分为平台体验积分，不涉及资金。',mallTab==='products'?`<button id="newMallProduct" class="primary" ${can('config.write')?'':'disabled'}>新增商品</button>`:'')+
    `<div class="toolbar" style="margin-bottom:16px">${tabs.map(([id,name])=>`<button data-mall-tab="${id}" class="${mallTab===id?'primary':''}">${esc(name)}</button>`).join('')}</div><section class="panel">${body}</section>`;
  $$('[data-mall-tab]').forEach(b=>b.onclick=()=>{mallTab=b.dataset.mallTab;pointsMallAdmin();});
  if($('#newMallProduct'))$('#newMallProduct').onclick=()=>mallProductEdit();
  $$('[data-mall-product]').forEach(b=>b.onclick=()=>mallProductEdit(products.find(p=>p.id===b.dataset.mallProduct)));
  $$('[data-mall-ship]').forEach(b=>b.onclick=()=>{const r=redemptions.find(x=>x.id===b.dataset.mallShip),requestId=requestUUID();
    modal('登记发货',`<p>${esc(r.productName)} → ${esc(r.receiver?.name)} ${esc(r.receiver?.mobile)}<br>${esc(r.receiver?.address)}</p><label>发货说明（快递公司与单号）<input id="shipNote" maxlength="200"></label>`,[['返回','',close],['确认已发货','primary',async()=>{const note=$('#shipNote').value.trim();if(note.length<2)throw Error('请填写发货说明');await operationsWrite('ops.mallRedemption.ship',{id:r.id,expectedVersion:r.version,shipNote:note},requestId);close();toast('已登记发货，兑换人可在兑换记录中查看');}]]);});
  $$('[data-mall-rule]').forEach(b=>b.onclick=()=>pointsRuleEditModal(rules.find(x=>x.audience===b.dataset.mallRule)));
}
function mallProductEdit(p){
  const requestId=requestUUID();
  modal(p?'编辑商品':'新增商品',`<label>商品名称<input id="mpName" maxlength="60" value="${esc(p?.name||'')}"></label><label>商品说明<textarea id="mpDesc" maxlength="500">${esc(p?.description||'')}</textarea></label><div class="grid2"><label>适用对象<select id="mpAudience" ${p?'disabled':''}>${Object.entries(mallAudience).map(([id,name])=>`<option value="${id}" ${p?.audience===id?'selected':''}>${name}端</option>`).join('')}</select></label><label>商品类型<select id="mpType"><option value="VIRTUAL" ${p?.type!=='PHYSICAL'?'selected':''}>虚拟权益</option><option value="PHYSICAL" ${p?.type==='PHYSICAL'?'selected':''}>实物（需发货）</option></select></label><label>兑换所需积分<input id="mpPoints" type="number" min="1" step="1" value="${esc(p?.points??'')}"></label><label>可兑换数<input id="mpStock" type="number" min="0" step="1" value="${esc(p?.stock??'')}"></label></div><label><input id="mpEnabled" type="checkbox" ${p?.enabled!==false?'checked':''}>上架</label>`,[['返回','',close],['保存','primary',async()=>{
    const name=$('#mpName').value.trim(),points=$('#mpPoints').value.trim(),stock=$('#mpStock').value.trim();
    if(!name)throw Error('请填写商品名称');if(!/^[1-9]\d{0,7}$/.test(points))throw Error('兑换积分须为正整数');if(!/^\d{1,7}$/.test(stock))throw Error('可兑换数须为非负整数');
    await operationsWrite('ops.mallProduct.save',{name,description:$('#mpDesc').value.trim(),audience:$('#mpAudience').value,type:$('#mpType').value,points:Number(points),stock:Number(stock),enabled:$('#mpEnabled').checked,...(p?{id:p.id,expectedVersion:p.version}:{})},requestId);close();toast('商品已保存');
  }]]);
}

/* ---- 修改意见3.0 第二阶段：运价、服务费、订单/公司分账、发票冲红重开与技术服务费、服务分、优惠券资金池与实时预览、拉新与阶梯积分 ---- */
function nonNegInt(raw,fieldLabel,min=0,max=1000000){
  const s=String(raw??'').trim();
  if(!/^-?\d+$/.test(s))throw Error(`${fieldLabel}须为整数`);
  const n=Number(s);
  if(!Number.isSafeInteger(n)||n<min||(max!=null&&n>max))throw Error(`${fieldLabel}须为${min}${max!=null?'至'+max:'以上'}的整数`);
  return n;
}
function computeCouponPreview(draft){
  const type=draft?.type==='DIRECT'?'DIRECT':'FULL_REDUCTION';
  const name=String(draft?.name||'').trim()||'未命名出行优惠券';
  const amountYuan=Number(draft?.amountYuan);
  const thresholdYuan=type==='DIRECT'?0:Number(draft?.thresholdYuan);
  const qty=Number(draft?.totalQuantity);
  const perUser=Number(draft?.perUserLimit)||1;
  const amountOk=Number.isFinite(amountYuan)&&amountYuan>=0.01&&amountYuan<=10000&&/^\d+(\.\d{1,2})?$/.test(String(draft?.amountYuan||'').trim());
  const thresholdOk=type==='DIRECT'||(Number.isFinite(thresholdYuan)&&thresholdYuan>0&&thresholdYuan<=100000&&/^\d+(\.\d{1,2})?$/.test(String(draft?.thresholdYuan||'').trim()));
  const qtyOk=Number.isInteger(qty)&&qty>=1&&qty<=1000000;
  const amountCents=amountOk?Math.round(amountYuan*100):0;
  const thresholdCents=type==='DIRECT'?0:(thresholdOk?Math.round(thresholdYuan*100):0);
  let error='';
  if(!String(draft?.name||'').trim())error='请填写优惠券名称';
  else if(!amountOk)error='优惠面额须为 0.01 至 10000 元';
  else if(type==='FULL_REDUCTION'&&(!thresholdOk||thresholdCents<=amountCents))error='满减券使用门槛须大于优惠面额';
  else if(!qtyOk)error='发放总量须为 1 至 1000000 的正整数';
  const issuanceCostCents=amountCents*(qtyOk?qty:0);
  const fmt=c=>(c/100).toFixed(c%100===0?0:2);
  const labelText=type==='FULL_REDUCTION'?`满${fmt(thresholdCents)}减${fmt(amountCents)}`:`立减${fmt(amountCents)}元`;
  const conditionText=type==='FULL_REDUCTION'?`运价费满 ¥${(thresholdCents/100).toFixed(2)} 可用`:'无门槛立减（仅抵扣运价费）';
  const validityText=draft?.validity==='RANGE'
    ? `${draft?.validStartAt||'待设开始时间'} 至 ${draft?.validEndAt||'待设结束时间'}`
    : `领取后 ${Number(draft?.validDays)||7} 天内有效`;
  const claimWindowText=draft?.claimable
    ? (draft?.claimStartAt||draft?.claimEndAt?`领券时段：${draft?.claimStartAt||'立即'} ~ ${draft?.claimEndAt||'长期'}`:'领券中心常驻开放领取')
    : '不在领券中心公开（仅新人注册等定向发放）';
  return {valid:!error,error,name,type,amountCents,thresholdCents,totalQuantity:qtyOk?qty:0,perUserLimit:perUser,issuanceCostCents,label:labelText,conditionText,validityText,claimWindowText,tagText:type==='FULL_REDUCTION'?'满减券':'立减券'};
}
function validateAcceptSlots24h(items,enabled){
  if(!Array.isArray(items)||items.length>5)return {ok:false,message:'接单时段最多5个'};
  const parsed=[];
  for(const s of items){
    if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(String(s?.start||''))||!/^([01]\d|2[0-3]):[0-5]\d$/.test(String(s?.end||'')))return {ok:false,message:'接单时段须为 HH:MM 格式'};
    const a=Number(s.start.slice(0,2))*60+Number(s.start.slice(3)),b=Number(s.end.slice(0,2))*60+Number(s.end.slice(3));
    if(a===b)return {ok:false,message:'接单时段起止时间不能相同'};
    const pts=Number(s.points);
    if(!Number.isInteger(pts)||pts<0||pts>1000)return {ok:false,message:'接单加分须为0至1000的整数'};
    parsed.push({start:s.start,end:s.end,points:pts,a,b});
  }
  if(enabled){
    if(!parsed.length)return {ok:false,message:'启用接单时段加分后至少配置1个时段'};
    const cov=new Array(1440).fill(0);
    for(const p of parsed){
      if(p.a<p.b){for(let m=p.a;m<p.b;m++)cov[m]++;}
      else{for(let m=p.a;m<1440;m++)cov[m]++;for(let m=0;m<p.b;m++)cov[m]++;}
    }
    if(cov.some(c=>c!==1))return {ok:false,message:'启用接单时段加分时，全部时段须恰好无缝覆盖24小时且不重叠'};
  }
  return {ok:true,items:parsed.map(({start,end,points})=>({start,end,points}))};
}
function validateScoreLevels(levels){
  const names=['卓越司机','优秀司机','标准司机','待提升司机','风险司机'];
  if(!Array.isArray(levels)||levels.length!==5||levels.some((l,i)=>l?.name!==names[i]))return {ok:false,message:'服务分等级须固定为卓越、优秀、标准、待提升、风险五级'};
  for(let i=0;i<5;i++){
    const lv=levels[i];
    if((lv.max===null)!==(i===0)||(lv.min===null)!==(i===4))return {ok:false,message:'卓越司机无上限、风险司机无下限，其余边界须填写整数'};
    if((lv.min!==null&&!Number.isInteger(lv.min))||(lv.max!==null&&!Number.isInteger(lv.max)))return {ok:false,message:'等级分值边界须为整数'};
    if(i>0&&lv.max!==levels[i-1].min)return {ok:false,message:'相邻服务分等级边界须首尾相接'};
    if(lv.min!==null&&lv.max!==null&&lv.max<=lv.min)return {ok:false,message:'等级上限须严格大于下限'};
  }
  return {ok:true,levels};
}

async function orderSettlementsAdmin(){
  const rows=await queryListing('orderSettlements','订单分账记录','按单次差额费分账恒等式展示已完成共享订单的运价费、区域服务费、优惠券抵扣与司机/公司/平台三方分账（整数分模拟账务）。',['订单 / 城市','所属公司 / 司机','订单费用 / 实付','运价费 / 服务费 / 券','司机分账','公司佣金','平台佣金 / 差额','分账时间','操作'],r=>[
    `<b>${esc(r.orderId||r.id)}</b><small>${esc(cityLabel(r.city))}</small>`,`${esc(r.companyName||'未归属公司')}<small>${esc(r.driverId||'—')}</small>`,`<b>${money(r.orderFee)}</b><small>实付 ${money(r.paidAmount)}</small>`,`运价 ${money(r.tripFare)}<small>服务费 ${money(r.serviceFee)} · 券 -${money(r.couponAmount)}</small>`,`<b>${money(r.driverAmount)}</b>`,money(r.companyCommission),`${money(r.platformCommission)}<small>差额 ${money(r.platformDifference)} · 地图 ${money(r.mapServiceFee)}</small>`,time(r.settledAt),`<button data-settlement="${esc(r.orderId||r.id)}">恒等式明细</button>`]);
  if(!rows)return;
  $$('[data-settlement]').forEach(b=>b.onclick=()=>{
    const r=rows.find(x=>String(x.orderId||x.id)===b.dataset.settlement);if(!r)return;
    const oid=r.orderId||r.id,hasOrder=dataset('orders').some(o=>o.id===oid);
    modal('订单分账恒等式明细 · '+oid,'<p class="notice">本记录为系统模拟清分台账（费率口径：支付手续费 0.6%、分账手续费 0.15%，分币余数归平台，差额费单次扣除），未连接扫呗或银行实付通道。</p>'+details({
      '订单编号':esc(oid),'所属城市':esc(cityLabel(r.city)),'归属公司':esc(r.companyName||'未绑定公司（服务费全归平台）'),'关联司机':esc(r.driverId||'—'),
      '订单总费用 F (A+S)':money(r.orderFee),'运价费 A':money(r.tripFare),'区域服务费 S':money(r.serviceFee),'优惠券抵扣 C':'-'+money(r.couponAmount),'乘客实付 P (F-C)':money(r.paidAmount),
      '运价手续费 / 分账费':`${money(r.tripPayFee)} / ${money(r.tripSplitFee)}`,'实付手续费 / 分账费':`${money(r.paidPayFee)} / ${money(r.paidSplitFee)}`,
      '平台差额费 D':`${money(r.platformDifference)}（${r.differenceBorneBy==='SERVICE_FEE'?'从可抽佣服务费中单次扣除':'优惠券降低手续费，差额补入平台'}）`,
      '可抽佣金额 K':money(r.commissionable),'佣金比例':`平台 ${esc(r.platformRate)}% / 公司 ${esc(r.companyRate)}%`,'地图服务费 M':money(r.mapServiceFee),
      '司机分账入账':`<b>${money(r.driverAmount)}</b>`,'出租车公司佣金':`<b>${money(r.companyCommission)}</b>`,'平台佣金（含地图费与余数）':`<b>${money(r.platformCommission)}</b>`,
      '模拟扫呗入账 / 可分总额':`${money(r.scanPayIncome)} / ${money(r.scanPaySplit)}（校验 = 司机+公司+平台）`,'结算时间':time(r.settledAt)
    })+rawRecord(r),[['关闭','',close],...(hasOrder?[['查看关联订单','',()=>orderDetail(oid)]]:[])]);
  });
}

function tariffsAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('区域运价管理');
  const base=ops.defaultTariff||{},rows=ops.tariffs||[],cities=state.cities||[];
  $('#content').innerHTML=head('区域运价管理','按城市维护起步价、里程续程价、回空加收、夜间加收、低速等候费及有责取消空车费。未单独保存的城市沿用宜昌默认运价。')+
    '<p class="notice">即时单与预约单在乘客下单时按区域运价计算预估价，行程结束优先按司机实时定位里程与低速等候计费（定位样本不足时回退预估里程并明确标记）。</p><section class="panel">'+
    table(['城市','起步价 / 里程','续程单价','远途回空','夜间加收','免费等候 / 等候费 / 低速','空车费 / 免责分钟','版本','操作'],cities.map(c=>{
      const own=rows.find(x=>x.city===c.id),t={...base,...(own||{})};
      return `<tr><td><b>${esc(c.name)}</b><small>${esc(c.id)} · ${own?'独立配置':'默认规则'}</small></td><td>${money(t.startPrice)} / ${(t.startMeters/1000).toFixed(1)}km</td><td>${money(t.unitPrice)} / ${esc(t.unitMeters)}m</td><td>${(t.returnAfterMeters/1000).toFixed(1)}km起 +${esc(t.returnRate)}%</td><td>${esc(t.nightStart)}-${esc(t.nightEnd)} +${esc(t.nightRate)}%</td><td>${esc(t.freeWaitMinutes)}分钟免费 · ${money(t.waitPrice)}/分<small>&lt;${esc(t.lowSpeedKmh)}km/h 计入等候</small></td><td>${money(t.emptyFee)} · 接单${esc(t.freeCancelMinutes)}分钟内免责</td><td>V${esc(own?.version||0)}</td><td><button data-tariff-city="${esc(c.id)}" ${can('config.write')?'':'disabled'}>配置运价</button></td></tr>`;
    }))+'</section>';
  $$('[data-tariff-city]').forEach(b=>b.onclick=()=>{
    const city=cities.find(c=>c.id===b.dataset.tariffCity),own=rows.find(x=>x.city===city.id),t={...base,...(own||{})},requestId=requestUUID();
    modal('配置区域运价 · '+city.name,`<div class="grid3"><label>起步价（元）<input id="tfStartPrice" type="number" min="0" max="1000" step="0.01" value="${(t.startPrice/100).toFixed(2)}"></label><label>起步里程（米）<input id="tfStartMeters" type="number" min="0" max="100000" step="100" value="${esc(t.startMeters)}"></label><label>续程单价（元）<input id="tfUnitPrice" type="number" min="0.01" max="100" step="0.01" value="${(t.unitPrice/100).toFixed(2)}"></label><label>续程跳表距离（米，≥50）<input id="tfUnitMeters" type="number" min="50" max="10000" step="50" value="${esc(t.unitMeters)}"></label><label>回空起算里程（米）<input id="tfReturnMeters" type="number" min="0" max="1000000" step="100" value="${esc(t.returnAfterMeters)}"></label><label>回空加收比例（%）<input id="tfReturnRate" type="number" min="0" max="300" step="1" value="${esc(t.returnRate)}"></label><label>夜间开始（HH:MM）<input id="tfNightStart" value="${esc(t.nightStart)}" placeholder="23:00"></label><label>夜间结束（HH:MM，含该分钟）<input id="tfNightEnd" value="${esc(t.nightEnd)}" placeholder="05:00"></label><label>夜间加收比例（%）<input id="tfNightRate" type="number" min="0" max="300" step="1" value="${esc(t.nightRate)}"></label><label>免费等候时长（分钟）<input id="tfFreeWait" type="number" min="0" max="120" step="1" value="${esc(t.freeWaitMinutes)}"></label><label>超时等候单价（元/分钟）<input id="tfWaitPrice" type="number" min="0" max="100" step="0.01" value="${(t.waitPrice/100).toFixed(2)}"></label><label>低速并入等候阈值（km/h）<input id="tfLowSpeed" type="number" min="0" max="60" step="1" value="${esc(t.lowSpeedKmh)}"></label><label>有责取消空车费（元）<input id="tfEmptyFee" type="number" min="0" max="1000" step="0.01" value="${(t.emptyFee/100).toFixed(2)}"></label><label>接单后免责取消（分钟）<input id="tfFreeCancel" type="number" min="0" max="120" step="1" value="${esc(t.freeCancelMinutes)}"></label></div><p class="notice">保存后对后续新订单与新取消生效，已结算历史订单不重算。</p>`,[['返回','',close],['保存并生效','primary',async()=>{
      const nightStart=$('#tfNightStart').value.trim(),nightEnd=$('#tfNightEnd').value.trim();
      if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(nightStart)||!/^([01]\d|2[0-3]):[0-5]\d$/.test(nightEnd))throw Error('夜间起止时间须为 HH:MM 格式');
      const payload={city:city.id,expectedVersion:own?.version||0,startPrice:cents($('#tfStartPrice').value.trim()),startMeters:nonNegInt($('#tfStartMeters').value,'起步里程',0,100000),unitPrice:cents($('#tfUnitPrice').value.trim()),unitMeters:nonNegInt($('#tfUnitMeters').value,'续程跳表距离',50,10000),returnAfterMeters:nonNegInt($('#tfReturnMeters').value,'回空起算里程',0,1000000),returnRate:nonNegInt($('#tfReturnRate').value,'回空加收比例',0,300),nightStart,nightEnd,nightRate:nonNegInt($('#tfNightRate').value,'夜间加收比例',0,300),freeWaitMinutes:nonNegInt($('#tfFreeWait').value,'免费等候时长',0,120),waitPrice:cents($('#tfWaitPrice').value.trim()),lowSpeedKmh:nonNegInt($('#tfLowSpeed').value,'低速阈值',0,60),emptyFee:cents($('#tfEmptyFee').value.trim()),freeCancelMinutes:nonNegInt($('#tfFreeCancel').value,'免责取消分钟',0,120)};
      if(payload.unitPrice<1)throw Error('续程单价须至少0.01元');
      await operationsWrite('ops.tariff.save',payload,requestId);close();toast('区域运价已保存并生效');
    }]]);
  });
}

function serviceFeesAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('区域服务费管理');
  const rows=ops.serviceFees||[],cities=state.cities||[];
  $('#content').innerHTML=head('区域服务费管理','按城市设置每单固定区域服务费。启用后计入乘客应付金额，优惠券仅抵扣运价费、不抵扣区域服务费。')+
    '<section class="panel">'+table(['城市','每单区域服务费','启用状态','当前乘客生效金额','版本','更新时间','操作'],cities.map(c=>{
      const r=rows.find(x=>x.city===c.id)||{amount:0,enabled:false,version:0};
      return `<tr><td><b>${esc(c.name)}</b><small>${esc(c.id)}</small></td><td>${money(r.amount)}</td><td>${badge(r.enabled?'ACTIVE':'DISABLED')}</td><td><b>${money(r.enabled?r.amount:0)}</b></td><td>V${esc(r.version||0)}</td><td>${time(r.updatedAt)}</td><td><button data-sf-city="${esc(c.id)}" ${can('config.write')?'':'disabled'}>设置服务费</button></td></tr>`;
    }))+'</section>';
  $$('[data-sf-city]').forEach(b=>b.onclick=()=>{
    const city=cities.find(c=>c.id===b.dataset.sfCity),r=rows.find(x=>x.city===city.id)||{amount:0,enabled:false,version:0},requestId=requestUUID();
    modal('设置区域服务费 · '+city.name,`<label>每单固定服务费（元，0-1000）<input id="sfAmount" type="number" min="0" max="1000" step="0.01" value="${((r.amount||0)/100).toFixed(2)}"></label><label><input id="sfEnabled" type="checkbox" ${r.enabled?'checked':''}>启用该城市区域服务费</label><p class="notice">区域服务费参与平台与出租车公司佣金分配；仅对后续新创订单生效。</p>`,[['返回','',close],['保存','primary',async()=>{
      const amount=cents($('#sfAmount').value.trim());if(amount>100000)throw Error('区域服务费不能超过1000元');
      await operationsWrite('ops.serviceFee.save',{city:city.id,expectedVersion:r.version||0,amount,enabled:$('#sfEnabled').checked},requestId);close();toast('区域服务费已更新');
    }]]);
  });
}

function companySplitAccountsAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('公司分账账户管理');
  if(!can('payments.read')){$('#content').innerHTML=head('公司分账账户管理','')+'<section class="panel empty">当前角色缺少账务读取权限（payments.read）。</section>';return;}
  const companies=ops.companies||[],accounts=ops.companySplitAccounts||[];
  $('#content').innerHTML=head('公司分账账户管理','管理出租车公司模拟扫呗分账虚拟户开户、换绑及签约状态查询。已绑定公司的司机须在公司完成模拟签约后才允许上线和接单。')+
    '<p class="notice">开户前须先在“公司与客服电话”维护该公司的商户税号。此处生成模拟扫呗虚拟户（SBVA前缀）并记录脱敏卡号，不连接真实银行或扫呗通道。</p><section class="panel">'+
    (companies.length?table(['出租车公司 / 税号','签约状态 / 虚拟户','对公账户 / 开户行','分账可用余额','累计入账 / 累计提现','操作'],companies.map(c=>{
      const a=accounts.find(x=>x.companyId===c.id);
      return `<tr><td><b>${esc(c.name)}</b><small>${esc(c.id)} · 税号：${esc(c.taxNumber||'未维护')}</small></td><td>${badge(a?.status||'UNSIGNED')}<small>${esc(a?.virtualAccount||'尚未生成虚拟户')}</small></td><td>${a?`${esc(a.accountName)}<small>${esc(a.bankName)} · ${esc(a.bankAccountMask)}</small>`:'尚未绑卡'}</td><td><b>${money(a?.availableBalance||0)}</b></td><td>${money(a?.totalCredited||0)} / ${money(a?.totalWithdrawn||0)}</td><td><button data-split-open="${esc(c.id)}" ${can('config.write')?'':'disabled'}>${a?'换绑银行卡':'绑卡开户'}</button> <button data-split-query="${esc(c.id)}" ${can('config.write')?'':'disabled'}>查询签约</button></td></tr>`;
    })):'<div class="empty">暂无公司记录，请先前往“公司与客服电话”新增出租车公司并维护商户税号。</div>')+'</section>';
  $$('[data-split-open]').forEach(b=>b.onclick=()=>{
    const c=companies.find(x=>x.id===b.dataset.splitOpen),a=accounts.find(x=>x.companyId===c.id),requestId=requestUUID();
    if(!c.taxNumber){toast('请先在“公司与客服电话”维护该公司商户税号后再办理开户');navigate('companySupportAdmin');return;}
    modal((a?'换绑分账对公卡 · ':'绑卡开户（模拟签约） · ')+c.name,`<p class="notice">商户税号：${esc(c.taxNumber)}。提交后立即更新为“已签约(模拟)”，银行卡号仅保存后4位掩码，不写入审计原文。</p><label>对公账户名称<input id="csAccountName" maxlength="100" value="${esc(a?.accountName||c.invoiceTitle||c.name)}"></label><label>开户银行名称<input id="csBankName" maxlength="100" value="${esc(a?.bankName||'')}"></label><label>对公银行账号（8-30位数字）<input id="csBankAccount" maxlength="30" placeholder="请输入完整银行数字账号"></label><label>财务联系电话（选填，11位手机号）<input id="csMobile" maxlength="11" value="${esc(a?.contactMobile||'')}"></label>`,[['返回','',close],['确认提交','primary',async()=>{
      const accountName=$('#csAccountName').value.trim(),bankName=$('#csBankName').value.trim(),bankAccount=$('#csBankAccount').value.trim(),contactMobile=$('#csMobile').value.trim();
      if(!accountName||!bankName)throw Error('请填写对公账户名称和开户银行');
      if(!/^\d{8,30}$/.test(bankAccount))throw Error('对公银行账号须为8至30位纯数字');
      if(contactMobile&&!/^1\d{10}$/.test(contactMobile))throw Error('联系电话须为11位手机号或留空');
      await operationsWrite(a?'ops.companySplit.rebind':'ops.companySplit.open',{companyId:c.id,expectedVersion:a?.version||0,accountName,bankName,bankAccount,...(contactMobile?{contactMobile}:{})},requestId);
      close();toast(a?'对公银行卡换绑成功（模拟）':'公司分账账户已开户并完成模拟签约');
    }]]);
  });
  $$('[data-split-query]').forEach(b=>b.onclick=async()=>{
    b.disabled=true;
    try{
      const res=await operationsWrite('ops.companySplit.query',{companyId:b.dataset.splitQuery});
      const q=res?.companySplit||res?.result?.companySplit||{};
      modal('模拟扫呗分账关系查询结果',`<p class="notice">${esc(q.channelNotice||'模拟扫呗分账关系查询')}</p>`+details({'公司编号':esc(q.companyId),'分账签约状态':badge(q.relationStatus||q.status||'UNSIGNED'),'模拟虚拟户号':esc(q.virtualAccount||'未开户'),'账户名称':esc(q.accountName||'—'),'开户银行':esc(q.bankName||'—'),'卡号掩码':esc(q.bankAccountMask||'—'),'可用余额':money(q.availableBalance||0),'查询时间':time(q.queryTime)}),[['关闭','',close]]);
    }catch(e){toast(e.message);}finally{b.disabled=false;}
  });
}

let splitLedgerCompany='';
function companySplitLedgerAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('公司分账账户明细');
  if(!can('payments.read')){$('#content').innerHTML=head('公司分账账户明细','')+'<section class="panel empty">当前角色缺少账务读取权限（payments.read）。</section>';return;}
  const companies=ops.companies||[],accounts=ops.companySplitAccounts||[];
  const all=(ops.companySplitLedger||[]).slice().sort((a,b)=>String(b.createdAt||'').localeCompare(String(a.createdAt||'')));
  const rows=splitLedgerCompany?all.filter(x=>x.companyId===splitLedgerCompany):all;
  $('#content').innerHTML=head('公司分账账户明细','查看出租车公司订单佣金入账与对公模拟提现流水。')+
    '<section class="panel"><h2>已签约公司账户概览与提现</h2>'+(accounts.length?table(['出租车公司','虚拟户 / 对公卡','当前可提现余额','累计入账','累计提现','操作'],accounts.map(a=>{const c=companies.find(x=>x.id===a.companyId);return `<tr><td><b>${esc(c?.name||a.companyId)}</b></td><td>${esc(a.virtualAccount)}<small>${esc(a.bankName)} ${esc(a.bankAccountMask)}</small></td><td><b>${money(a.availableBalance)}</b></td><td>${money(a.totalCredited)}</td><td>${money(a.totalWithdrawn)}</td><td><button data-company-withdraw="${esc(a.companyId)}" ${can('config.write')&&a.status==='SIGNED'&&a.availableBalance>0?'':'disabled'}>模拟对公提现</button></td></tr>`;})):'<div class="empty">尚无已开户的公司分账账户。</div>')+'</section>'+
    `<section class="panel"><div class="toolbar"><h2>分账收支流水（共 ${rows.length} 条）</h2><select id="splitLedgerFilter"><option value="">全部公司</option>${companies.map(c=>`<option value="${esc(c.id)}" ${c.id===splitLedgerCompany?'selected':''}>${esc(c.name)}</option>`).join('')}</select></div>`+
    (rows.length?table(['流水编号','出租车公司','变动类型','关联订单 / 备注','变动金额','变动后可用余额','时间'],rows.map(e=>`<tr><td>${esc(e.id)}</td><td>${esc(companies.find(c=>c.id===e.companyId)?.name||e.companyId)}</td><td>${e.type==='WITHDRAWAL'?'<span class="badge warn">模拟对公提现</span>':'<span class="badge good">订单佣金分账</span>'}</td><td>${esc(e.orderId||e.note||'—')}${e.operator?`<small>操作人：${esc(e.operator)}</small>`:''}</td><td><b>${e.amount>=0?'+':''}${money(e.amount)}</b></td><td>${money(e.availableAfter)}</td><td>${time(e.createdAt)}</td></tr>`)):'<div class="empty">暂无公司分账流水。</div>')+'</section>';
  $('#splitLedgerFilter').onchange=e=>{splitLedgerCompany=e.target.value;companySplitLedgerAdmin();};
  $$('[data-company-withdraw]').forEach(b=>b.onclick=()=>{
    const a=accounts.find(x=>x.companyId===b.dataset.companyWithdraw),c=companies.find(x=>x.id===a.companyId),requestId=requestUUID();
    modal('模拟对公提现 · '+(c?.name||a.companyId),`<p class="notice">收款账户：${esc(a.accountName)}（${esc(a.bankName)} ${esc(a.bankAccountMask)}）。当前可用余额 ${money(a.availableBalance)}，仅记模拟对公提现流水，不发起真实银行打款。</p><label>提现金额（元）<input id="cwAmount" type="number" min="0.01" max="${(a.availableBalance/100).toFixed(2)}" step="0.01" value="${(a.availableBalance/100).toFixed(2)}"></label><label>提现备注（选填）<input id="cwNote" maxlength="200" placeholder="例如：月度佣金对公提现"></label>`,[['返回','',close],['确认模拟提现','primary',async()=>{
      const amount=cents($('#cwAmount').value.trim());if(amount<1||amount>a.availableBalance)throw Error('提现金额须大于0且不超过当前可用余额');
      await operationsWrite('ops.companySplit.withdraw',{companyId:a.companyId,expectedVersion:a.version,amount,note:$('#cwNote').value.trim()},requestId);close();toast('模拟对公提现已登记');
    }]]);
  });
}

function invoiceRedFlushModal(r){
  const requestId=requestUUID();
  modal('模拟冲红回执 · '+r.id,`<p class="notice">将把乘客模拟回执 ${esc(r.id)}（订单 ${esc(r.orderId)}，金额 ${money(r.amount)}）标记为“已冲红”。未连接税务平台。</p><label>冲红原因（2-200字）<textarea id="rfReason" minlength="2" maxlength="200" placeholder="请填写冲红原因，例如：抬头填写有误"></textarea></label>`,[['返回','',close],['确认冲红','danger',async()=>{
    const reason=$('#rfReason').value.trim();if(reason.length<2)throw Error('请填写至少2个字的冲红原因');
    await operationsWrite('ops.invoice.redFlush',{passengerId:r.accountId,invoiceId:r.id,reason},requestId);close();toast('回执已模拟冲红');
  }]]);
}
function invoiceReissueModal(r){
  const requestId=requestUUID();
  modal('冲红并重开模拟回执 · '+r.id,`<p class="notice">原回执 ${esc(r.id)} 将先自动冲红，并按新抬头生成重开模拟回执（金额保持 ${money(r.amount)}）。</p><div class="grid2"><label>抬头类型<select id="riType"><option value="PERSONAL" ${r.titleType!=='COMPANY'?'selected':''}>个人</option><option value="COMPANY" ${r.titleType==='COMPANY'?'selected':''}>企业</option></select></label><label>新发票抬头<input id="riTitle" maxlength="100" value="${esc(r.title||'')}"></label><label>企业税号（企业必填）<input id="riTax" maxlength="20" value="${esc(r.taxNumber||'')}"></label><label>接收邮箱（选填）<input id="riEmail" type="email" maxlength="100" value="${esc(r.email||'')}"></label></div><label>重开原因（2-200字）<textarea id="riReason" minlength="2" maxlength="200" placeholder="例如：变更为企业抬头"></textarea></label>`,[['返回','',close],['确认重开','primary',async()=>{
    const type=$('#riType').value,title=$('#riTitle').value.trim(),taxNumber=$('#riTax').value.trim().toUpperCase(),email=$('#riEmail').value.trim(),reason=$('#riReason').value.trim();
    if(!title)throw Error('请填写新发票抬头');if(type==='COMPANY'&&!taxNumber)throw Error('企业抬头须填写税号');if(reason.length<2)throw Error('请填写至少2个字的重开原因');
    await operationsWrite('ops.invoice.reissue',{passengerId:r.accountId,invoiceId:r.id,reason,type,title,taxNumber,email},requestId);close();toast('已冲红原回执并生成新模拟回执');
  }]]);
}

function invoiceReissuesAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('发票重开申请');
  if(!can('payments.read')){$('#content').innerHTML=head('发票重开申请','')+'<section class="panel empty">当前角色缺少账务读取权限（payments.read）。</section>';return;}
  const rows=(ops.invoiceReissues||[]).slice().sort((a,b)=>String(b.createdAt||'').localeCompare(String(a.createdAt||'')));
  $('#content').innerHTML=head('发票重开申请','审核乘客端提交的行程回执换开/重开申请。通过后原回执自动冲红并生成新抬头模拟回执。')+
    '<section class="panel">'+(rows.length?table(['申请单号','乘客 / 订单 / 原回执','原抬头 → 申请新抬头','金额','申请原因 / 审核意见','状态','申请时间','操作'],rows.map(r=>{
      const nt=r.newTitle||{};
      return `<tr><td><b>${esc(r.id)}</b></td><td>${esc(accountLabel(r.passengerId,'passengers'))}<small>订单 ${esc(r.orderId)} · 原回执 ${esc(r.invoiceId)}</small></td><td>${esc(r.oldTitle)} → <b>${esc(nt.title)}</b><small>${nt.type==='COMPANY'?'企业 · '+esc(nt.taxNumber||''):'个人'}${nt.email?' · '+esc(nt.email):''}</small></td><td>${money(r.amount)}</td><td>${esc(r.reason)}${r.decision?`<small>审核：${esc(r.decision)}${r.newInvoiceId?' · 新回执 '+esc(r.newInvoiceId):''}</small>`:''}</td><td>${badge(r.status)}</td><td>${time(r.createdAt)}</td><td>${r.status==='PENDING'&&can('config.write')?`<button data-reissue-decide="${esc(r.id)}" data-approve="true" class="primary">通过重开</button> <button data-reissue-decide="${esc(r.id)}" data-approve="false" class="danger">驳回</button>`:'已归档'}</td></tr>`;
    })):'<div class="empty">暂无乘客发票重开申请。</div>')+'</section>';
  $$('[data-reissue-decide]').forEach(b=>b.onclick=()=>{
    const r=rows.find(x=>x.id===b.dataset.reissueDecide),approve=b.dataset.approve==='true',requestId=requestUUID();
    modal((approve?'审核通过并重开 · ':'驳回重开申请 · ')+r.id,`<p>原回执 ${esc(r.invoiceId)}（${money(r.amount)}）：${esc(r.oldTitle)} → <b>${esc(r.newTitle?.title)}</b></p><label>审核处理意见（2-200字）<textarea id="irdReason" minlength="2" maxlength="200">${approve?'核对无误，同意冲红原回执并重开':''}</textarea></label>`,[['返回','',close],[approve?'确认通过并重开':'确认驳回',approve?'primary':'danger',async()=>{
      const reason=$('#irdReason').value.trim();if(reason.length<2)throw Error('请填写至少2个字的处理意见');
      await operationsWrite('ops.invoiceReissue.decide',{id:r.id,expectedVersion:r.version,approve,reason},requestId);close();toast(approve?'已通过申请并生成新模拟回执':'已驳回重开申请');
    }]]);
  });
}

function techInvoicesAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('技术服务费开票');
  if(!can('payments.read')){$('#content').innerHTML=head('技术服务费开票','')+'<section class="panel empty">当前角色缺少账务读取权限（payments.read）。</section>';return;}
  const issuer=ops.invoiceIssuer||{name:'',taxNumber:'',taxRate:6,version:0},rows=ops.techFeeRows||[];
  $('#content').innerHTML=head('技术服务费开票','按出租车公司、按自然月汇总已分账订单中的平台佣金，向出租车公司开具模拟技术服务费发票。',`<button id="editIssuerBtn" class="primary" ${can('config.write')?'':'disabled'}>维护平台开票方信息</button>`)+
    '<section class="panel"><h2>平台开票方（销售方）信息</h2>'+details({'销售方名称':esc(issuer.name||'尚未维护（开票前必填）'),'销售方税号':esc(issuer.taxNumber||'尚未维护（开票前必填）'),'技术服务费税率':`${esc(issuer.taxRate??6)}%`,'配置版本':'V'+esc(issuer.version||0)})+'</section>'+
    '<section class="panel"><h2>月度平台佣金开票台账</h2><p class="muted">仅统计归属到具体出租车公司且已完成分账、未全额退款的订单平台佣金。均为模拟开票回执，未连接税控设备。</p>'+
    (rows.length?table(['结算月份','出租车公司','购买方抬头 / 税号','分账订单数','平台佣金合计(含税)','适用税率','开票状态','操作'],rows.map(r=>`<tr><td><b>${esc(r.month)}</b></td><td>${esc(r.companyName||r.companyId)}</td><td>${esc(r.companyTitle||'—')}<small>${esc(r.companyTaxNumber||'未维护税号')}</small></td><td>${esc(r.orderCount)} 单</td><td><b>${money(r.platformCommission)}</b>${r.amountChanged?'<small style="color:var(--danger)">开票后当月佣金有新增</small>':''}</td><td>${esc(r.taxRate)}%</td><td>${badge(r.invoiceStatus)}</td><td>${r.issued?`<button data-tech-detail="${esc(r.id)}">查看回执</button>`:`<button data-tech-issue="${esc(r.id)}" class="primary" ${can('config.write')?'':'disabled'}>开具模拟发票</button>`}</td></tr>`)):'<div class="empty">当前尚无归属公司的已完成订单平台佣金记录。</div>')+'</section>';
  $('#editIssuerBtn').onclick=()=>{
    const requestId=requestUUID();
    modal('维护平台开票方（销售方）信息',`<label>开票方公司全称<input id="iiName" maxlength="100" value="${esc(issuer.name||'')}"></label><label>纳税人识别号（15/18/20位大写字母数字）<input id="iiTax" maxlength="20" value="${esc(issuer.taxNumber||'')}"></label><label>技术服务费税率（%，整数 0-20）<input id="iiRate" type="number" min="0" max="20" step="1" value="${esc(issuer.taxRate??6)}"></label>`,[['返回','',close],['保存','primary',async()=>{
      const name=$('#iiName').value.trim(),taxNumber=$('#iiTax').value.trim().toUpperCase(),taxRate=nonNegInt($('#iiRate').value,'税率',0,20);
      if(name.length<2)throw Error('请填写至少2个字的开票方名称');
      if(!/^(?:[0-9A-Z]{15}|[0-9A-Z]{18}|[0-9A-Z]{20})$/.test(taxNumber))throw Error('税号须为15、18或20位大写字母数字');
      await operationsWrite('ops.invoiceIssuer.save',{expectedVersion:issuer.version||0,name,taxNumber,taxRate},requestId);close();toast('平台开票方信息已保存');
    }]]);
  };
  $$('[data-tech-issue]').forEach(b=>b.onclick=()=>{
    const r=rows.find(x=>x.id===b.dataset.techIssue),requestId=requestUUID();
    if(!issuer.name||!issuer.taxNumber){toast('请先点击右上角“维护平台开票方信息”填写平台名称与税号');return;}
    if(!r.companyTaxNumber){toast('请先在“公司与客服电话”维护该公司商户税号');return;}
    const tax=Math.round(r.platformCommission*issuer.taxRate/(100+issuer.taxRate));
    modal('开具技术服务费模拟发票 · '+r.month,`<p class="notice">模拟发票回执，未连接税务平台。</p>`+details({'结算月份':esc(r.month),'购买方抬头':esc(r.companyTitle),'购买方税号':esc(r.companyTaxNumber),'销售方名称':esc(issuer.name),'销售方税号':esc(issuer.taxNumber),'关联分账订单数':esc(r.orderCount)+' 单','价税合计':money(r.platformCommission),'税率 / 模拟税额':`${esc(issuer.taxRate)}% / ${money(tax)}`,'不含税金额':money(r.platformCommission-tax)}),[['返回','',close],['确认开具','primary',async()=>{
      await operationsWrite('ops.techInvoice.issue',{companyId:r.companyId,month:r.month,expectedAmount:r.platformCommission},requestId);close();toast('技术服务费模拟发票已开具');
    }]]);
  });
  $$('[data-tech-detail]').forEach(b=>b.onclick=()=>{
    const r=rows.find(x=>x.id===b.dataset.techDetail),inv=r?.issued;if(!inv)return;
    modal('技术服务费模拟发票详情 · '+inv.id,`<p class="notice">${esc(inv.notice)}</p>`+details({'发票编号':esc(inv.id),'结算月份':esc(inv.month),'购买方名称':esc(inv.buyerTitle),'购买方税号':esc(inv.buyerTaxNumber),'销售方名称':esc(inv.sellerName),'销售方税号':esc(inv.sellerTaxNumber),'分账订单数':esc(inv.orderCount)+' 单','价税合计':money(inv.amount),'税率':esc(inv.taxRate)+'%','税额':money(inv.taxAmount),'不含税金额':money(inv.amountExcludingTax),'开票时间':time(inv.issuedAt),'开票人':esc(inv.issuedBy)}),[['关闭','',close]]);
  });
}

const SCORE_RATINGS=['0.5','1','1.5','2','2.5','3','3.5','4','4.5'];
function serviceScoreRuleAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('服务分规则配置');
  const rule=ops.serviceScoreRule||{initial:100,max:null,min:null,levels:[{name:'卓越司机',min:110,max:null},{name:'优秀司机',min:100,max:110},{name:'标准司机',min:90,max:100},{name:'待提升司机',min:80,max:90},{name:'风险司机',min:null,max:80}],acceptSlots:{enabled:false,items:[]},onlineAdd:{enabled:false,items:[]},ratingAdd:{enabled:false,points:0},onlineDeduct:{enabled:false,items:[]},ratingDeduct:{enabled:false,items:{}},complaintDeduct:{enabled:false,cycleDays:null,items:[]},version:0};
  let slots=(rule.acceptSlots?.items||[]).map(x=>({...x}));
  let onAdd=(rule.onlineAdd?.items||[]).map(x=>({...x}));
  let onDed=(rule.onlineDeduct?.items||[]).map(x=>({...x}));
  let comp=(rule.complaintDeduct?.items||[]).map(x=>({...x}));
  const lv=rule.levels||[];
  const b0=lv[0]?.min??110,b1=lv[1]?.min??100,b2=lv[2]?.min??90,b3=lv[3]?.min??80;
  const editable=can('config.write'),dis=editable?'':'disabled';
  $('#content').innerHTML=head('服务分规则配置',`配置司机初始服务分、五级门槛及接单/在线时长/评价/投诉加减分规则（当前版本 V${esc(rule.version||0)}）。`,`<button id="saveScoreRuleBtn" class="primary" ${dis}>保存服务分规则</button>`)+
    '<p class="notice">服务分仅用于展示司机服务等级，不影响派单优先级与接单资格；系统超时自动默认好评不触发评价加减分。</p>'+
    `<section class="panel"><h2>基础分值与五级划分（相邻等级自动首尾相接）</h2><div class="grid3"><label>初始服务分<input id="ssInitial" type="number" step="1" value="${esc(rule.initial??100)}" ${dis}></label><label>服务分上限（留空无上限）<input id="ssMax" type="number" step="1" value="${esc(rule.max??'')}" placeholder="留空表示无上限" ${dis}></label><label>服务分下限（留空无下限）<input id="ssMin" type="number" step="1" value="${esc(rule.min??'')}" placeholder="留空表示无下限" ${dis}></label></div><div class="grid2"><label>卓越司机最低分（≥此值，无上限）<input id="ssLv0" type="number" step="1" value="${esc(b0)}" ${dis}></label><label>优秀司机最低分（≥此值且 &lt;卓越）<input id="ssLv1" type="number" step="1" value="${esc(b1)}" ${dis}></label><label>标准司机最低分（≥此值且 &lt;优秀）<input id="ssLv2" type="number" step="1" value="${esc(b2)}" ${dis}></label><label>待提升司机最低分（≥此值且 &lt;标准；低于此值为风险司机）<input id="ssLv3" type="number" step="1" value="${esc(b3)}" ${dis}></label></div></section>`+
    `<section class="panel"><div class="toolbar"><h2>1. 接单时段加分（启用后须无缝覆盖全天 24 小时）</h2><div><button type="button" id="ssFill24h" ${dis}>一键填充全天3时段示例</button> <button type="button" id="ssAddSlot" ${dis}>添加时段</button></div></div><label><input type="checkbox" id="ssSlotsEnabled" ${rule.acceptSlots?.enabled?'checked':''} ${dis}>启用接单时段加分</label><div id="ssSlotsBox"></div></section>`+
    `<div class="split"><section class="panel"><div class="toolbar"><h2>2. 每日在线时长加分（分钟，0-1440）</h2><button type="button" id="ssAddOnAdd" ${dis}>添加区间</button></div><label><input type="checkbox" id="ssOnAddEnabled" ${rule.onlineAdd?.enabled?'checked':''} ${dis}>启用在线时长加分</label><div id="ssOnAddBox"></div></section><section class="panel"><div class="toolbar"><h2>3. 每日在线时长扣分（分钟，0-1440）</h2><button type="button" id="ssAddOnDed" ${dis}>添加区间</button></div><label><input type="checkbox" id="ssOnDedEnabled" ${rule.onlineDeduct?.enabled?'checked':''} ${dis}>启用在线时长扣分</label><div id="ssOnDedBox"></div></section></div>`+
    `<section class="panel"><h2>4. 乘客手动评分加分 / 扣分</h2><div class="grid2"><label><input type="checkbox" id="ssRateAddEnabled" ${rule.ratingAdd?.enabled?'checked':''} ${dis}>启用 5.0 满分好评加分</label><label>每次 5.0 分好评加分值<input id="ssRateAddPts" type="number" min="0" max="1000" step="1" value="${esc(rule.ratingAdd?.points??0)}" ${dis}></label></div><label><input type="checkbox" id="ssRateDedEnabled" ${rule.ratingDeduct?.enabled?'checked':''} ${dis}>启用低分评价扣分（0.5 分至 4.5 分各档扣分整数）</label><div class="grid3">${SCORE_RATINGS.map(k=>`<label>${k} 分扣减服务分<input data-ss-rating="${k}" type="number" min="0" max="1000" step="1" value="${esc(rule.ratingDeduct?.items?.[k]??0)}" ${dis}></label>`).join('')}</div></section>`+
    `<section class="panel"><div class="toolbar"><h2>5. 订单投诉周期阶梯扣分</h2><button type="button" id="ssAddComp" ${dis}>添加次数档位</button></div><div class="grid2"><label><input type="checkbox" id="ssCompEnabled" ${rule.complaintDeduct?.enabled?'checked':''} ${dis}>启用投诉扣分</label><label>统计重置周期（天，留空表示累计不重置）<input id="ssCompCycle" type="number" min="1" max="3650" step="1" value="${esc(rule.complaintDeduct?.cycleDays??'')}" placeholder="留空不重置" ${dis}></label></div><div id="ssCompBox"></div></section>`;
  function drawSlots(){
    $('#ssSlotsBox').innerHTML=slots.length?slots.map((s,i)=>`<div class="tier-grid"><label>开始时间(HH:MM)<input data-slot="${i}" data-f="start" value="${esc(s.start)}" ${dis}></label><label>结束时间(HH:MM)<input data-slot="${i}" data-f="end" value="${esc(s.end)}" ${dis}></label><label>每单加分<input data-slot="${i}" data-f="points" type="number" min="0" max="1000" value="${esc(s.points)}" ${dis}></label><button type="button" data-slot-del="${i}" ${dis}>删除</button></div>`).join(''):'<p class="muted">暂未添加接单时段。</p>';
    $$('[data-slot]').forEach(el=>el.oninput=()=>{slots[Number(el.dataset.slot)][el.dataset.f]=el.dataset.f==='points'?Number(el.value):el.value.trim();});
    $$('[data-slot-del]').forEach(el=>el.onclick=()=>{slots.splice(Number(el.dataset.slotDel),1);drawSlots();});
  }
  function drawRanges(boxId,arr,attr,allowOpenMax){
    $('#'+boxId).innerHTML=arr.length?arr.map((r,i)=>`<div class="tier-grid"><label>最小值${allowOpenMax?'(次数)':'(分钟)'}<input data-${attr}="${i}" data-f="min" type="number" min="0" value="${esc(r.min??0)}" ${dis}></label><label>最大值${allowOpenMax?'(留空无上限)':'(分钟 ≤1440)'}<input data-${attr}="${i}" data-f="max" type="number" min="1" value="${esc(r.max??'')}" placeholder="${allowOpenMax?'无上限':''}" ${dis}></label><label>分值<input data-${attr}="${i}" data-f="points" type="number" min="0" max="100000" value="${esc(r.points??1)}" ${dis}></label><button type="button" data-${attr}-del="${i}" ${dis}>删除</button></div>`).join(''):'<p class="muted">暂未配置区间。</p>';
    $$(`[data-${attr}]`).forEach(el=>el.oninput=()=>{const v=el.value.trim();arr[Number(el.getAttribute('data-'+attr))][el.dataset.f]=(el.dataset.f==='max'&&allowOpenMax&&v==='')?null:Number(v);});
    $$(`[data-${attr}-del]`).forEach(el=>el.onclick=()=>{arr.splice(Number(el.getAttribute(`data-${attr}-del`)),1);drawRanges(boxId,arr,attr,allowOpenMax);});
  }
  drawSlots();drawRanges('ssOnAddBox',onAdd,'onadd',false);drawRanges('ssOnDedBox',onDed,'onded',false);drawRanges('ssCompBox',comp,'comp',true);
  if(!editable)return;
  $('#ssFill24h').onclick=()=>{slots=[{start:'00:00',end:'07:00',points:2},{start:'07:00',end:'21:00',points:1},{start:'21:00',end:'00:00',points:2}];drawSlots();};
  $('#ssAddSlot').onclick=()=>{if(slots.length>=5){toast('最多5个时段');return;}slots.push({start:'00:00',end:'24:00'.replace('24:00','00:00'),points:1});if(slots.length===1)slots[0]={start:'00:00',end:'12:00',points:1};drawSlots();};
  $('#ssAddOnAdd').onclick=()=>{if(onAdd.length>=5){toast('最多5个区间');return;}onAdd.push({min:240,max:1440,points:1});drawRanges('ssOnAddBox',onAdd,'onadd',false);};
  $('#ssAddOnDed').onclick=()=>{if(onDed.length>=5){toast('最多5个区间');return;}onDed.push({min:0,max:60,points:1});drawRanges('ssOnDedBox',onDed,'onded',false);};
  $('#ssAddComp').onclick=()=>{if(comp.length>=5){toast('最多5个档位');return;}comp.push({min:1,max:null,points:2});drawRanges('ssCompBox',comp,'comp',true);};
  $('#saveScoreRuleBtn').onclick=async()=>{
    const requestId=requestUUID();
    try{
      const initial=nonNegInt($('#ssInitial').value,'初始服务分',-100000,100000);
      const maxRaw=$('#ssMax').value.trim(),minRaw=$('#ssMin').value.trim();
      const max=maxRaw===''?null:nonNegInt(maxRaw,'服务分上限',initial+1,1000000);
      const min=minRaw===''?null:nonNegInt(minRaw,'服务分下限',-1000000,initial-1);
      const n0=nonNegInt($('#ssLv0').value,'卓越最低分',-100000,100000),n1=nonNegInt($('#ssLv1').value,'优秀最低分',-100000,100000),n2=nonNegInt($('#ssLv2').value,'标准最低分',-100000,100000),n3=nonNegInt($('#ssLv3').value,'待提升最低分',-100000,100000);
      const lvCheck=validateScoreLevels([{name:'卓越司机',min:n0,max:null},{name:'优秀司机',min:n1,max:n0},{name:'标准司机',min:n2,max:n1},{name:'待提升司机',min:n3,max:n2},{name:'风险司机',min:null,max:n3}]);
      if(!lvCheck.ok)throw Error(lvCheck.message);
      const slotsEnabled=$('#ssSlotsEnabled').checked,slotCheck=validateAcceptSlots24h(slots,slotsEnabled);
      if(!slotCheck.ok)throw Error(slotCheck.message);
      const normRange=(arr,lbl,hi,openMax)=>arr.map(x=>{const lo=nonNegInt(x.min,lbl+'最小值',0,hi),mx=(openMax&&(x.max===null||x.max===''||Number.isNaN(x.max)))?null:nonNegInt(x.max,lbl+'最大值',0,hi);if(mx!==null&&mx<=lo)throw Error(lbl+'最大值须大于最小值');return {min:lo,max:mx,points:nonNegInt(x.points,lbl+'分值',0,100000)};});
      const onAddEnabled=$('#ssOnAddEnabled').checked,onAddItems=normRange(onAdd,'在线加分',1440,false);if(onAddEnabled&&!onAddItems.length)throw Error('启用在线时长加分后请至少添加1个区间');
      const onDedEnabled=$('#ssOnDedEnabled').checked,onDedItems=normRange(onDed,'在线扣分',1440,false);if(onDedEnabled&&!onDedItems.length)throw Error('启用在线时长扣分后请至少添加1个区间');
      const ratingItems={};SCORE_RATINGS.forEach(k=>{ratingItems[k]=nonNegInt($(`[data-ss-rating="${k}"]`).value,`${k}分扣分`,0,1000);});
      const compEnabled=$('#ssCompEnabled').checked,cycleRaw=$('#ssCompCycle').value.trim(),cycleDays=cycleRaw===''?null:nonNegInt(cycleRaw,'投诉统计周期',1,3650),compItems=normRange(comp,'投诉次数',null,true);
      if(compEnabled&&!compItems.length)throw Error('启用投诉扣分后请至少添加1个次数档位');
      await operationsWrite('ops.serviceScoreRule.save',{expectedVersion:rule.version||0,initial,max,min,levels:lvCheck.levels,acceptSlots:{enabled:slotsEnabled,items:slotCheck.items},onlineAdd:{enabled:onAddEnabled,items:onAddItems},ratingAdd:{enabled:$('#ssRateAddEnabled').checked,points:nonNegInt($('#ssRateAddPts').value,'五分好评加分',0,1000)},onlineDeduct:{enabled:onDedEnabled,items:onDedItems},ratingDeduct:{enabled:$('#ssRateDedEnabled').checked,items:ratingItems},complaintDeduct:{enabled:compEnabled,cycleDays,items:compItems}},requestId);
      toast('服务分规则已保存并生效');
    }catch(e){toast(e.message);}
  };
}

let scoreDriverFilter='';
function scoreLevelFor(score,rule){
  for(const lv of (rule?.levels||[])){if((lv.min===null||score>=lv.min)&&(lv.max===null||score<lv.max))return lv.name;}
  return '标准司机';
}
function serviceScoreStatsAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('司机服务分统计');
  if(!can('drivers.read')){$('#content').innerHTML=head('司机服务分统计','')+'<section class="panel empty">当前角色缺少司机读取权限（drivers.read）。</section>';return;}
  const rule=ops.serviceScoreRule||{initial:100,levels:[]},stats=ops.serviceScoreStats||[],drivers=state.drivers||[],entries=ops.serviceScoreEntries||[];
  const rows=drivers.map(d=>{const s=stats.find(x=>x.driverId===d.id),score=s?s.score:(rule.initial??100),lv=s?s.level:scoreLevelFor(score,rule),cnt=entries.filter(e=>e.driver===d.id).length;return {driverId:d.id,name:d.name,mobile:d.mobile,city:d.city,score,level:lv,count:cnt};}).sort((a,b)=>b.score-a.score);
  $('#content').innerHTML=head('司机服务分统计','查看全部在册司机当前总服务分、所处服务等级及变动次数。')+
    '<section class="panel">'+(rows.length?table(['司机姓名 / 编号','手机号 / 城市','当前总服务分','当前服务等级','累计变动记录','操作'],rows.map(r=>`<tr><td><b>${esc(r.name)}</b><small>${esc(r.driverId)}</small></td><td>${esc(r.mobile)}<small>${esc(cityLabel(r.city))}</small></td><td><b>${esc(r.score)} 分</b></td><td><span class="badge ${r.level==='卓越司机'||r.level==='优秀司机'?'good':r.level==='风险司机'?'bad':''}">${esc(r.level)}</span></td><td>${esc(r.count)} 条</td><td><button data-score-entries="${esc(r.driverId)}">查看明细</button></td></tr>`)):'<div class="empty">暂无司机数据。</div>')+'</section>';
  $$('[data-score-entries]').forEach(b=>b.onclick=()=>{scoreDriverFilter=b.dataset.scoreEntries;navigate('serviceScoreEntriesAdmin');});
}

function serviceScoreEntriesAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('服务分明细记录');
  if(!can('drivers.read')){$('#content').innerHTML=head('服务分明细记录','')+'<section class="panel empty">当前角色缺少司机读取权限（drivers.read）。</section>';return;}
  const all=ops.serviceScoreEntries||[],drivers=state.drivers||[];
  const rows=all.filter(e=>!scoreDriverFilter||e.driver===scoreDriverFilter);
  $('#content').innerHTML=head('服务分明细记录','查看司机接单时段、在线时长、手动评分及投诉引发的服务分加减明细。')+
    `<section class="panel"><div class="toolbar"><h2>变动流水（共 ${rows.length} 条）</h2><div><select id="ssDriverSel"><option value="">全部司机</option>${drivers.map(d=>`<option value="${esc(d.id)}" ${d.id===scoreDriverFilter?'selected':''}>${esc(d.name)} (${esc(d.id)})</option>`).join('')}</select> ${scoreDriverFilter?'<button id="ssClearFilter">显示全部</button>':''}</div></div>`+
    (rows.length?table(['记录编号','司机','加减分值','变动后总分','触发原因','关联单号 / 周期','记录时间'],rows.map(e=>{const d=drivers.find(x=>x.id===e.driver);return `<tr><td>${esc(e.id)}</td><td><b>${esc(d?.name||e.driver)}</b><small>${esc(e.driver)}</small></td><td><b style="color:${e.delta>=0?'var(--success)':'var(--danger)'}">${e.delta>0?'+':''}${esc(e.delta)}</b></td><td>${esc(e.score_after)} 分</td><td>${esc(e.reason)}</td><td>${esc(e.ref||'—')}</td><td>${time(e.created)}</td></tr>`;})):'<div class="empty">暂无服务分变动明细记录。</div>')+'</section>';
  $('#ssDriverSel').onchange=e=>{scoreDriverFilter=e.target.value;serviceScoreEntriesAdmin();};
  if($('#ssClearFilter'))$('#ssClearFilter').onclick=()=>{scoreDriverFilter='';serviceScoreEntriesAdmin();};
}

function couponPoolAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('优惠券资金池');
  if(!can('payments.read')){$('#content').innerHTML=head('优惠券资金池','')+'<section class="panel empty">当前角色缺少账务读取权限（payments.read）。</section>';return;}
  const pool=ops.couponPool||{total:0,available:0,frozen:0,used:0,warnThreshold:500000,fuseThreshold:50000,fused:true};
  const alerts=(ops.couponPoolAlerts||[]).slice().sort((a,b)=>String(b.createdAt||'').localeCompare(String(a.createdAt||'')));
  const entries=(ops.couponPoolEntries||[]).slice().sort((a,b)=>String(b.createdAt||'').localeCompare(String(a.createdAt||'')));
  const poolState=pool.available<(pool.fuseThreshold||50000)?badge('FUSE'):pool.available<(pool.warnThreshold||500000)?badge('WARNING'):'<span class="badge good">充足正常</span>';
  const entryTypeLabel={RECHARGE:'手工入账充值',FREEZE:'领券冻结面额',UNFREEZE:'过期解冻退回',DEDUCT:'订单核销扣减'};
  $('#content').innerHTML=head('优惠券资金池','管理营销优惠券资金池手工充值登记、不足预警（<¥5,000）与熔断保护（<¥500 自动停止领券）。',`<button id="poolRechargeBtn" class="primary" ${can('config.write')?'':'disabled'}>登记充值入账</button>`)+
    `<div class="stats stats-grid"><div class="stat"><span class="stat-tag">累计充值总额</span><div class="stat-num">${money(pool.total)}</div><div class="stat-sub">状态：${poolState}</div></div><div class="stat stat-highlight"><span class="stat-tag">当前可用余额</span><div class="stat-num">${money(pool.available)}</div><div class="stat-sub">&lt;¥500 触发领券熔断</div></div><div class="stat"><span class="stat-tag">未核销冻结金额</span><div class="stat-num">${money(pool.frozen)}</div><div class="stat-sub">乘客已领未使用面额</div></div><div class="stat stat-accent"><span class="stat-tag">累计已核销使用</span><div class="stat-num">${money(pool.used)}</div><div class="stat-sub">完单实际抵扣运价费合计</div></div></div>`+
    (alerts.length?`<section class="panel"><h2>资金池预警记录（单日同级仅触发一次，仅后台展示）</h2>${table(['预警编号','级别','预警说明','触发时可用余额','触发时间'],alerts.map(a=>`<tr><td>${esc(a.id)}</td><td>${badge(a.level)}</td><td>${esc(a.message)}</td><td>${money(a.available)}</td><td>${time(a.createdAt)}</td></tr>`))}</section>`:'')+
    `<section class="panel"><h2>资金池变动流水（共 ${entries.length} 条）</h2>`+(entries.length?table(['流水号','变动类型','变动金额','变动后可用 / 冻结 / 已用','关联凭证 / 券号 / 订单','备注 / 操作人','时间'],entries.map(e=>`<tr><td>${esc(e.id)}</td><td>${esc(entryTypeLabel[e.type]||e.type)}</td><td><b>${money(e.amount)}</b>${e.face!=null&&e.face!==e.amount?`<small>原冻结面额 ${money(e.face)}，差额退回可用</small>`:''}</td><td>可用 ${money(e.availableAfter)}<small>冻结 ${money(e.frozenAfter)} · 已用 ${money(e.usedAfter)}</small></td><td>${esc(e.reference||e.couponId||'—')}${e.orderId?`<small>订单 ${esc(e.orderId)}</small>`:''}</td><td>${esc(e.note||e.operator||'系统自动')}</td><td>${time(e.createdAt)}</td></tr>`)):'<div class="empty">尚无资金池流水，请先点击右上角“登记充值入账”补充可用营销资金。</div>')+'</section>';
  $('#poolRechargeBtn').onclick=()=>{
    const requestId=requestUUID();
    modal('登记优惠券资金池充值（模拟入账）','<p class="notice">未接入扫呗实时入金，由财务/运营核对线下或扫呗入账单号后手工登记，单号不可重复。</p><label>充值金额（元）<input id="prAmount" type="number" min="0.01" max="10000000" step="0.01" value="10000.00"></label><label>扫呗 / 财务入账单号（唯一，2-64位）<input id="prRef" maxlength="64" placeholder="例如：CBK20261112001"></label><label>备注说明（选填）<input id="prNote" maxlength="200" placeholder="例如：11月首批营销预算入池"></label>',[['返回','',close],['确认入账','primary',async()=>{
      const amount=cents($('#prAmount').value.trim()),reference=$('#prRef').value.trim(),note=$('#prNote').value.trim();
      if(amount<1)throw Error('充值金额须至少0.01元');if(reference.length<2)throw Error('请填写至少2个字符的入账单号');
      await operationsWrite('ops.couponPool.recharge',{amount,reference,note},requestId);close();toast('优惠券资金池充值已登记');
    }]]);
  };
}

let editingCouponId=null;
function couponCreateAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('制作优惠券');
  const templates=(ops.couponTemplates||[]).slice().sort((a,b)=>String(b.updatedAt||'').localeCompare(String(a.updatedAt||'')));
  const pool=ops.couponPool||null;
  const cur=editingCouponId?templates.find(x=>x.id===editingCouponId):null;
  if(editingCouponId&&!cur)editingCouponId=null;
  const editable=can('config.write'),dis=editable?'':'disabled';
  $('#content').innerHTML=head('制作优惠券','配置满减券或立减券（仅抵扣运价费，抵扣后运价费不低于0），右侧实时预览乘客端券面样式与最高发行总成本。',cur?`<button id="resetCouponFormBtn">切换为新建优惠券</button>`:'')+
    `<div class="coupon-studio"><section class="panel"><h2>${cur?'编辑优惠券 · '+esc(cur.id)+' (V'+esc(cur.version)+')':'新建优惠券模板'}</h2>`+
    `<div class="grid2"><label>优惠券名称（1-40字）<input id="ctName" maxlength="40" value="${esc(cur?.name||'')}" placeholder="例如：宜昌出行立减券" ${dis}></label><label>优惠类型<select id="ctType" ${dis}><option value="FULL_REDUCTION" ${cur?.type!=='DIRECT'?'selected':''}>满减券（运价费满门槛可用）</option><option value="DIRECT" ${cur?.type==='DIRECT'?'selected':''}>立减券（无门槛抵扣运价费）</option></select></label><label>优惠面额（元）<input id="ctAmount" type="number" min="0.01" max="10000" step="0.01" value="${cur?((cur.amount||0)/100).toFixed(2):'5.00'}" ${dis}></label><label>满减使用门槛（元，满减券须大于面额）<input id="ctThreshold" type="number" min="0" max="100000" step="0.01" value="${cur?((cur.threshold||0)/100).toFixed(2):'20.00'}" ${dis}></label><label>发放总量（张，≥已领 ${esc(cur?.issued||0)}）<input id="ctTotal" type="number" min="${Math.max(1,cur?.issued||1)}" max="1000000" step="1" value="${esc(cur?.totalQuantity??100)}" ${dis}></label><label>每人限领张数（1-100）<input id="ctPerUser" type="number" min="1" max="100" step="1" value="${esc(cur?.perUserLimit??1)}" ${dis}></label></div>`+
    `<div class="grid3"><label>有效期方式<select id="ctValidity" ${dis}><option value="DAYS" ${cur?.validity!=='RANGE'?'selected':''}>领取后固定天数</option><option value="RANGE" ${cur?.validity==='RANGE'?'selected':''}>固定起止时间</option></select></label><label id="ctDaysWrap">领取后有效天数（1-365）<input id="ctValidDays" type="number" min="1" max="365" step="1" value="${esc(cur?.validDays??7)}" ${dis}></label><label id="ctStartWrap" hidden>生效开始（北京时间）<input id="ctValidStart" type="datetime-local" value="${operationsLocalTime(cur?.validStartAt)}" ${dis}></label><label id="ctEndWrap" hidden>生效结束（北京时间）<input id="ctValidEnd" type="datetime-local" value="${operationsLocalTime(cur?.validEndAt)}" ${dis}></label></div>`+
    `<div class="grid2"><label>可领取开始时间（选填，北京时间）<input id="ctClaimStart" type="datetime-local" value="${operationsLocalTime(cur?.claimStartAt)}" ${dis}></label><label>可领取结束时间（选填，北京时间）<input id="ctClaimEnd" type="datetime-local" value="${operationsLocalTime(cur?.claimEndAt)}" ${dis}></label></div>`+
    `<label>使用说明（选填，最多300字）<textarea id="ctDesc" maxlength="300" placeholder="例如：仅抵扣行程运价费，不抵扣区域服务费；取消订单原路退券。" ${dis}>${esc(cur?.description||'')}</textarea></label>`+
    `<div class="grid2"><label><input type="checkbox" id="ctClaimable" ${cur?.claimable!==false?'checked':''} ${dis}>在乘客端“领券中心”公开展示</label><label><input type="checkbox" id="ctEnabled" ${cur?.enabled!==false?'checked':''} ${dis}>立即启用此优惠券模板</label></div>`+
    `<div class="toolbar"><button id="saveCouponBtn" class="primary" ${dis}>${cur?'保存模板更新':'创建优惠券模板'}</button></div></section>`+
    `<aside class="panel coupon-preview-panel"><h2>券面与成本实时预览</h2><div id="couponLivePreview"></div></aside></div>`+
    `<section class="panel"><h2>已创建优惠券模板（共 ${templates.length} 个）</h2>`+(templates.length?table(['编号 / 名称','类型 / 规则','有效期','已领 / 总量','每人限领 / 渠道','最高发行成本','状态 / 版本','操作'],templates.map(t=>`<tr><td><b>${esc(t.name)}</b><small>${esc(t.id)}</small></td><td>${t.type==='FULL_REDUCTION'?`满 ${(t.threshold/100).toFixed(2)} 减 ${(t.amount/100).toFixed(2)} 元`:`立减 ${(t.amount/100).toFixed(2)} 元`}</td><td>${t.validity==='DAYS'?`领后 ${esc(t.validDays)} 天`:`${time(t.validStartAt)}<br>至 ${time(t.validEndAt)}`}</td><td>${esc(t.issued||0)} / ${esc(t.totalQuantity)}</td><td>限 ${esc(t.perUserLimit)} 张<small>${t.claimable?'领券中心公开':'仅定向发放'}</small></td><td><b>${money(t.cost||(t.amount*t.totalQuantity))}</b></td><td>${badge(t.enabled?'ACTIVE':'DISABLED')} · V${esc(t.version)}</td><td><button data-edit-coupon="${esc(t.id)}">${editable?'载入编辑':'载入预览'}</button></td></tr>`)):'<div class="empty">尚未创建任何优惠券模板。</div>')+'</section>';
  function syncFormVisibility(){
    const isDirect=$('#ctType').value==='DIRECT',isRange=$('#ctValidity').value==='RANGE';
    $('#ctThreshold').disabled=!editable||isDirect;
    $('#ctDaysWrap').hidden=isRange;$('#ctStartWrap').hidden=!isRange;$('#ctEndWrap').hidden=!isRange;
  }
  function updatePreview(){
    syncFormVisibility();
    const p=computeCouponPreview({name:$('#ctName').value,type:$('#ctType').value,amountYuan:$('#ctAmount').value,thresholdYuan:$('#ctThreshold').value,totalQuantity:Number($('#ctTotal').value),perUserLimit:Number($('#ctPerUser').value),validity:$('#ctValidity').value,validDays:$('#ctValidDays').value,validStartAt:$('#ctValidStart').value,validEndAt:$('#ctValidEnd').value,claimable:$('#ctClaimable').checked,claimStartAt:$('#ctClaimStart').value,claimEndAt:$('#ctClaimEnd').value});
    const poolWarn=pool?(pool.fused?'<p class="error">当前资金池可用余额低于 ¥500（已熔断），乘客暂时无法领取，请先前往“优惠券资金池”充值。</p>':p.issuanceCostCents>pool.available?`<p class="notice">提示：本次最高发行总成本 ${money(p.issuanceCostCents)} 高于当前资金池可用余额 ${money(pool.available)}，超出部分在领券时将受可用余额限制。</p>`:''):'';
    $('#couponLivePreview').innerHTML=`<div class="coupon-ticket"><div class="coupon-ticket-left"><div class="coupon-ticket-amount"><small>¥</small>${(p.amountCents/100).toFixed(p.amountCents%100===0?0:2)}</div><div class="coupon-ticket-cond">${esc(p.conditionText)}</div></div><div class="coupon-ticket-right"><div><span class="coupon-ticket-tag">${esc(p.tagText)}</span><span class="coupon-ticket-tag">${$('#ctClaimable').checked?'领券中心':'定向活动'}</span><div class="coupon-ticket-title">${esc(p.name)}</div></div><div class="coupon-ticket-meta">${esc(p.validityText)} · 每人限领 ${esc(p.perUserLimit)} 张<br>${esc($('#ctDesc').value.trim()||'仅抵扣行程运价费，不抵扣区域服务费')}</div></div></div>`+
      `<div class="cost-box"><div class="cost-row"><span>券面简述</span><b>${esc(p.label)}</b></div><div class="cost-row"><span>单张最高补贴</span><b>${money(p.amountCents)}</b></div><div class="cost-row"><span>计划发放总量</span><b>${esc(p.totalQuantity)} 张</b></div><div class="cost-row"><span>预计最高发行总成本</span><b style="font-size:16px;color:var(--teal-hover)">${money(p.issuanceCostCents)}</b></div>${pool?`<div class="cost-row"><span>当前资金池可用余额</span><b>${money(pool.available)}</b></div>`:''}<div class="cost-row"><span>领取开放策略</span><span>${esc(p.claimWindowText)}</span></div></div>`+
      (p.error?`<p class="error" style="margin-top:12px">${esc(p.error)}</p>`:'')+poolWarn;
  }
  ['ctName','ctType','ctAmount','ctThreshold','ctTotal','ctPerUser','ctValidity','ctValidDays','ctValidStart','ctValidEnd','ctClaimStart','ctClaimEnd','ctDesc','ctClaimable','ctEnabled'].forEach(id=>{const el=$('#'+id);if(el){el.addEventListener('input',updatePreview);el.addEventListener('change',updatePreview);}});
  updatePreview();
  if($('#resetCouponFormBtn'))$('#resetCouponFormBtn').onclick=()=>{editingCouponId=null;couponCreateAdmin();};
  $$('[data-edit-coupon]').forEach(b=>b.onclick=()=>{editingCouponId=b.dataset.editCoupon;couponCreateAdmin();window.scrollTo(0,0);});
  if(editable)$('#saveCouponBtn').onclick=async()=>{
    const requestId=requestUUID();
    try{
      const type=$('#ctType').value,amount=cents($('#ctAmount').value.trim()),threshold=type==='DIRECT'?0:cents($('#ctThreshold').value.trim());
      const name=$('#ctName').value.trim();if(!name)throw Error('请填写优惠券名称');
      if(amount<1||amount>1000000)throw Error('优惠面额须在0.01至10000元之间');
      if(type==='FULL_REDUCTION'&&threshold<=amount)throw Error('满减券门槛须严格大于优惠面额');
      const totalQuantity=nonNegInt($('#ctTotal').value,'发放总量',Math.max(1,cur?.issued||1),1000000),perUserLimit=nonNegInt($('#ctPerUser').value,'每人限领',1,100);
      const validity=$('#ctValidity').value;
      const payload={name,description:$('#ctDesc').value.trim(),type,threshold,amount,validity,totalQuantity,perUserLimit,claimable:$('#ctClaimable').checked,enabled:$('#ctEnabled').checked,...(cur?{id:cur.id,expectedVersion:cur.version}:{})};
      if(validity==='DAYS')payload.validDays=nonNegInt($('#ctValidDays').value,'领取后有效天数',1,365);
      else{payload.validStartAt=operationsISO($('#ctValidStart').value);payload.validEndAt=operationsISO($('#ctValidEnd').value);if(payload.validStartAt>=payload.validEndAt)throw Error('有效期结束时间须晚于开始时间');}
      const cs=$('#ctClaimStart').value.trim(),ce=$('#ctClaimEnd').value.trim();
      if(cs)payload.claimStartAt=operationsISO(cs);if(ce)payload.claimEndAt=operationsISO(ce);
      if(payload.claimStartAt&&payload.claimEndAt&&payload.claimStartAt>=payload.claimEndAt)throw Error('可领取结束时间须晚于开始时间');
      await operationsWrite('ops.couponTemplate.save',payload,requestId);editingCouponId=null;toast('优惠券模板已保存');
    }catch(e){toast(e.message);}
  };
}

function newUserCouponAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('新用户注册');
  const cfg=ops.newUserCoupon||{enabled:false,templateIds:[],version:0},templates=ops.couponTemplates||[];
  const selected=new Set(cfg.templateIds||[]),editable=can('config.write'),dis=editable?'':'disabled';
  $('#content').innerHTML=head('新用户注册赠券','配置新乘客首次注册登录时自动发放的优惠券组合（最多勾选 10 张模板）。',`<button id="saveNewUserCouponBtn" class="primary" ${dis}>保存新人赠券配置</button>`)+
    '<p class="notice">新乘客首次注册成功时按所选模板自动发券并从优惠券资金池冻结面额；若某模板库存不足、未启用或资金池已熔断，自动跳过该张券且不影响新乘客正常注册登录。</p>'+
    `<section class="panel"><label><input type="checkbox" id="nucEnabled" ${cfg.enabled?'checked':''} ${dis}>启用新乘客首次注册自动赠券（当前版本 V${esc(cfg.version||0)}）</label><h3>选择关联优惠券模板（已选 <span id="nucCount">${selected.size}</span> / 10）</h3>`+
    (templates.length?table(['选择','模板编号 / 名称','面额与门槛','有效期','剩余库存','状态'],templates.map(t=>`<tr><td><input type="checkbox" data-nuc-tpl="${esc(t.id)}" ${selected.has(t.id)?'checked':''} ${dis}></td><td><b>${esc(t.name)}</b><small>${esc(t.id)}</small></td><td>${t.type==='FULL_REDUCTION'?`满 ${(t.threshold/100).toFixed(2)} 减 ${(t.amount/100).toFixed(2)} 元`:`立减 ${(t.amount/100).toFixed(2)} 元`}</td><td>${t.validity==='DAYS'?`领后 ${esc(t.validDays)} 天`:`${time(t.validStartAt)} ~ ${time(t.validEndAt)}`}</td><td>${esc(t.totalQuantity-(t.issued||0))} 张</td><td>${badge(t.enabled?'ACTIVE':'DISABLED')}</td></tr>`)):'<div class="empty">尚无优惠券模板，请先前往“制作优惠券”创建模板。</div>')+'</section>';
  $$('[data-nuc-tpl]').forEach(cb=>cb.onchange=()=>$('#nucCount').textContent=String($$('[data-nuc-tpl]:checked').length));
  if(editable)$('#saveNewUserCouponBtn').onclick=async()=>{
    const requestId=requestUUID(),templateIds=$$('[data-nuc-tpl]:checked').map(x=>x.dataset.nucTpl);
    if(templateIds.length>10){toast('最多关联10张优惠券模板');return;}
    try{await operationsWrite('ops.newUserCoupon.save',{expectedVersion:cfg.version||0,enabled:$('#nucEnabled').checked,templateIds},requestId);toast('新用户注册赠券配置已保存');}catch(e){toast(e.message);}
  };
}

let referralTab='rules';
function referralsAdmin(){
  const ops=operationalData();if(!ops)return operationsPageUnavailable('拉新活动');
  const rules=ops.referralRules||[],records=ops.referrals||[];
  const tabs=[['rules','拉新规则配置'],['records',`拉新邀请记录（${records.length}）`]];
  let body='';
  if(referralTab==='rules'){
    body='<p class="notice">受邀新乘客或新司机在首次注册登录时填写 7 位邀请码（P/D 开头）即可绑定邀请关系；任何单个账号最多奖励前 100 名有效受邀人，超出部分记录为“已达上限”不再发积分。</p>'+
      table(['邀请人角色','触发时机','单次奖励模式','累计里程碑奖励','状态 / 版本','操作'],['PASSENGER','DRIVER'].map(role=>{
        const r=rules.find(x=>x.role===role)||{role,enabled:false,trigger:'REGISTER',tiered:false,points:0,tiers:[],cumulative:false,bonuses:[],version:0};
        const baseTxt=r.tiered?(r.tiers||[]).map(t=>`第${t.from}人起 +${t.points}分`).join('；')||'阶梯待配':`固定每邀1人 +${esc(r.points)} 积分`;
        const bonusTxt=r.cumulative?(r.bonuses||[]).map(b=>`满${b.count}人额外 +${b.points}分`).join('；')||'未设':'未开启';
        return `<tr><td><b>${role==='PASSENGER'?'乘客邀请好友':'司机邀请好友'}</b></td><td>${r.trigger==='FIRST_ORDER'?'受邀人完成首单':'受邀人完成注册'}</td><td>${esc(baseTxt)}</td><td>${esc(bonusTxt)}</td><td>${badge(r.enabled?'ACTIVE':'DISABLED')} · V${esc(r.version||0)}</td><td><button data-ref-rule="${role}" ${can('config.write')?'':'disabled'}>配置规则</button></td></tr>`;
      }));
  }else{
    body=!can('passengers.read')?'<div class="empty">当前角色缺少乘客读取权限（passengers.read），无法查看拉新明细。</div>':
      (records.length?table(['记录号','邀请人','受邀人','受邀手机号','触发节点','状态','获得积分','注册 / 发放时间'],records.map(r=>`<tr><td>${esc(r.id)}</td><td>${r.inviter_role==='DRIVER'?'司机':'乘客'} · ${esc(r.inviter)}</td><td>${r.invitee_role==='DRIVER'?'司机':'乘客'} · ${esc(r.invitee)}</td><td>${esc(r.mobileMasked||'—')}</td><td>${r.trigger==='FIRST_ORDER'?'首单完单':r.trigger==='REGISTER'?'注册成功':'待触发'}</td><td>${badge(r.status)}</td><td><b>+${esc(r.reward||0)}</b></td><td>${time(r.createdAt)}${r.rewarded_at?`<small>发放 ${time(r.rewarded_at)}</small>`:''}</td></tr>`)):'<div class="empty">暂无拉新绑定记录。</div>');
  }
  $('#content').innerHTML=head('拉新活动','配置乘客端与司机端邀请码拉新积分及阶梯/累计额外奖励，并查看拉新明细。')+
    `<div class="toolbar" style="margin-bottom:16px">${tabs.map(([id,name])=>`<button data-ref-tab="${id}" class="${referralTab===id?'primary':''}">${esc(name)}</button>`).join('')}</div><section class="panel">${body}</section>`;
  $$('[data-ref-tab]').forEach(b=>b.onclick=()=>{referralTab=b.dataset.refTab;referralsAdmin();});
  $$('[data-ref-rule]').forEach(b=>b.onclick=()=>{
    const role=b.dataset.refRule,r=rules.find(x=>x.role===role)||{role,enabled:false,trigger:'REGISTER',tiered:false,points:10,tiers:[{from:1,points:10}],cumulative:false,bonuses:[{count:5,points:50}],version:0},requestId=requestUUID();
    let tiers=(r.tiers?.length?r.tiers:[{from:1,points:10}]).map(x=>({...x}));
    let bonuses=(r.bonuses?.length?r.bonuses:[{count:5,points:50}]).map(x=>({...x}));
    modal('配置拉新规则 · '+(role==='PASSENGER'?'乘客端':'司机端'),`<div class="grid2"><label><input type="checkbox" id="rrEnabled" ${r.enabled?'checked':''}>启用该端拉新奖励</label><label>积分发放触发时机<select id="rrTrigger"><option value="REGISTER" ${r.trigger!=='FIRST_ORDER'?'selected':''}>受邀新用户完成注册</option><option value="FIRST_ORDER" ${r.trigger==='FIRST_ORDER'?'selected':''}>受邀新用户完成首单</option></select></label></div><label><input type="checkbox" id="rrTiered" ${r.tiered?'checked':''}>采用按邀请序号阶梯积分（最多5档；不勾选则为固定积分）</label><div id="rrFixedBox"><label>每成功邀请 1 人固定奖励积分<input id="rrPoints" type="number" min="0" max="1000000" step="1" value="${esc(r.points??10)}"></label></div><div id="rrTiersWrap"><div class="toolbar"><h3>按邀请人数阶梯（1-5档）</h3><button type="button" id="rrAddTier">添加档位</button></div><div id="rrTiersBox"></div></div><label><input type="checkbox" id="rrCumulative" ${r.cumulative?'checked':''}>开启累计邀请人数里程碑额外奖励（最多5档，单账号封顶100人）</label><div id="rrBonusWrap"><div class="toolbar"><h3>累计人数额外奖励（1-5档）</h3><button type="button" id="rrAddBonus">添加里程碑</button></div><div id="rrBonusBox"></div></div>`,[['返回','',close],['保存规则','primary',async()=>{
      const tiered=$('#rrTiered').checked,cumulative=$('#rrCumulative').checked;
      const normTiers=tiered?tiers.map(t=>({from:nonNegInt(t.from,'阶梯起始人数',1,100),points:nonNegInt(t.points,'阶梯积分',0,1000000)})):[];
      if(tiered&&(normTiers.length<1||normTiers.length>5||new Set(normTiers.map(x=>x.from)).size!==normTiers.length))throw Error('阶梯须为1至5档且起始人数不能重复');
      const normBonuses=cumulative?bonuses.map(x=>({count:nonNegInt(x.count,'累计人数',1,100),points:nonNegInt(x.points,'额外奖励积分',1,1000000)})):[];
      if(cumulative&&(normBonuses.length<1||normBonuses.length>5||new Set(normBonuses.map(x=>x.count)).size!==normBonuses.length))throw Error('累计奖励须为1至5档且人数不能重复');
      await operationsWrite('ops.referralRule.save',{role,expectedVersion:r.version||0,enabled:$('#rrEnabled').checked,trigger:$('#rrTrigger').value,tiered,points:tiered?0:nonNegInt($('#rrPoints').value,'每邀请积分',0,1000000),tiers:normTiers,cumulative,bonuses:normBonuses},requestId);
      close();toast('拉新规则已保存');
    }]]);
    function draw(){
      const tOn=$('#rrTiered').checked,cOn=$('#rrCumulative').checked;
      $('#rrFixedBox').hidden=tOn;$('#rrTiersWrap').hidden=!tOn;$('#rrBonusWrap').hidden=!cOn;
      $('#rrTiersBox').innerHTML=tiers.map((t,i)=>`<div class="tier-grid"><label>从第 N 位受邀人起<input data-rt="${i}" data-f="from" type="number" min="1" max="100" value="${esc(t.from)}"></label><label>每人奖励积分<input data-rt="${i}" data-f="points" type="number" min="0" max="1000000" value="${esc(t.points)}"></label>${tiers.length>1?`<button type="button" data-rt-del="${i}">删除</button>`:''}</div>`).join('');
      $$('[data-rt]').forEach(el=>el.oninput=()=>{tiers[Number(el.dataset.rt)][el.dataset.f]=Number(el.value);});
      $$('[data-rt-del]').forEach(el=>el.onclick=()=>{tiers.splice(Number(el.dataset.rtDel),1);draw();});
      $('#rrBonusBox').innerHTML=bonuses.map((x,i)=>`<div class="tier-grid"><label>累计成功满 N 人（≤100）<input data-rb="${i}" data-f="count" type="number" min="1" max="100" value="${esc(x.count)}"></label><label>额外奖励积分<input data-rb="${i}" data-f="points" type="number" min="1" max="1000000" value="${esc(x.points)}"></label>${bonuses.length>1?`<button type="button" data-rb-del="${i}">删除</button>`:''}</div>`).join('');
      $$('[data-rb]').forEach(el=>el.oninput=()=>{bonuses[Number(el.dataset.rb)][el.dataset.f]=Number(el.value);});
      $$('[data-rb-del]').forEach(el=>el.onclick=()=>{bonuses.splice(Number(el.dataset.rbDel),1);draw();});
    }
    $('#rrTiered').onchange=draw;$('#rrCumulative').onchange=draw;
    $('#rrAddTier').onclick=()=>{if(tiers.length>=5){toast('最多5档');return;}tiers.push({from:(tiers.at(-1)?.from||0)+5,points:20});draw();};
    $('#rrAddBonus').onclick=()=>{if(bonuses.length>=5){toast('最多5档');return;}bonuses.push({count:Math.min(100,(bonuses.at(-1)?.count||0)+10),points:100});draw();};
    draw();
  });
}

const POINTS_TRIGGER_LABELS={ORDER:'完成订单',AMOUNT:'按实付金额(整元)',DAY:'日间订单(05:30-23:00)',NIGHT:'夜间订单(23:00-05:30)',ANY:'完成订单'};
function pointsRuleSummary(r){
  if(Array.isArray(r?.rules)&&r.rules.length){
    return r.rules.map(item=>{
      const t=POINTS_TRIGGER_LABELS[item.trigger]||item.trigger;
      if(item.mode==='TIERED')return `${esc(t)}：当月完单阶梯 `+(item.tiers||[]).map(x=>`第${esc(x.from)}单起${item.trigger==='AMOUNT'?'每元':''}+${esc(x.points)}分`).join(' / ');
      return `${esc(t)}：固定 ${item.trigger==='AMOUNT'?`每实付满1元 +${esc(item.points)} 分`:`每单 +${esc(item.points)} 分`}`;
    }).join('<br>');
  }
  return `完成一笔订单：固定每单 +${esc(r?.perOrder??1)} 分`;
}
function pointsRuleEditModal(r){
  if(!r)return;
  const triggers=r.audience==='PASSENGER'?[['ORDER','完成订单'],['AMOUNT','支付金额（按实付整元倍乘）']]:[['DAY','日间订单（05:30-23:00 下单）'],['NIGHT','夜间订单（23:00-05:30 下单）']];
  const existing=Array.isArray(r.rules)&&r.rules.length?r.rules:(r.audience==='PASSENGER'?[{trigger:'ORDER',mode:'FIXED',points:r.perOrder??1,tiers:[]}]:[{trigger:'DAY',mode:'FIXED',points:r.perOrder??1,tiers:[]},{trigger:'NIGHT',mode:'FIXED',points:r.perOrder??1,tiers:[]}]);
  const stateByTrig={};
  for(const [trig] of triggers){
    const found=existing.find(x=>x.trigger===trig);
    stateByTrig[trig]={active:Boolean(found),mode:found?.mode||'FIXED',points:found?.points??1,tiers:(found?.tiers?.length?found.tiers:[{from:1,points:1}]).map(x=>({...x}))};
  }
  const requestId=requestUUID();
  modal('配置积分规则 · '+mallAudience[r.audience],`<p class="notice">阶梯模式统一按当月完单序号分档（最多5档，每月1日0时重置）；乘客“支付金额”阶梯表示当月第 N 单起每实付满 1 元获得的积分倍率。</p><label><input type="checkbox" id="prRuleEnabled" ${r.enabled?'checked':''}>启用${esc(mallAudience[r.audience])}积分发放</label><div id="prTriggersRoot"></div>`,[['返回','',close],['保存规则','primary',async()=>{
    const out=[];
    for(const [trig,title] of triggers){
      const st=stateByTrig[trig];if(!st.active)continue;
      if(st.mode==='FIXED'){
        out.push({trigger:trig,mode:'FIXED',points:nonNegInt(st.points,title+'固定积分',0,100000),tiers:[]});
      }else{
        const norm=st.tiers.map(t=>({from:nonNegInt(t.from,title+'阶梯起始单数',1,100000),points:nonNegInt(t.points,title+'阶梯积分',0,1000000)}));
        if(norm.length<1||norm.length>5||new Set(norm.map(x=>x.from)).size!==norm.length)throw Error(title+'阶梯须为1至5档且起始单数不重复');
        out.push({trigger:trig,mode:'TIERED',points:0,tiers:norm});
      }
    }
    if(!out.length)throw Error('请至少勾选启用一个触发条件');
    await operationsWrite('ops.pointsRule.save',{audience:r.audience,expectedVersion:r.version||0,enabled:$('#prRuleEnabled').checked,rules:out},requestId);
    close();toast('积分规则已保存');
  }]]);
  function draw(){
    $('#prTriggersRoot').innerHTML=triggers.map(([trig,title])=>{
      const st=stateByTrig[trig];
      return `<section class="panel"><label><input type="checkbox" data-pt-active="${trig}" ${st.active?'checked':''}><b>${esc(title)}</b></label>${st.active?`<div class="grid2"><label>计算方式<select data-pt-mode="${trig}"><option value="FIXED" ${st.mode==='FIXED'?'selected':''}>固定积分</option><option value="TIERED" ${st.mode==='TIERED'?'selected':''}>按当月完单数阶梯（1-5档）</option></select></label>${st.mode==='FIXED'?`<label>${trig==='AMOUNT'?'每实付满 1 元得积分':'每完成 1 单得积分'}<input data-pt-pts="${trig}" type="number" min="0" max="100000" value="${esc(st.points)}"></label>`:'<div></div>'}</div>${st.mode==='TIERED'?`<div class="toolbar"><small>当月第 N 单起对应${trig==='AMOUNT'?'每满1元积分':'本单积分'}</small><button type="button" data-pt-add="${trig}">添加档位</button></div>`+st.tiers.map((t,i)=>`<div class="tier-grid"><label>当月第 N 单起<input data-pt-tier="${trig}:${i}:from" type="number" min="1" value="${esc(t.from)}"></label><label>${trig==='AMOUNT'?'每满1元积分':'本单奖励积分'}<input data-pt-tier="${trig}:${i}:points" type="number" min="0" max="1000000" value="${esc(t.points)}"></label>${st.tiers.length>1?`<button type="button" data-pt-del="${trig}:${i}">删除</button>`:''}</div>`).join(''):''}`:''}</section>`;
    }).join('');
    $$('[data-pt-active]').forEach(el=>el.onchange=()=>{stateByTrig[el.dataset.ptActive].active=el.checked;draw();});
    $$('[data-pt-mode]').forEach(el=>el.onchange=()=>{stateByTrig[el.dataset.ptMode].mode=el.value;draw();});
    $$('[data-pt-pts]').forEach(el=>el.oninput=()=>{stateByTrig[el.dataset.ptPts].points=Number(el.value);});
    $$('[data-pt-add]').forEach(el=>el.onclick=()=>{const arr=stateByTrig[el.dataset.ptAdd].tiers;if(arr.length>=5){toast('最多5档');return;}arr.push({from:(arr.at(-1)?.from||0)+5,points:2});draw();});
    $$('[data-pt-tier]').forEach(el=>el.oninput=()=>{const [trig,idx,f]=el.dataset.ptTier.split(':');stateByTrig[trig].tiers[Number(idx)][f]=Number(el.value);});
    $$('[data-pt-del]').forEach(el=>el.onclick=()=>{const [trig,idx]=el.dataset.ptDel.split(':');stateByTrig[trig].tiers.splice(Number(idx),1);draw();});
  }
  draw();
}
