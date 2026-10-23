"""Create scoped console QA accounts; never print passwords or sessions."""
import json
import pathlib
import secrets
import urllib.request
import uuid
ROOT = pathlib.Path(__file__).resolve().parents[1]
def call(path, data, session=None):
    headers = {'Content-Type': 'application/json'}
    if session:
        headers['X-Admin-Session'] = session
    request = urllib.request.Request('http://127.0.0.1:8790/api/local/admin/v2/' + path, data=json.dumps(data).encode(), headers=headers)
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.load(response)['data']
def main():
    private = ROOT / '.private'
    session = call('login', {'username': 'admin', 'password': (private / 'admin-bootstrap-password.txt').read_text().strip()})['session']
    accounts = []
    try:
        for username, role in [('qa_operator', 'OPERATOR'), ('qa_viewer', 'VIEWER')]:
            password = secrets.token_urlsafe(24)
            call('action', {'action': 'user.save', 'payload': {'username': username, 'password': password, 'role': role, 'enabled': True}, 'requestId': str(uuid.uuid4())}, session)
            accounts.append({'username': username, 'password': password, 'role': role})
        destination = private / 'qa-accounts.json'
        destination.write_text(json.dumps(accounts, ensure_ascii=False))
        destination.chmod(0o600)
        print('QA operator/viewer accounts ready. Credentials saved locally, not printed.')
    finally:
        call('logout', {}, session)
if __name__ == '__main__':
    main()
