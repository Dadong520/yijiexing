#!/usr/bin/env python3
"""
宜捷行后台独立 HTTP 黑盒测试脚本 (Antigravity QA)
安全规范：
- 不硬编码凭据，从授权文件 .private/qa-accounts.json 读取
- 不输出任何密码、token 或有效 session
- 仅执行只读查询或被拒绝的非法写入，绝不修改线上业务数据
"""
import json
import pathlib
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
ACCOUNTS_FILE = ROOT / '.private' / 'qa-accounts.json'
BASE_URL = 'http://127.0.0.1:8790'

def load_accounts():
    if not ACCOUNTS_FILE.is_file():
        raise FileNotFoundError(f"未找到授权测试账号文件: {ACCOUNTS_FILE}")
    accounts = json.loads(ACCOUNTS_FILE.read_text())
    operator = next((a for a in accounts if a.get('role') == 'OPERATOR' or 'operator' in a.get('username', '')), None)
    viewer = next((a for a in accounts if a.get('role') == 'VIEWER' or 'viewer' in a.get('username', '')), None)
    if not operator or not viewer:
        raise ValueError("未能解析到有效的 operator 或 viewer 账号")
    return operator, viewer

def request(path, method='GET', data=None, session=None):
    url = BASE_URL + path
    headers = {'Content-Type': 'application/json'}
    if session:
        headers['X-Admin-Session'] = session
    body = json.dumps(data).encode('utf-8') if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            resp_body = resp.read().decode('utf-8')
            try:
                j = json.loads(resp_body)
            except Exception:
                j = {'raw': resp_body}
            return resp.status, j, resp.headers
    except urllib.error.HTTPError as err:
        err_body = err.read().decode('utf-8')
        try:
            j = json.loads(err_body)
        except Exception:
            j = {'raw': err_body}
        return err.code, j, err.headers

def mask_session(s):
    if not s:
        return 'None'
    return s[:4] + '****' + s[-4:] if len(s) > 8 else '****'

def main():
    print("=== 开始宜捷行后台独立 HTTP 黑盒测试 ===")
    operator, viewer = load_accounts()
    print(f"[*] 成功加载测试账号: operator={operator['username']}, viewer={viewer['username']} (密码已妥善保护)")

    results = {}

    # 1. 验证无会话请求 401
    print("\n--- 1. 验证无会话请求受保护接口 (期望 401) ---")
    status, body, _ = request('/api/local/admin/v2/state')
    print(f"状态码: {status}, 响应: {body}")
    results['unauthorized_request'] = {
        'status': status,
        'passed': status == 401 or body.get('code') == 401,
        'message': body.get('message', '')
    }

    # 2. Operator 登录
    print("\n--- 2. Operator 登录验证 (期望 200, 获取 session) ---")
    status, body, _ = request('/api/local/admin/v2/login', method='POST', data={
        'username': operator['username'],
        'password': operator['password']
    })
    op_session = body.get('data', {}).get('session') if status == 200 and body.get('code') == 0 else None
    print(f"状态码: {status}, code: {body.get('code')}, session: {mask_session(op_session)}")
    results['operator_login'] = {
        'status': status,
        'passed': op_session is not None,
        'username': operator['username']
    }

    # 3. Operator 获取 state 数据并只读查询目标订单及投诉
    print("\n--- 3. Operator 获取 state 数据及目标订单/投诉核对 ---")
    status, body, _ = request('/api/local/admin/v2/state', session=op_session)
    state = body.get('data', {}) if status == 200 and body.get('code') == 0 else {}
    orders = state.get('orders', [])
    support = state.get('support', [])
    print(f"获取状态码: {status}, 共享订单数: {len(orders)}, 反馈数: {len(support)}")

    target_order_id = 'PORDdcabe83784eb'
    target_order = next((o for o in orders if o.get('id') == target_order_id), None)
    order_ok = False
    if target_order:
        print(f"[*] 找到目标订单 {target_order_id}:")
        print(f"    stage: {target_order.get('stage')}")
        print(f"    fare: {target_order.get('fare')} (分) = ¥{target_order.get('fare', 0)/100:.2f}")
        print(f"    paid: {target_order.get('paid')}, paymentStatus: {target_order.get('paymentStatus')}")
        order_ok = (target_order.get('stage') == 'DONE' and target_order.get('fare') == 1724)
    else:
        print(f"[!] 未找到目标订单 {target_order_id}")

    target_complaint_id = 'PC1f2deda60b4b'
    target_complaint = next((s for s in support if s.get('id') == target_complaint_id), None)
    complaint_ok = False
    if target_complaint:
        print(f"[*] 找到目标投诉 {target_complaint_id}:")
        print(f"    ownerId: {target_complaint.get('ownerId')}")
        print(f"    orderId: {target_complaint.get('orderId')}")
        print(f"    status: {target_complaint.get('status')}")
        print(f"    content: {target_complaint.get('content')}")
        print(f"    reply: {target_complaint.get('reply')}")
        complaint_ok = (target_complaint.get('status') in ['RESOLVED', 'REPLIED'] and bool(target_complaint.get('reply')))
    else:
        print(f"[!] 未找到目标投诉 {target_complaint_id}")

    # 检查双模拟器测试订单 PORD29d6c69f4de0 (仅只读查看)
    sim_order_id = 'PORD29d6c69f4de0'
    sim_order = next((o for o in orders if o.get('id') == sim_order_id), None)
    if sim_order:
        print(f"[*] 观察到双模拟器测试订单 {sim_order_id}: stage={sim_order.get('stage')}, fare={sim_order.get('fare')}")

    results['target_order_check'] = {
        'order_id': target_order_id,
        'found': target_order is not None,
        'passed': order_ok,
        'stage': target_order.get('stage') if target_order else None,
        'fare': target_order.get('fare') if target_order else None
    }
    results['target_complaint_check'] = {
        'complaint_id': target_complaint_id,
        'found': target_complaint is not None,
        'passed': complaint_ok,
        'status': target_complaint.get('status') if target_complaint else None,
        'reply': target_complaint.get('reply') if target_complaint else None
    }

    # 4. 验证错误参数 400
    print("\n--- 4. 验证非法操作参数 (期望 400) ---")
    status, body, _ = request('/api/local/admin/v2/action', method='POST', session=op_session, data={
        'action': 'invalid.action.name',
        'payload': {}
    })
    print(f"状态码: {status}, 响应: {body}")
    results['invalid_parameter_check'] = {
        'status': status,
        'passed': status == 400 or body.get('code') == 400,
        'message': body.get('message', '')
    }

    # 5. Viewer 登录
    print("\n--- 5. Viewer 登录验证 (期望 200, 获取 session) ---")
    status, body, _ = request('/api/local/admin/v2/login', method='POST', data={
        'username': viewer['username'],
        'password': viewer['password']
    })
    vw_session = body.get('data', {}).get('session') if status == 200 and body.get('code') == 0 else None
    print(f"状态码: {status}, code: {body.get('code')}, session: {mask_session(vw_session)}")
    results['viewer_login'] = {
        'status': status,
        'passed': vw_session is not None,
        'username': viewer['username']
    }

    # 6. Viewer 获取 state 数据
    print("\n--- 6. Viewer 获取 state 数据验证 ---")
    status, body, _ = request('/api/local/admin/v2/state', session=vw_session)
    vw_state = body.get('data', {}) if status == 200 and body.get('code') == 0 else {}
    vw_user = vw_state.get('user', {})
    print(f"状态码: {status}, 当前角色: {vw_user.get('role')}, 权限: {vw_user.get('permissions')}")
    results['viewer_state_check'] = {
        'status': status,
        'passed': status == 200 and vw_user.get('role') == 'VIEWER',
        'role': vw_user.get('role'),
        'permissions': vw_user.get('permissions', [])
    }

    # 7. 验证 Viewer 写入操作 403 (安全测试: 尝试对 viewer 发 order.cancel)
    print("\n--- 7. 验证 Viewer 写入操作 order.cancel (期望 403 权限不足) ---")
    status, body, _ = request('/api/local/admin/v2/action', method='POST', session=vw_session, data={
        'action': 'order.cancel',
        'payload': {'id': target_order_id, 'expectedStage': 'DONE', 'reason': '测试viewer写入拦截'},
        'requestId': 'sec-test-viewer-write-403'
    })
    print(f"状态码: {status}, 响应: {body}")
    results['viewer_write_forbidden'] = {
        'status': status,
        'passed': status == 403 or body.get('code') == 403,
        'message': body.get('message', '')
    }

    # 8. 验证 Viewer 导出操作 403
    print("\n--- 8. 验证 Viewer 导出数据 (期望 403 权限不足) ---")
    status, body, _ = request('/api/local/admin/v2/export?dataset=orders', session=vw_session)
    print(f"状态码: {status}, 响应: {body}")
    results['viewer_export_forbidden'] = {
        'status': status,
        'passed': status == 403 or body.get('code') == 403,
        'message': body.get('message', '')
    }

    # 9. 登出及会话失效验证 (401)
    print("\n--- 9. 验证登出后会话失效 (期望 401) ---")
    # Operator 登出
    status_op_lo, body_op_lo, _ = request('/api/local/admin/v2/logout', method='POST', session=op_session, data={})
    print(f"Operator 登出状态码: {status_op_lo}, 响应: {body_op_lo}")
    # 再次使用 op_session 请求 state
    status_op_invalid, body_op_invalid, _ = request('/api/local/admin/v2/state', session=op_session)
    print(f"已登出 Operator 会话重试状态码: {status_op_invalid}, 响应: {body_op_invalid}")

    # Viewer 登出
    status_vw_lo, body_vw_lo, _ = request('/api/local/admin/v2/logout', method='POST', session=vw_session, data={})
    print(f"Viewer 登出状态码: {status_vw_lo}, 响应: {body_vw_lo}")
    # 再次使用 vw_session 请求 state
    status_vw_invalid, body_vw_invalid, _ = request('/api/local/admin/v2/state', session=vw_session)
    print(f"已登出 Viewer 会话重试状态码: {status_vw_invalid}, 响应: {body_vw_invalid}")

    results['logout_invalidation'] = {
        'operator_logout_passed': (status_op_invalid == 401 or body_op_invalid.get('code') == 401),
        'viewer_logout_passed': (status_vw_invalid == 401 or body_vw_invalid.get('code') == 401)
    }

    # 保存脱敏后的目标数据和测试结果
    out_dir = ROOT / 'qa' / 'antigravity'
    out_dir.mkdir(parents=True, exist_ok=True)

    sanitized_state = {
        'target_order': target_order,
        'target_complaint': target_complaint,
        'sim_order_summary': {'id': sim_order.get('id'), 'stage': sim_order.get('stage')} if sim_order else None,
        'state_summary': {
            'orders_count': len(orders),
            'drivers_count': len(state.get('drivers', [])),
            'passengers_count': len(state.get('passengers', [])),
            'support_count': len(support),
            'cities_count': len(state.get('cities', [])),
            'audit_count': len(state.get('audit', []))
        }
    }

    (out_dir / 'sanitized_state.json').write_text(json.dumps(sanitized_state, ensure_ascii=False, indent=2))
    (out_dir / 'test_results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2))

    print("\n=== 测试完成 ===")
    print(f"测试结果已写入: {out_dir / 'test_results.json'}")
    print(f"脱敏业务数据已写入: {out_dir / 'sanitized_state.json'}")

if __name__ == '__main__':
    main()
