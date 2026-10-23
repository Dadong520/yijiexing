"""Bounded passenger safety/support options for canonical shared orders."""
import base64
import binascii
import json
from common import ApiError, clone, dumps, now, parse_time, require, stamp, textfield, uid


class PassengerOrderOptionsMixin:
    def _passenger_blocked(self, pid):
        return self._passenger_extra_store(pid).get('blockedDrivers', [])

    def passenger_driver_allowed(self, pid, driver):
        return not any(item['driverId'] == driver for item in self._passenger_blocked(pid))

    def expire_passenger_bids(self):
        count = 0
        for row in self.db.execute('SELECT payload FROM passenger_orders'):
            order = json.loads(row['payload'])
            if order['kind'] != 'BID' or order['stage'] != 'AVAILABLE' or not order.get('expiresAt'):
                continue
            if parse_time(order['expiresAt']) > now():
                continue
            require(not order.get('paid'), '报价阶段不应存在已支付金额', 409)
            order.update(stage='CANCELLED', cancelReason='体验竞价等待超过10分钟，系统自动取消',
                         cancelledBy='SYSTEM_TIMEOUT', cancelledAt=stamp())
            self.save_shared(order, 'BIDDING_TIMEOUT_CANCELLED')
            count += 1
        return count

    def _complaint_file_table(self):
        self.db.execute('''CREATE TABLE IF NOT EXISTS passenger_complaint_files(
            id TEXT PRIMARY KEY, passenger TEXT NOT NULL, order_id TEXT NOT NULL,
            complaint_id TEXT NOT NULL, mime TEXT NOT NULL, content BLOB NOT NULL,
            created TEXT NOT NULL)''')

    def passenger_complaint_file(self, pid, file_id):
        self.passenger_profile(pid)
        self._complaint_file_table()
        row = self.db.execute('SELECT mime,content,order_id FROM passenger_complaint_files WHERE id=? AND passenger=?', (file_id, pid)).fetchone()
        require(row is not None, '投诉附件不存在', 404)
        self.own_passenger_order(pid, row['order_id'])
        return row['mime'], bytes(row['content'])

    @staticmethod
    def _decode_complaint_image(value):
        require(isinstance(value, str) and len(value) <= 710000, '每张投诉图片须不超过512KB')
        if value.startswith('data:'):
            require(value.startswith(('data:image/png;base64,', 'data:image/jpeg;base64,')), '仅支持PNG或JPEG图片')
            value = value.split(',', 1)[1]
        try:
            raw = base64.b64decode(value, validate=True)
        except (ValueError, binascii.Error):
            raise ApiError(400, '图片Base64无效')
        require(20 < len(raw) <= 512 * 1024, '每张投诉图片须不超过512KB且内容完整')
        mime = 'image/png' if raw.startswith(b'\x89PNG\r\n\x1a\n') else 'image/jpeg' if raw.startswith(b'\xff\xd8\xff') else None
        require(mime is not None, '仅支持PNG或JPEG图片，不接受外部图片地址')
        # Reject implausible/truncated containers before persisting user content.
        if mime == 'image/png':
            require(len(raw) >= 45 and raw[12:16] == b'IHDR' and b'IEND' in raw[-16:], 'PNG图片不完整')
            width, height = int.from_bytes(raw[16:20], 'big'), int.from_bytes(raw[20:24], 'big')
            require(0 < width <= 4096 and 0 < height <= 4096 and width * height <= 16000000, '图片尺寸须不超过4096且总像素小于1600万')
        else:
            require(raw.endswith(b'\xff\xd9'), 'JPEG图片不完整')
            pos = 2; dimensions = None
            while pos < len(raw) - 2:
                require(raw[pos] == 255, 'JPEG结构无效')
                while pos < len(raw) and raw[pos] == 255: pos += 1
                require(pos < len(raw), 'JPEG结构无效')
                marker = raw[pos]; pos += 1
                if marker in (0xD9, 0xDA): break
                if marker == 0x01 or 0xD0 <= marker <= 0xD7: continue
                require(pos + 2 <= len(raw), 'JPEG结构无效')
                length = int.from_bytes(raw[pos:pos + 2], 'big')
                require(length >= 2 and pos + length <= len(raw), 'JPEG结构无效')
                if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                    require(length >= 8, 'JPEG结构无效')
                    height = int.from_bytes(raw[pos + 3:pos + 5], 'big')
                    width = int.from_bytes(raw[pos + 5:pos + 7], 'big')
                    dimensions = (width, height)
                pos += length
            require(dimensions is not None, 'JPEG图片缺少尺寸信息')
            width, height = dimensions
            require(0 < width <= 4096 and 0 < height <= 4096 and width * height <= 16000000, '图片尺寸须不超过4096且总像素小于1600万')
        return mime, raw

    def passenger_order_option(self, pid, action, p):
        require(isinstance(p, dict), '操作参数须为对象')
        if action == 'order.unblockDriver':
            extra = self._passenger_extra_store(pid)
            rows = extra.get('blockedDrivers', [])
            row = next((r for r in rows if r['driverId'] == p.get('driverId')), None)
            require(row is not None, '此司机不在你的屏蔽列表', 404)
            rows.remove(row); extra['blockedDrivers'] = rows
            self._save_passenger_extras(pid, extra)
            return {'unblockedDriverId': row['driverId']}
        order = self.own_passenger_order(pid, p.get('id'))
        if action == 'order.blockDriver':
            require(order['stage'] in ('DONE', 'WAIT_PAY', 'CANCELLED'), '订单结束后可屏蔽司机', 409)
            require(bool(order.get('driverId')), '此订单没有已分配司机', 409)
            extra = self._passenger_extra_store(pid)
            rows = extra.setdefault('blockedDrivers', [])
            row = next((r for r in rows if r['driverId'] == order['driverId']), None)
            if row is None:
                require(len(rows) < 100, '最多屏蔽100位司机', 409)
                public = self.driver_public(order['driverId'])
                row = {'driverId': order['driverId'], 'name': public['name'], 'plate': public['plate'],
                       'sourceOrderId': order['id'], 'createdAt': stamp(),
                       'reason': textfield(p.get('reason') or '乘客主动屏蔽', '屏蔽原因', 1, 200)}
                rows.append(row); self._save_passenger_extras(pid, extra)
            return {'blockedDriver': clone(row)}
        if action == 'order.remark':
            require(order['stage'] in ('AVAILABLE', 'PREPAY', 'RESERVED', 'PICKUP', 'ARRIVED', 'WAIT_CONTINUE'), '当前阶段不能修改备注', 409)
            order['remark'] = textfield(p.get('remark'), '订单备注', 1, 500)
            self.save_shared(order, 'PASSENGER_REMARK_UPDATED')
            return {'order': self.passenger_view(order)}
        if action == 'order.complaint.attach':
            require(order['stage'] in ('DONE', 'CANCELLED', 'WAIT_PAY'), '订单结束后可提交投诉附件', 409)
            complaint = next((c for c in order.get('complaints', []) if c['id'] == p.get('complaintId')), None)
            require(complaint is not None, '订单投诉不存在', 404)
            images = p.get('imagesBase64')
            existing = complaint.get('attachments', [])
            require(isinstance(images, list) and 1 <= len(images) <= 3 and len(existing) + len(images) <= 3, '每条投诉最多上传3张图片')
            decoded = [self._decode_complaint_image(value) for value in images]
            # Validate all images first. An invalid second attachment cannot leave
            # an orphan first attachment when the HTTP layer commits a business error.
            self._complaint_file_table(); added = []
            for mime, raw in decoded:
                fid = uid('PCF')
                self.db.execute('INSERT INTO passenger_complaint_files VALUES (?,?,?,?,?,?,?)',
                                (fid, pid, order['id'], complaint['id'], mime, raw, stamp()))
                added.append({'id': fid, 'mime': mime, 'size': len(raw),
                              'path': '/api/passenger/files/' + fid})
            complaint['attachments'] = existing + added
            self.save_shared(order, 'PASSENGER_COMPLAINT_ATTACHMENT')
            return {'order': self.passenger_view(order), 'attachments': clone(added)}
        raise ApiError(404, '乘客订单操作不存在')
