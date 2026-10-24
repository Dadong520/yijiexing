#!/bin/bash
set -u
readonly ADMIN_URL='https://driver-admin-491829048305.us-west1.run.app/'
echo "宜捷行云后台：$ADMIN_URL"
echo '使用已授权 Google 账号登录，再输入后台管理账号与密码；无需本机代理或复制管理令牌。'
open "$ADMIN_URL"
