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

function renderSummaryCards(summary, definition) {
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

  const defHtml = definition ? `<div class="filter-summary-definition" style="font-size:12px;color:#475569;margin-top:6px;">口径：${esc(definition)} (UTC+8，同条件全匹配汇总)</div>` : '';
  const missingHtml = (summary?.missingDateCount > 0) ? `<div style="font-size:12px;color:#b45309;margin-top:4px;">${esc(String(summary.missingDateCount))} 条历史数据缺失登记时间，已在时间筛选中排除</div>` : '';

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
  try {
    const sp = queryParams(key, false);
    resData = await api('query?' + sp.toString());
    view.lastResponse = resData;
  } catch (e) {
    if (curTicket !== queryTicket || curNavPage !== page) return null;
    queryError = e.message || '查询请求失败';
    if (!view.lastResponse) { tableSec.innerHTML = ''; showErr(queryError, true); return []; }
    resData = view.lastResponse;
  }

  if (curTicket !== queryTicket || curNavPage !== page) return null;

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

  summarySec.innerHTML = queryError ? '' : renderSummaryCards(view.summary, view.definition);

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
        ...fieldOpts.map(o => `<option value="${esc(String(o.value))}" ${String(curVal) === String(o.value) ? 'selected' : ''}>${esc(queryChoiceLabel(o.label || o.value))}</option>`)
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
    const rowsHtml = items.map(it => row(it));
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
