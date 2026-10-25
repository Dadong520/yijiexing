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
