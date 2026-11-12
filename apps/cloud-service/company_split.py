"""Company split accounts and ledger, simulated only, in the caller transaction."""
from common import *

SPLIT_ACTIONS = frozenset('ops.companySplit.' + x for x in ('open', 'rebind', 'query', 'withdraw'))


class CompanySplitMixin:
    def require_company_split(self, driver):
        link = self.operations.get('driverCompany', driver)
        cid = link.get('companyId') if link else None
        account = self.operations.get('companySplitAccount', cid) if cid else None
        require(not cid or (account and account.get('status') == 'SIGNED'),
                '所属出租车公司尚未完成分账账户绑卡开户（模拟签约），请联系后台开通后再上线接单', 403,
                {'reason': 'COMPANY_SPLIT_ACCOUNT_REQUIRED', 'companyId': cid})

    def company_split_action(self, action, p, actor):
        from ops_extras import integer
        ops = self.operations
        cid = ops.identifier(p.get('companyId'))
        company = ops.get('company', cid)
        require(company is not None, '公司不存在', 404)
        before = ops.get('companySplitAccount', cid)
        if action == 'ops.companySplit.query':
            ops.fields(p, {'companyId'}, {'companyId'})
            value = {**clone(before or {'companyId': cid, 'status': 'UNSIGNED', 'version': 0}),
                     'relationStatus': 'SIGNED' if before and before['status'] == 'SIGNED' else 'UNSIGNED',
                     'queryTime': stamp(), 'channelNotice': '模拟扫呗分账关系查询（未连接真实扫呗接口）'}
            self.audit(actor, action, None, {'companyId': cid, 'relationStatus': value['relationStatus']})
            return {'companySplit': value}
        ops.expected(before, p)
        version = before['version'] + 1 if before else 1
        if action in ('ops.companySplit.open', 'ops.companySplit.rebind'):
            ops.fields(p, {'companyId', 'expectedVersion', 'accountName', 'bankName', 'bankAccount', 'contactMobile'},
                       {'companyId', 'expectedVersion', 'accountName', 'bankName', 'bankAccount'})
            require(company.get('taxNumber'), '请先在“公司与客服电话”维护出租车公司商户税号与开票信息', 409)
            require(not before if action.endswith('.open') else bool(before),
                    '账户已开户，请使用换绑' if action.endswith('.open') else '请先开户', 409)
            bank = p['bankAccount']
            require(isinstance(bank, str) and re.fullmatch(r'\d{8,30}', bank), '银行卡号须为8至30位数字')
            mobile = p.get('contactMobile') or ''
            require(isinstance(mobile, str) and (not mobile or re.fullmatch(r'1\d{10}', mobile)), '联系电话格式无效')
            virtual = 'SBVA' + ''.join(str(secrets.randbelow(10)) for _ in range(16))
            while before and virtual == before['virtualAccount']:
                virtual = 'SBVA' + ''.join(str(secrets.randbelow(10)) for _ in range(16))
            value = {**clone(before or {}), 'id': cid, 'companyId': cid, 'status': 'SIGNED',
                     'accountName': textfield(p['accountName'], '账户名称', 1, 100),
                     'bankName': textfield(p['bankName'], '开户银行', 1, 100), 'bankAccountMask': '****' + bank[-4:],
                     'contactMobile': mobile, 'virtualAccount': virtual, 'version': version,
                     'availableBalance': (before or {}).get('availableBalance', 0),
                     'totalCredited': (before or {}).get('totalCredited', 0),
                     'totalWithdrawn': (before or {}).get('totalWithdrawn', 0),
                     'updatedAt': stamp(), 'notice': '模拟扫呗分账账户，未连接银行或扫呗接口'}
        else:
            ops.fields(p, {'companyId', 'expectedVersion', 'amount', 'note'}, {'companyId', 'expectedVersion', 'amount'})
            require(before and before['status'] == 'SIGNED', '请先完成公司分账账户模拟签约', 409)
            amount = integer(p['amount'], '提现金额（分）', 1)
            require(amount <= before['availableBalance'], '公司分账可用余额不足', 409)
            value = {**clone(before), 'availableBalance': before['availableBalance'] - amount,
                     'totalWithdrawn': before.get('totalWithdrawn', 0) + amount, 'version': version, 'updatedAt': stamp()}
            entry = {'id': uid('CSE'), 'companyId': cid, 'type': 'WITHDRAWAL', 'amount': -amount,
                     'availableAfter': value['availableBalance'], 'note': textfield(p.get('note') or '', '备注', 0, 200),
                     'createdAt': stamp(), 'operator': actor, 'simulated': True}
            ops.write('companySplitEntry', entry['id'], entry)
        ops.write('companySplitAccount', cid, value)
        self.audit(actor, action, before, value)  # Never audit the raw bank-account payload.
        return {'companySplit': clone(value)}

    def credit_company_split(self, o):
        s = o.get('settlement') or {}
        cid, amount = o.get('companyId'), s.get('companyCommission', 0)
        if not cid or amount <= 0 or o.get('refundAmount') or o.get('companySplitCredited'):
            return
        # A stable per-order ledger key also protects recovery of legacy credited markers.
        key = 'ORDER:' + o['id']
        if self.operations.get('companySplitEntry', key):
            o['companySplitCredited'] = True
            return
        account = self.operations.get('companySplitAccount', cid)
        require(account and account['status'] == 'SIGNED', '公司分账账户尚未签约', 409)
        account.update(availableBalance=account['availableBalance'] + amount,
                       totalCredited=account['totalCredited'] + amount, version=account['version'] + 1, updatedAt=stamp())
        self.operations.write('companySplitAccount', cid, account)
        self.operations.write('companySplitEntry', key, {'id': key, 'companyId': cid, 'orderId': o['id'],
            'type': 'ORDER_SPLIT', 'amount': amount, 'availableAfter': account['availableBalance'],
            'createdAt': s['settledAt'], 'simulated': True})
        o['companySplitCredited'] = True

    def reverse_company_split(self, o):
        if not o.get('companySplitCredited') or o.get('companySplitReversed'):
            return
        account = self.operations.get('companySplitAccount', o['companyId'])
        amount = o['settlement']['companyCommission']
        require(account and account['availableBalance'] >= amount, '公司分账可用余额不足以冲回，退款需人工规则确认', 409)
        account.update(availableBalance=account['availableBalance'] - amount,
                       totalReversed=account.get('totalReversed', 0) + amount,
                       version=account['version'] + 1, updatedAt=stamp())
        self.operations.write('companySplitAccount', o['companyId'], account)
        key = 'REFUND:' + o['id']
        self.operations.write('companySplitEntry', key, {'id': key, 'companyId': o['companyId'], 'orderId': o['id'],
            'type': 'REFUND_REVERSAL', 'amount': -amount, 'availableAfter': account['availableBalance'],
            'createdAt': stamp(), 'simulated': True})
        o['companySplitReversed'] = True
