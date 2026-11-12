"""修改意见3.0: invoice red-flush (冲红), reissue (重开), passenger reissue
applications and the technical-service-fee (技术服务费) invoices to companies.

Every invoice remains a simulated receipt: no tax platform is contacted.
"""
from common import *
from decimal import Decimal
from pricing import round_cents

DEFAULT_ISSUER = {'name': '', 'taxNumber': '', 'taxRate': 6, 'version': 0}


class InvoiceOpsMixin:
    def find_passenger_invoice(self, pid, invoice_id):
        data = self._passenger_extra_store(pid)
        inv = next((i for i in data['invoices'] if i['id'] == invoice_id), None)
        require(inv is not None, '发票记录不存在', 404)
        return data, inv

    def red_flush(self, data, inv, reason, actor):
        require(inv.get('status') != 'RED_FLUSHED', '此发票已冲红', 409)
        inv.update(status='RED_FLUSHED', redFlushedAt=stamp(), redFlushReason=reason, redFlushedBy=actor,
                   notice='模拟回执已冲红（未连接税务平台）')

    def reissue_invoice(self, pid, data, inv, title_fields, reason, actor):
        require(not inv.get('reissuedAs'), '此发票已重开', 409)
        if inv.get('status') != 'RED_FLUSHED':
            self.red_flush(data, inv, reason, actor)
        row = {'id': uid('SI'), 'orderId': inv['orderId'], 'titleId': None, 'title': title_fields['title'],
               'titleSnapshot': {'id': None, **title_fields}, 'amount': inv['amount'], 'status': 'SIMULATED_ISSUED',
               'simulated': True, 'createdAt': stamp(), 'reissueOf': inv['id'], 'reissueReason': reason,
               'notice': '重开的模拟回执 · 未连接税务开票平台 · 不可报销'}
        inv['reissuedAs'] = row['id']
        data['invoices'].insert(0, row)
        self._save_passenger_extras(pid, data)
        return row

    def passenger_reissue_apply(self, pid, p):
        require(set(p) <= {'invoiceId', 'reason', 'type', 'title', 'taxNumber', 'email'}, '重开申请参数无效')
        data, inv = self.find_passenger_invoice(pid, p.get('invoiceId'))
        require(inv.get('status') == 'SIMULATED_ISSUED', '仅已开具的发票可申请重开', 409)
        pending = [r for r in self.operations.rows('invoiceReissue') if r['invoiceId'] == inv['id'] and r['status'] == 'PENDING']
        require(not pending, '此发票已有待审核的重开申请', 409)
        fields = self.invoice_title_fields(p)
        record = {'id': uid('IR'), 'passengerId': pid, 'invoiceId': inv['id'], 'orderId': inv['orderId'],
                  'amount': inv['amount'], 'oldTitle': inv['title'], 'newTitle': fields, 'status': 'PENDING',
                  'reason': textfield(p.get('reason'), '重开原因', 2, 200), 'createdAt': stamp(), 'version': 1}
        self.operations.write('invoiceReissue', record['id'], record)
        inv['reissueStatus'] = 'PENDING'
        self._save_passenger_extras(pid, data)
        return {'reissue': clone(record)}

    def passenger_reissues(self, pid):
        return [r for r in self.operations.rows('invoiceReissue') if r['passengerId'] == pid]

    # ---- 技术服务费 ------------------------------------------------------
    def invoice_issuer(self):
        return self.operations.get('invoiceIssuer', 'ISSUER') or clone(DEFAULT_ISSUER)

    def tech_fee_rows(self):
        """One row per company per month: 平台佣金合计 of settled orders."""
        groups = {}
        companies = {c['id']: c for c in self.operations.rows('company')}
        for r in self.db.execute('SELECT payload FROM passenger_orders'):
            o = json.loads(r['payload'])
            s = o.get('settlement')
            if not s or not o.get('companyId') or o.get('refundAmount'):
                continue
            month = s['settledAt'][:7]
            g = groups.setdefault((o['companyId'], month), {'companyId': o['companyId'], 'month': month, 'orderCount': 0,
                                                             'platformCommission': 0, 'orderIds': []})
            g['orderCount'] += 1; g['platformCommission'] += s['platformCommission']; g['orderIds'].append(o['id'])
        issuer = self.invoice_issuer()
        rows = []
        for (cid, month), g in sorted(groups.items(), key=lambda kv: (kv[0][1], kv[0][0]), reverse=True):
            c = companies.get(cid, {})
            issued = self.operations.get('techInvoice', cid + ':' + month)
            rows.append({**g, 'id': cid + ':' + month, 'companyName': c.get('name'), 'companyTitle': c.get('invoiceTitle') or c.get('name'),
                         'companyTaxNumber': c.get('taxNumber') or '', 'taxRate': issuer['taxRate'],
                         'invoiceStatus': issued['status'] if issued else 'NOT_ISSUED', 'issued': issued,
                         'amountChanged': bool(issued and issued['amount'] != g['platformCommission'])})
        return rows

    def issue_tech_invoice(self, p, actor):
        require(set(p) == {'companyId', 'month', 'expectedAmount'}, '开票参数无效')
        row = next((r for r in self.tech_fee_rows() if r['companyId'] == p['companyId'] and r['month'] == p['month']), None)
        require(row is not None, '该公司当月没有可开票的技术服务费', 404)
        require(type(p['expectedAmount']) is int and row['platformCommission'] == p['expectedAmount'], '金额已变化，请刷新后重新确认', 409)
        require(not row['issued'], '该月技术服务费已开票', 409)
        issuer = self.invoice_issuer()
        require(issuer['name'] and issuer['taxNumber'], '请先维护开票方（平台）名称与税号', 409)
        require(row['companyTaxNumber'], '请先在“公司与客服电话”维护该公司开票抬头与税号', 409)
        amount = row['platformCommission']
        tax = round_cents(Decimal(amount) * issuer['taxRate'] / (100 + issuer['taxRate']))
        value = {'id': row['id'], 'companyId': row['companyId'], 'month': row['month'], 'amount': amount,
                 'taxRate': issuer['taxRate'], 'taxAmount': tax, 'amountExcludingTax': amount - tax,
                 'buyerTitle': row['companyTitle'], 'buyerTaxNumber': row['companyTaxNumber'],
                 'sellerName': issuer['name'], 'sellerTaxNumber': issuer['taxNumber'], 'orderCount': row['orderCount'],
                 'status': 'SIMULATED_ISSUED', 'issuedAt': stamp(), 'issuedBy': actor, 'version': 1,
                 'notice': '技术服务费模拟发票 · 未连接税务开票平台'}
        self.operations.write('techInvoice', value['id'], value)
        return {'techInvoice': value}
