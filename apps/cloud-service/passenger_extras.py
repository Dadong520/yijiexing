"""Passenger-owned supporting records for the isolated demo. No provider calls."""
import json
import re
from common import ApiError, clone, dumps, require, stamp, textfield, uid


class PassengerExtrasMixin:
    def _passenger_extra_store(self, pid):
        self.passenger_profile(pid)  # Authentication/identity must exist first.
        self.db.execute('CREATE TABLE IF NOT EXISTS passenger_extras(pid TEXT PRIMARY KEY,payload TEXT NOT NULL)')
        row = self.db.execute('SELECT payload FROM passenger_extras WHERE pid=?', (pid,)).fetchone()
        data = json.loads(row['payload']) if row else {'riders': [], 'invoiceTitles': [], 'invoices': []}
        data.setdefault('blockedDrivers', [])
        return data

    def _save_passenger_extras(self, pid, data):
        self.db.execute('INSERT OR REPLACE INTO passenger_extras(pid,payload) VALUES (?,?)', (pid, dumps(data)))

    @staticmethod
    def _owned_extra(items, record_id, label):
        value = next((item for item in items if item['id'] == record_id), None)
        require(value is not None, label + '不存在', 404)
        return value

    def passenger_rider(self, pid, rider_id):
        return clone(self._owned_extra(self._passenger_extra_store(pid)['riders'], rider_id, '乘车人'))

    def passenger_extra_data(self, pid):
        data = clone(self._passenger_extra_store(pid))
        # A capability describes the independently configured experience, not an
        # assertion that a real external provider has been integrated.
        data['support'] = {
            'phone': None, 'phoneConfigured': False,
            'notice': '后台可查看和回复已保存投诉；实时人工客服、客服电话、电话中继和推送未配置。',
            'notices': [
                {'id': 'EXPERIENCE_BOUNDARY', 'title': '独立体验环境说明',
                 'content': '乘客和司机共享真实创建的体验订单；验证码回显，支付及退款为模拟，不产生真实资金收付。'},
                {'id': 'MAP_BOUNDARY', 'title': '地图与路线',
                 'content': '路线为坐标连线示意，不是道路导航或实际行驶轨迹。'},
                {'id': 'INVOICE_BOUNDARY', 'title': '模拟开票说明',
                 'content': '可以保存发票抬头并申请模拟回执；回执不是税务发票，不能报销，未连接开票平台。'},
            ],
            'capabilities': [
                {'key': 'sharedOrders', 'label': '两端共享订单', 'status': 'AVAILABLE', 'notice': '独立体验数据库持久化'},
                {'key': 'riders', 'label': '乘车人管理', 'status': 'AVAILABLE', 'notice': '按登录乘客隔离保存'},
                {'key': 'sms', 'label': '短信验证码', 'status': 'SIMULATED', 'notice': '验证码回显，不发送真实短信'},
                {'key': 'payment', 'label': '支付与退款', 'status': 'SIMULATED', 'notice': '模拟成功、失败、待处理和取消，不收款'},
                {'key': 'routing', 'label': '道路路线规划', 'status': 'UNCONFIGURED', 'notice': '只有坐标连线示意'},
                {'key': 'customerService', 'label': '投诉回复与实时客服', 'status': 'PARTIAL', 'notice': '后台可查看和回复已保存投诉；实时人工客服、电话中继和推送未配置'},
                {'key': 'invoice', 'label': '发票开具', 'status': 'SIMULATED', 'notice': '仅模拟回执，不可报销'},
                {'key': 'push', 'label': '厂商消息推送', 'status': 'UNCONFIGURED', 'notice': 'App前台轮询订单状态'},
            ],
        }
        return data

    def passenger_extra_action(self, pid, action, p):
        require(isinstance(p, dict), '操作参数须为对象')
        data = self._passenger_extra_store(pid)
        if action == 'rider.save':
            name = textfield(p.get('name'), '乘车人姓名', 1, 30)
            mobile = p.get('mobile')
            require(isinstance(mobile, str) and bool(re.fullmatch(r'1\d{10}', mobile)), '乘车人手机号须为11位数字')
            rows = data['riders']; rid = p.get('id')
            if rid:
                row = self._owned_extra(rows, rid, '乘车人')
            else:
                require(len(rows) < 20, '最多保存20位乘车人', 409)
                require(not any(r['mobile'] == mobile for r in rows), '此手机号的乘车人已存在', 409)
                row = {'id': uid('R'), 'createdAt': stamp()}; rows.append(row)
            require(not any(r['id'] != row['id'] and r['mobile'] == mobile for r in rows), '此手机号的乘车人已存在', 409)
            row.update(name=name, mobile=mobile, updatedAt=stamp())
            result = {'rider': clone(row)}
        elif action == 'rider.remove':
            row = self._owned_extra(data['riders'], p.get('id'), '乘车人')
            data['riders'].remove(row); result = {'removed': row['id']}
        elif action == 'invoice.title.save':
            kind = p.get('type', 'PERSONAL'); require(kind in ('PERSONAL', 'COMPANY'), '抬头类型无效')
            title = textfield(p.get('title'), '发票抬头', 1, 100)
            tax = p.get('taxNumber', '').strip() if isinstance(p.get('taxNumber', ''), str) else ''
            if kind == 'COMPANY':
                require(bool(re.fullmatch(r'(?:[0-9A-Za-z]{15}|[0-9A-Za-z]{18}|[0-9A-Za-z]{20})', tax)), '企业税号须为15、18或20位字母数字')
            else:
                tax = ''
            email = p.get('email', '')
            require(isinstance(email, str) and len(email) <= 254 and (not email or bool(re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', email))), '邮箱格式无效')
            rows = data['invoiceTitles']; tid = p.get('id')
            if tid:
                row = self._owned_extra(rows, tid, '发票抬头')
            else:
                require(len(rows) < 20, '最多保存20个发票抬头', 409)
                row = {'id': uid('IT'), 'createdAt': stamp()}; rows.append(row)
            row.update(type=kind, title=title, taxNumber=tax.upper(), email=email, updatedAt=stamp())
            result = {'invoiceTitle': clone(row)}
        elif action == 'invoice.title.remove':
            row = self._owned_extra(data['invoiceTitles'], p.get('id'), '发票抬头')
            data['invoiceTitles'].remove(row); result = {'removed': row['id']}
        elif action == 'invoice.apply':
            order = self.own_passenger_order(pid, p.get('orderId'))
            require(order.get('stage') == 'DONE' and order.get('paid') is True, '仅已完成且已模拟支付的订单可以申请回执', 409)
            existing = next((i for i in data['invoices'] if i['orderId'] == order['id']), None)
            if existing:
                require(existing['titleId'] == p.get('titleId'), '此订单已申请模拟回执，不能重复开具到不同抬头', 409)
                return {'invoice': clone(existing)}
            self.operations.require_feature(order['city'], 'invoice')
            title = self._owned_extra(data['invoiceTitles'], p.get('titleId'), '发票抬头')
            row = {'id': uid('SI'), 'orderId': order['id'], 'titleId': title['id'],
                   'title': title['title'], 'titleSnapshot': clone(title), 'amount': order['fare'],
                   'status': 'SIMULATED_ISSUED', 'simulated': True, 'createdAt': stamp(),
                   'notice': '模拟开票回执 · 未连接税务开票平台 · 不是税务发票，不可报销，未发送邮件'}
            data['invoices'].insert(0, row); result = {'invoice': clone(row)}
        else:
            raise ApiError(404, '乘客操作不存在')
        self._save_passenger_extras(pid, data)
        return result
