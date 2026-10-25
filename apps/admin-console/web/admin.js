'use strict';
const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)],esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=v=>'¥'+(Number(v||0)/100).toFixed(2), pretty=v=>esc(JSON.stringify(v,(key,value)=>['paircode','pairuri','code_hash'].includes(key.toLowerCase())?undefined:value,2)),time=v=>v&&Number.isFinite(new Date(v).getTime())?esc(new Date(v).toLocaleString('zh-CN',{hour12:false,timeZone:'Asia/Shanghai'})):'未记录';
let session='',state={},page='overview',query='',status='',pageNo=1,loading=false;
const pages=[['overview','◈','经营概览'],['statistics','▥','经营报表'],['cancellations','▧','取消订单记录'],['refunds','¥','模拟退款记录'],['orders','▤','订单管理'],['drivers','♙','司机与车辆'],['passengers','♧','乘客管理'],['payments','¥','模拟账务'],['support','◇','评价投诉反馈'],['configs','⚙','城市与会员'],['users','♙','账号与权限'],['audit','≡','操作日志']];
const labels={AVAILABLE:'待接单',WAIT_CONTINUE:'等待乘客继续叫车',BID_SENT:'已报价',PREPAY:'待预付',RESERVED:'预约待出发',PICKUP:'接驾中',ARRIVED:'已到达',TRIP:'行程中',WAIT_PAY:'待付款',DONE:'已完成',CANCELLED:'已取消',PENDING:'待处理',APPROVED:'已通过',REJECTED:'已驳回',SUCCESS:'模拟支付成功',FAILED:'失败',UNPAID:'未支付',REFUNDED:'已模拟退款',PROCESSING:'处理中',REPLIED:'已回复',RESOLVED:'已处理',STANDARD:'即时用车',BID:'竞价用车',SIMULATED:'模拟',NEGOTIATED:'线下议价',OFFERED:'已报价，待乘客确认',DISPUTED:'乘客提出价格异议',ACCEPTED:'乘客已确认报价',ACTIVE:'启用',DISABLED:'禁用',WAIT_FIRST_APPROVAL:'待一级审批',WAIT_SECOND_APPROVAL:'待二级审批',WAIT_APPROVAL:'待审批',SCHEDULED:'定时待生效',APPLIED:'已生效',SKIPPED:'已跳过',READY:'待下发',RECORDED:'已记录',SANDBOX_APPROVED:'模拟审核通过'};
const label=v=>labels[v]||v||'—', badge=v=>`<span class="badge ${['DONE','SUCCESS','APPROVED','RESOLVED','REPLIED','ACTIVE'].includes(v)?'good':['FAILED','REJECTED','DISABLED'].includes(v)?'bad':['PENDING','WAIT_PAY','PREPAY'].includes(v)?'warn':''}">${esc(label(v))}</span>`;
function toast(msg){$('#toast').textContent=msg;$('#toast').style.opacity=1;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('#toast').style.opacity=0,5000)}
async function api(path,body){const r=await backendFetch('/api/local/admin/v2/'+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json','X-Admin-Session':session},body:body?JSON.stringify(body):undefined});if(r.status===401&&path!=='login'){session='';state={};queryViews.clear();$('#app').hidden=true;$('#login').hidden=false;$('#loginError').textContent='管理会话已失效，请重新登录';throw Error('管理会话已失效，请重新登录')}let j;try{j=await r.json()}catch(e){if(isConnectionError(e))throw connectionError(e);throw Error('服务返回异常，请确认本机入口与云服务已启动后重试')}if(!r.ok||j.code!==0)throw Error((r.status===403?'权限不足：':'')+(j.message||`请求失败 (${r.status})`));return j.data}
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
async function render(){const found=pages.find(x=>x[0]===page);$('#pageTitle').textContent=found[2];$('#identity').textContent=`${state.user?.username||''} · ${roleLabel(state.user?.role)}`;$$('nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));try{await ({overview,orders,drivers,passengers,payments,support,configs,users,audit,statistics,cancellations,refunds}[page])();applyPermissions()}catch(e){fail(e)}}
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
    toast('已导出当前已应用条件的全部匹配记录，操作已记入审计');
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
            return c && c.status === 'PENDING';
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
function orderDetail(id){const o=dataset('orders').find(x=>x.id===id);modal('订单详情 · '+id,(o.stage==='WAIT_CONTINUE'?'<p class="notice">权威订单状态：等待乘客继续叫车。原司机已取消，其司机端显示已取消；乘客可继续叫车或取消本次订单。此时暂未匹配新的司机。</p>':'')+(o.stage==='WAIT_PAY'&&Number.isInteger(o.fareVersion)?'<p class="notice">行程已结束，等待乘客付款。该单不占用司机的新接单资格；该乘客结清前不能再次叫车。调价后需重新确认金额与费用版本。</p>':'')+details({'费用版本':Number.isInteger(o.fareVersion)?'V'+o.fareVersion:'当前服务未提供','费用口径':o.destinationPending===true?'目的地待补充，费用待定，不代表免费。导出保留后端原始金额分值。':'体验估价或已确认报价；金额单位为元。','订单类型':esc(kindLabel(o.kind)),'订单状态':badge(o.stage),'行程':esc(o.origin)+' → '+esc(o.destination),'乘客':esc(o.passengerId||o.passenger?.id),'司机':esc(o.driver?.name||o.driverId||'未选择'),'金额':orderAmount(o),'支付':badge(o.paymentStatus)+' · 模拟账务','取消原因':esc(o.cancelReason),'创建时间':time(o.createdAt)})+`<div class="toolbar">${!['TRIP','WAIT_PAY','DONE','CANCELLED'].includes(o.stage)?'<button id="cancelOrder" class="danger">人工取消</button>':''}${o.stage==='WAIT_PAY'&&!o.paid?'<button id="adjustFare">调整体验费用</button>':''}${o.stage==='DONE'&&o.paid&&!o.refundAmount?'<button id="refund">模拟退款</button>':''}</div>${quotationDetails(o)}<h3 style="margin-top:28px">乘客评价</h3><p>${o.evaluation?`${'★'.repeat(o.evaluation.rating)} · ${esc(o.evaluation.content)}`:'尚未评价'}</p><h3>状态与操作时间线</h3><div class="timeline">${(o.events||[]).map(eventRow).join('')||'暂无事件'}</div><details><summary>完整共享订单记录</summary><pre class="code">${pretty(o)}</pre></details>`,[['关闭','',close]]);if($('#cancelOrder'))$('#cancelOrder').onclick=()=>confirmAction('人工取消订单','order.cancel',{id,expectedStage:o.stage,expectedFare:o.fare,fareVersion:o.fareVersion??0},'',`订单 ${id} 将取消；已模拟预付金额按服务端规则处理，不能恢复原状态。`);if($('#adjustFare'))$('#adjustFare').onclick=()=>confirmAction('调整体验费用','order.adjustFare',()=>({id,expectedStage:o.stage,expectedFare:o.fare,fareVersion:o.fareVersion??0,amount:cents($('#fare').value)}),`<label>新金额（元）<input id="fare" type="number" min="0.01" step="0.01" value="${(o.fare/100).toFixed(2)}"></label>`);if($('#refund'))$('#refund').onclick=()=>confirmAction('模拟全额退款','order.refund',{id,expectedStage:o.stage,expectedFare:o.fare,fareVersion:o.fareVersion??0},'',`模拟退还 ${orderAmount(o)}；若金额或版本已变化，须刷新后重新确认。不发生真实资金交易。`) }
function cents(v){if(!/^\d+(\.\d{1,2})?$/.test(v))throw Error('金额须为非负数且最多两位小数');return Math.round(Number(v)*100)}
async function drivers(){const rows=await queryListing('drivers','司机与车辆','共享司机资料、车辆信息与资质审核',['司机','手机 / 城市','车辆','会员','资质','操作'],d=>`<tr><td><b>${esc(d.name)}</b><small>${esc(d.id)}</small></td><td>${esc(d.mobile)}<small>${esc(cityLabel(d.city))}</small></td><td>${esc(d.plate||d.vehicle?.plate||d.profile?.plate)}<small>${esc(d.vehicleModel||d.vehicle?.model||d.profile?.vehicleModel||'')}</small></td><td>${esc(memberLevelLabel(d.currentLevel))}</td><td>${(d.certificates||[]).filter(c=>c.status==='PENDING').length} 项待审</td><td><button data-driver="${esc(d.id)}">资料与审核</button></td></tr>`);if(!rows)return;$$('[data-driver]').forEach(b=>b.onclick=()=>driverDetail(b.dataset.driver))}
function driverDetail(id){const d=dataset('drivers').find(x=>x.id===id);modal('司机资料 · '+d.name,details({'司机编号':esc(id),'手机':esc(d.mobile),'城市':esc(cityLabel(d.city)),'会员等级':esc(memberLevelLabel(d.currentLevel)),'模拟钱包':money(d.balance)})+`<h3>资质审核</h3>${(d.certificates||[]).map((c,i)=>`<section class="panel"><h3>${esc(c.title||c.name||c.type)} ${badge(c.status)}</h3><p>${esc(c.rejectionReason||c.reply||'')}</p><div class="toolbar"><button data-preview="${i}" data-side="front">查看正面 / 主附件</button><button data-preview="${i}" data-side="back">查看背面附件</button></div><div id="preview-${i}"></div><details><summary>证件资料和审核记录</summary><pre class="code">${pretty(c)}</pre></details>${c.status==='PENDING'?`<button data-cert="${i}" data-approve="true">通过审核</button> <button data-cert="${i}" data-approve="false">驳回补充</button>`:''}</section>`).join('')}<details><summary>完整资料</summary><pre class="code">${pretty(d)}</pre></details>`,[['关闭','',close]]);$$('[data-preview]').forEach(b=>b.onclick=async()=>{b.disabled=true;const target=$('#preview-'+b.dataset.preview);target.textContent='正在读取授权附件…';try{const result=await api('certificate-file?driverId='+encodeURIComponent(id)+'&type='+encodeURIComponent(d.certificates[Number(b.dataset.preview)].type)+'&side='+b.dataset.side);if(!/^image\/(png|jpeg|webp|gif)$/.test(result.mimeType))throw Error('附件不是可预览的图片类型');target.replaceChildren();const img=document.createElement('img');img.src='data:'+result.mimeType+';base64,'+result.imageBase64;img.alt='已授权的司机证件附件';img.style.cssText='max-width:100%;max-height:450px;margin:15px 0;object-fit:contain';target.append(img)}catch(e){target.textContent=e.message}finally{b.disabled=false}});$$('[data-cert]').forEach(b=>b.onclick=()=>confirmAction('司机资质审核','certificate.audit',{driverId:id,type:d.certificates[Number(b.dataset.cert)].type,approved:b.dataset.approve==='true'}))}
async function passengers(){const rows=await queryListing('passengers','乘客管理','乘客账号资料及关联共享订单',['乘客','手机','城市','共享订单','操作'],p=>`<tr><td>${esc(p.name||p.profile?.name)}<small>${esc(p.id)}</small></td><td>${esc(p.mobile)}</td><td>${esc(cityLabel(p.city||p.profile?.city))}</td><td>${dataset('orders').filter(o=>o.passengerId===p.id).length}</td><td><button data-passenger="${esc(p.id)}">查看资料</button></td></tr>`);if(!rows)return;$$('[data-passenger]').forEach(b=>b.onclick=()=>{const p=dataset('passengers').find(x=>x.id===b.dataset.passenger);modal('乘客资料',details({'乘客姓名':esc(p.name||p.profile?.name),'乘客编号':esc(p.id),'手机号码':esc(p.mobile),'所在城市':esc(p.cityName||cityLabel(p.city||p.profile?.city)),'注册时间':time(p.createdAt),'最近更新':time(p.updatedAt)})+`<h3>关联订单</h3>${dataset('orders').filter(o=>o.passengerId===p.id).map(o=>`<section class="panel"><h3>${esc(kindLabel(o.kind))} · ${badge(o.stage)}</h3><p>${esc(o.origin)} → ${esc(o.destination)}</p>${details({'订单编号':esc(o.id),'订单金额':orderAmount(o),'创建时间':time(o.createdAt),'支付状态':badge(o.paymentStatus)})}</section>`).join('')||'<p class="muted">暂无共享订单</p>'}${rawRecord(p)}`,[['关闭','',close]])})}
async function payments(){const rows=await queryListing('payments','模拟账务','共享订单费用为当前状态台账；无终点订单费用待定，导出保留原始金额分值；不涉及真实资金交易',['记录编号','订单 / 司机','类型','金额','状态','时间','操作'],p=>`<tr><td>${esc(p.id||p.orderId)}</td><td>${esc(p.orderId||p.driverId)}</td><td>${esc(p.recordType==='WITHDRAWAL'?'模拟提现':'订单模拟费用')}</td><td>${paymentAmount(p)}</td><td>${badge(p.status)}</td><td>${time(p.createdAt||p.at)}</td><td><button data-payment="${esc(p.id||p.orderId)}">查看记录</button></td></tr>`);if(!rows)return;$$('[data-payment]').forEach(b=>b.onclick=()=>{const p=rows.find(x=>(x.id||x.orderId)===b.dataset.payment);modal('模拟账务记录',`<p class="notice">此记录为独立体验环境中的模拟账务，不发生真实支付、退款或银行打款。</p>`+details({'记录编号':esc(p.id||p.orderId),'记录类型':esc(p.recordType==='WITHDRAWAL'?'模拟提现':'订单模拟费用'),'关联订单':esc(p.orderId),'乘客账号':esc(accountLabel(p.passengerId,'passengers')),'司机账号':esc(accountLabel(p.driverId,'drivers')),'订单或提现金额':paymentAmount(p),'当前状态':badge(p.status),'累计模拟退款':money(p.refundAmount),'当前轮模拟退款':p.currentRefundAmount==null?'当前服务未提供分项':money(p.currentRefundAmount),'历史轮模拟退款':p.historicalRefundAmount==null?'当前服务未提供分项':money(p.historicalRefundAmount),'支付时间':time(p.paidAt),'退款时间':time(p.refundedAt),'创建时间':time(p.createdAt||p.at),'审核时间':time(p.auditedAt),'处理意见':esc(p.reply||p.reason)})+refundDetails(p)+rawRecord(p),[['关闭','',close],...(p.recordType==='WITHDRAWAL'&&p.status==='PENDING'&&can('wallet.withdrawal.audit')?[['审核通过','primary',()=>confirmAction('模拟提现审核通过','wallet.withdrawal.audit',{driverId:p.driverId,id:p.id,approved:true},'','仅记录模拟审核通过，不发起银行转账。')],['驳回提现','danger',()=>confirmAction('驳回模拟提现','wallet.withdrawal.audit',{driverId:p.driverId,id:p.id,approved:false},'','驳回后返回对应模拟余额。')]]:[])])})}

async function support(){const rows=await queryListing('support','评价投诉与反馈','处理结果保存后两端刷新可查 · 评价随订单保留',['记录 / 来源','关联账号 / 订单','内容','状态','回复','操作'],s=>`<tr><td>${esc(s.id)}<small>${esc(sourceLabel(s.source))}</small></td><td>${esc(s.ownerId)}<small>${esc(s.orderId||'')}</small></td><td title="${esc(s.content)}">${esc((s.content||s.reason||'').slice(0,45))}</td><td>${badge(s.status)}</td><td>${esc((s.reply||'').slice(0,35))}</td><td><button data-support="${esc(s.rowId||s.id)}">查看与处理</button></td></tr>`);if(!rows)return;$$('[data-support]').forEach(b=>b.onclick=()=>{const s=rows.find(x=>(x.rowId||x.id)===b.dataset.support);modal('投诉 / 反馈详情',`${!can('support.reply')?'<p class="notice">当前角色仅可查看，无投诉反馈处理权限。</p>':''}${details({'记录编号':esc(s.id),'来源类型':esc(sourceLabel(s.source)),'处理状态':badge(s.status),'关联账号':esc(s.source==='ORDER_COMPLAINT'?accountLabel(dataset('orders').find(o=>o.id===s.ownerId)?.passengerId,'passengers'):accountLabel(s.ownerId,s.source?.startsWith('DRIVER')?'drivers':'passengers')),'关联订单':esc(s.orderId||(s.source==='ORDER_COMPLAINT'?s.ownerId:null)),'提交时间':time(s.createdAt),'问题类型':esc(s.reason),'评价星级':s.rating?esc(s.rating)+' / 5':'—'})}<h3>${s.source==='EVALUATION'?'评价内容':'问题内容'}</h3><p class="record-text">${esc(s.content||'未填写补充内容')}</p><h3>处理回复</h3><p class="record-text">${esc(s.reply||'尚未回复')}</p>${details({'处理人':esc(s.handledBy||s.repliedBy),'处理时间':time(s.repliedAt||s.handledAt)})}${rawRecord(s)}`,[['关闭','',close],...(s.source==='EVALUATION'||!can('support.reply')?[]:[['处理并回复','primary',()=>confirmAction('处理投诉与反馈','support.reply',{id:s.id,source:s.source,ownerId:s.ownerId})]])])})}
function configs(){listing('cities','城市与会员配置','城市字段继承：城市 → 大区 → 全国；价格和权益使用两端同源配置',['城市','大区','体系','普通年价','VIP年价','来源','操作'],c=>`<tr><td>${esc(c.name)}<small>${esc(c.id)}</small></td><td>${esc(c.regionName)}</td><td>${badge(c.systemEnabled?'ACTIVE':'DISABLED')}</td><td>${money(c.normalPrice)}</td><td>${money(c.vipPrice)}</td><td>${esc(c.source)}</td><td><button data-city="${esc(c.id)}">配置</button></td></tr>`,{buttons:'<button id="national">全国会员权益</button><button id="regions">大区配置</button><button id="batchConfig">批量城市配置</button><button id="approvalsList">二级审批</button><button id="scheduledList">定时任务</button><button id="batchesList">执行记录</button>'});$$('[data-city]').forEach(b=>b.onclick=()=>configForm('city',b.dataset.city));$('#batchConfig').onclick=batchForm;$('#approvalsList').onclick=()=>workflowList('approvals');$('#scheduledList').onclick=()=>workflowList('scheduled');$('#batchesList').onclick=()=>workflowList('batches');$('#national').onclick=()=>configForm('national','CN');$('#regions').onclick=()=>{const regions=dataset('configs').filter(c=>c.scope==='region');modal('大区配置',regions.length?regions.map(c=>`<p><button data-region="${esc(c.target)}">${esc(c.target==='CENTRAL'?'华中大区':c.target==='SOUTH'?'华南大区':c.target)} · V${c.version}</button></p>`).join(''):'<p>没有可配置的大区记录</p>',[['关闭','',close]]);$$('[data-region]').forEach(b=>b.onclick=()=>configForm('region',b.dataset.region))}}
function configForm(scope,target){const c=dataset('configs').find(x=>x.scope===scope&&x.target===target);if(!c){toast('缺少此配置的数据或读取权限');return}const value=c.draft||c.published,city=dataset('cities').find(x=>x.id===target),national=dataset('configs').find(x=>x.scope==='national')?.published||{},levels=value.levels||{};const effective=(code)=>({...national.levels?.[code],...city?.levels?.find(x=>x.id===code),...levels[code]});const names={login:'登录',online:'上线',bank:'银行卡',nearby:'近距离开单',allArea:'全区域接单',inApp:'应用提醒',floating:'浮窗提醒',voice:'语音播报',wallet:'钱包',withdraw:'模拟提现',password:'交易密码',map:'地图',invoice:'发票入口',instant:'即时订单',reservation:'预约订单',dispatch:'调度',offlineNegotiation:'线下议价',bidding:'竞价',vehicleChange:'更换车辆',vipBadge:'VIP标识',heatmap:'热力图',ranking:'排名',blacklist:'黑名单',support:'客服',update:'版本更新'};modal('配置编辑 · '+(city?.name||(scope==='region'?'大区 '+target:'全国会员权益')),`<p class="muted">发布 V${c.version} / 编辑 R${c.revision}。保存草稿不会影响 App，发布后刷新生效。</p>${scope==='national'?'':`<label><input id="systemEnabled" type="checkbox" ${(value.systemEnabled??city?.systemEnabled??national.systemEnabled??true)?'checked':''}>启用本${scope==='region'?'大区':'城市'}会员等级体系</label>`}<div class="grid2"><label>体验天数<input id="trialDays" type="number" min="1" max="365" value="${value.trialDays??national.trialDays??30}"></label><label>体验完成单上限<input id="trialOrders" type="number" min="1" max="10000" value="${value.trialOrders??national.trialOrders??15}"></label></div>${(scope==='national'?['TRIAL','NORMAL','VIP']:['NORMAL','VIP']).map(code=>{const l=effective(code);return `<section class="panel"><h3>${code==='TRIAL'?'体验司机权益':code==='VIP'?'VIP会员':'普通会员'}</h3>${code==='TRIAL'?'<p class="muted">免费体验，不设置收费价格；有效期按体验天数及完成单量上限判断。</p>':`<label>年度价格（元）<input data-price="${code}" type="number" min="0.01" step="0.01" value="${(Number(l.price||0)/100).toFixed(2)}"></label>`}${scope==='national'?`<div class="grid2">${Object.entries(l.rights||{}).map(([k,v])=>`<label><input type="checkbox" data-right="${code}:${k}" ${v?'checked':''}>${esc(names[k]||k)}</label>`).join('')}</div>`:''}${code==='TRIAL'?'':`<p class="muted">售卖状态：${l.saleEnabled===false?'停售':'开放'}。停售须多级审批，未审批不得改变已有会员权益。</p>`}</section>`}).join('')}<label>修改原因<textarea id="configReason"></textarea></label><details><summary>完整配置（只读）</summary><pre class="code">${pretty(value)}</pre></details>`,[['关闭','',close],['保存草稿','primary',async()=>{const reason=$('#configReason').value.trim();if(reason.length<2)throw Error('请填写修改原因');const patch={trialDays:Number($('#trialDays').value),trialOrders:Number($('#trialOrders').value),levels:{}};if($('#systemEnabled'))patch.systemEnabled=$('#systemEnabled').checked;$$('[data-price]').forEach(el=>{(patch.levels[el.dataset.price]??={}).price=cents(el.value)});$$('[data-right]').forEach(el=>{const [code,key]=el.dataset.right.split(':');((patch.levels[code]??={}).rights??={})[key]=el.checked});await act('config.draft',{scope,target,expectedRevision:c.revision,patch,reason});close();toast('草稿已保存，请重新打开配置核对并发布')}],...(c.draft?[['发布已保存草稿','primary',()=>confirmAction('发布配置草稿','config.publish',{scope,target,expectedRevision:c.revision,subscriptionPolicy:'KEEP_UNTIL_EXPIRY'},'','将提交上次保存的草稿，当前未保存的修改不提交。开启或调价可直接发布；关闭体系会进入二级审批，原配置暂不生效、存量会员权益保留至到期。')]]:[])])}

function users(){listing('users','账号与权限','角色权限由服务器校验，界面隐藏或禁用不替代接口鉴权',['用户名','角色','状态','更新时间','操作'],u=>`<tr><td>${esc(u.username)}</td><td>${esc(roleLabel(u.role))}<small>${esc(u.role)}</small></td><td>${badge(u.enabled===false?'DISABLED':'ACTIVE')}</td><td>${time(u.updatedAt||u.createdAt)}</td><td><button data-user="${esc(u.username)}">编辑权限</button></td></tr>`,{buttons:'<button id="newUser" class="primary">新增账号</button><button id="roles">角色权限表</button>'});$('#newUser').onclick=()=>userForm();$$('[data-user]').forEach(b=>b.onclick=()=>userForm(dataset('users').find(u=>u.username===b.dataset.user)));$('#roles').onclick=()=>modal('角色权限表',roleCards(),[['关闭','',close]])}
function userForm(u={}){const roles=Array.isArray(state.roles)?state.roles.map(r=>typeof r==='string'?r:r.id||r.name):Object.keys(state.roles||{});modal(u.username?'编辑管理账号':'新增管理账号',`<label>用户名<input id="userName" value="${esc(u.username||'')}" ${u.username?'readonly':''}></label><label>密码${u.username?'（留空保持原密码）':''}<input type="password" id="userPassword" autocomplete="new-password"></label><label>角色<select id="userRole">${roles.map(r=>`<option value="${esc(r)}" ${r===u.role?'selected':''}>${esc(roleLabel(r))}（${esc(r)}）</option>`).join('')}</select></label><label><input id="userDisabled" type="checkbox" ${u.enabled===false?'checked':''}>禁用账号</label><label>修改原因<textarea id="userReason"></textarea></label>`,[['关闭','',close],['保存账号','primary',async()=>{const username=$('#userName').value.trim(),password=$('#userPassword').value,reason=$('#userReason').value.trim();if(!username||reason.length<2)throw Error('用户名和至少两个字符的修改原因必填');if(!u.username&&password.length<12)throw Error('密码至少 12 位');await act('user.save',{username,password,role:$('#userRole').value,enabled:!$('#userDisabled').checked,reason,confirmed:true});close();toast('账号已保存')} ]])}
async function audit(){const rows=await queryListing('audit','操作日志','由服务端记录操作者、动作与变更前后数据',['编号','操作者','动作','时间','操作'],a=>`<tr><td>${esc(a.id)}</td><td>${esc(a.actor)}</td><td>${esc(a.action)}</td><td>${time(a.at||a.createdAt||a.created)}</td><td><button data-audit="${esc(a.id)}">变更详情</button></td></tr>`);if(!rows)return;$$('[data-audit]').forEach(b=>b.onclick=()=>modal('操作审计',`<pre class="code">${pretty(rows.find(a=>String(a.id)===b.dataset.audit))}</pre>`,[['关闭','',close]]))}
const navPaths={overview:'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',orders:'M8 4H5v17h14V4h-3M8 2h8v5H8zM8 11h8M8 15h6',drivers:'m3 15 2-8h14l2 8v5h-3v-3H6v3H3zM5 11h14M7 14h1M16 14h1',passengers:'M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8M2 21v-2a6 6 0 0 1 12 0v2M17 4a4 4 0 0 1 0 8M17 15a5 5 0 0 1 5 5',payments:'M3 5h17v16H3zM3 5V3h14M15 11h6v5h-6z',support:'M4 4h16v12H9l-5 5zM8 8h8M8 12h5',configs:'M4 6h16M4 12h16M4 18h16M8 3v6M16 9v6M10 15v6',users:'m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6zM8 12l3 3 5-6',audit:'M8 3h12v18H4V7zM8 3v5H4M8 12h8M8 16h8'};
navPaths.statistics=navPaths.overview;navPaths.cancellations=navPaths.orders;navPaths.refunds=navPaths.payments;
const navGroups=[['工作台',['overview','statistics']],['业务运营',['orders','cancellations','drivers','passengers','payments','refunds','support']],['配置与治理',['configs','users','audit']]];
$('#nav').innerHTML=navGroups.map(([title,keys])=>`<div class="nav-section">${title}</div>`+keys.map(key=>{const item=pages.find(p=>p[0]===key);return `<button data-page="${key}"><span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"><path d="${navPaths[key]}"></path></svg></span>${esc(item[2])}</button>`}).join('')).join('');$$('[data-page]').forEach(b=>b.onclick=()=>navigate(b.dataset.page));$('#closeModal').onclick=close;$('#refreshBtn').onclick=refresh;$('#logoutBtn').onclick=async()=>{try{await api('logout',{})}catch(e){toast(e.message)}session='';state={};$('#password').value='';$('#app').hidden=true;$('#login').hidden=false;close()};$('#loginForm').onsubmit=async e=>{e.preventDefault();$('#loginBtn').disabled=true;$('#loginError').textContent='';try{const d=await api('login',{username:$('#username').value.trim(),password:$('#password').value});session=d.session;$('#password').value='';state=await api('state');$('#login').hidden=true;$('#app').hidden=false;render();window.scrollTo(0,0);$('#sync').textContent='登录成功 · 点击刷新获取两端最新数据'}catch(e){session='';$('#loginError').textContent=e.message}finally{$('#loginBtn').disabled=false}};

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
