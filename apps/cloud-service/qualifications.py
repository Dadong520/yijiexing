"""BIZ-08: active certificates survive replacement review; local-date reminders."""
from common import *


class QualificationsMixin:
    def certificate_view(self, certificate):
        c = clone(certificate)
        today = now().date()
        remaining, validity = None, 'UNKNOWN'
        try:
            start = dt.date.fromisoformat(c.get('validFrom', ''))
        except (ValueError, TypeError):
            start = None
        if c.get('validUntil') == 'LONG_TERM':
            validity = 'LONG_TERM'
        else:
            try:
                remaining = (dt.date.fromisoformat(c.get('validUntil', '')) - today).days
                validity = 'EXPIRED' if remaining < 0 else 'EXPIRING' if remaining <= 60 else 'VALID'
            except (ValueError, TypeError):
                pass
        if start and start > today:
            validity = 'NOT_EFFECTIVE'
        elif start is None:
            validity = 'UNKNOWN'
        reviewing = isinstance(c.get('pending'), dict) or c.get('status') == 'PENDING'
        invalid_material = c.get('status') != 'APPROVED'
        c.update(version=c.get('version', 0), pending=c.get('pending'),
                 reviewStatus=c.get('reviewStatus', 'PENDING' if reviewing else 'NONE'),
                 daysRemaining=remaining, validity=validity,
                 reminder=invalid_material or validity in ('EXPIRING', 'EXPIRED', 'UNKNOWN', 'NOT_EFFECTIVE'),
                 canUpdate=not reviewing and (invalid_material or validity in ('EXPIRING', 'EXPIRED', 'UNKNOWN', 'NOT_EFFECTIVE')))
        return c

    def qualifications_profile(self, profile):
        profile = clone(profile)
        profile['certificates'] = [self.certificate_view(c) for c in profile.get('certificates', [])]
        return profile

    def qualification_alerts(self):
        alerts = []
        for row in self.db.execute('SELECT id,profile FROM accounts ORDER BY id'):
            for cert in json.loads(row['profile']).get('certificates', []):
                c = self.certificate_view(cert)
                if c['reminder']:
                    alerts.append({'driverId': row['id'], **{k: c.get(k) for k in
                        ('type', 'version', 'validUntil', 'daysRemaining', 'validity',
                         'pending', 'reviewStatus', 'canUpdate', 'reminder')}})
        return alerts

    def certificate_expected(self, cert, p, required=False):
        if required or 'expectedVersion' in p:
            value = p.get('expectedVersion')
            require(type(value) is int and value >= 0, '请提供证件整数版本expectedVersion')
            require(value == cert.get('version', 0), '证件已更新，请刷新后重新确认', 409)

    def certificate_dates(self, typ, p):
        start, end = p.get('validFrom'), p.get('validUntil')
        try:
            first = dt.date.fromisoformat(start)
        except (ValueError, TypeError):
            raise ApiError(400, '请输入有效起始日期')
        require(first <= now().date(), '有效起始日期不能晚于今天')
        if end == 'LONG_TERM':
            require(typ != 'insurance', '保单不能设置长期')
        else:
            try:
                last = dt.date.fromisoformat(end)
            except (ValueError, TypeError):
                raise ApiError(400, '请输入有效到期日期')
            require(last > first and last > now().date(), '证件到期日须晚于起始日与今天')
        return start, end

    def submit_qualification(self, driver, profile, p):
        typ = p.get('type')
        require(isinstance(typ, str), '证件类型无效')
        cert = next((c for c in profile['certificates'] if c['type'] == typ), None)
        require(cert is not None, '证件类型无效')
        self.certificate_expected(cert, p)
        view = self.certificate_view(cert)
        require(not cert.get('pending') and cert.get('status') != 'PENDING', '资质审核中，不能重复提交', 409)
        require(view['canUpdate'] or cert.get('status') != 'APPROVED', '未到60天临期更新窗口，当前证件仅支持查看', 409)
        start, end = self.certificate_dates(typ, p)
        front = self.save_image(driver, p.get('imageBase64'))
        back = self.save_image(driver, p.get('backImageBase64')) if typ == 'license' else None
        cert['pending'] = {'submissionId': uid('CERTSUB'), 'validFrom': start, 'validUntil': end,
            'fileId': front, 'imageId': front, 'backFileId': back, 'status': 'PENDING', 'submittedAt': stamp()}
        cert.update(version=cert.get('version', 0) + 1, reviewStatus='PENDING')
        # Do not change active dates/files/status or active certification status.
        return {'certificate': self.certificate_view(cert), 'fileId': front, 'backFileId': back}

    def audit_qualification(self, p, actor):
        driver = p.get('driverId')
        profile = json.loads(self.account(driver)['profile'])
        cert = next((c for c in profile['certificates'] if c['type'] == p.get('type')), None)
        require(cert is not None, '资质类型不存在', 404)
        candidate = cert.get('pending')
        modern = isinstance(candidate, dict)
        require(modern or cert.get('status') == 'PENDING', '资质不在待审核状态', 409)
        self.certificate_expected(cert, p, required=modern)
        if modern:
            require(p.get('submissionId') == candidate['submissionId'], '待审核材料已变化，请刷新', 409)
        else:
            candidate = clone(cert)  # Old snapshots retain their existing pending audit path.
        approved = p.get('approved')
        require(type(approved) is bool, '审核结果无效')
        reason = (textfield(p.get('reason', ''), '审核说明', 0, 500) if approved else
                  textfield(p.get('reason'), '退回原因', 2, 500))
        rejection = '' if approved else reason
        before = clone(cert)
        if approved:
            self.certificate_dates(cert['type'], candidate)
            cert.update({k: candidate.get(k) for k in
                         ('validFrom', 'validUntil', 'fileId', 'imageId', 'backFileId', 'submittedAt')})
            cert.update(status='APPROVED', statusCode=1)
        elif not modern:
            cert.update(status='REJECTED', statusCode=4)
        at = stamp()
        cert.update(pending=None, version=cert.get('version', 0) + 1,
            reviewStatus='APPROVED' if approved else 'REJECTED', rejectionReason=rejection,
            reply=rejection, auditedAt=at, auditedBy=actor)
        cert.setdefault('auditRecords', []).append({'actor': actor, 'approved': approved,
            'reason': reason, 'at': at, 'submission': clone(candidate)})
        profile['driver']['certificationStatus'] = ('APPROVED' if all(
            c.get('status') == 'APPROVED' for c in profile['certificates']) else 'PENDING')
        self.save_profile(driver, profile)
        self.audit(actor, 'certificate.audit', before,
                   {'driverId': driver, 'approved': approved, 'reason': reason, 'certificate': cert})
        return {'certificate': self.certificate_view(cert)}
