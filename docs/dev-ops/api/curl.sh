#!/usr/bin/env bash
# 大模型接口连通性验证脚本（OpenAI 兼容协议）
#
# 用法：
#   1. 把 key 填到同目录的 .env：DEEPSEEK_API_KEY=sk-xxx（该文件已被 gitignore）
#   2. ./curl.sh
#
# 也可以不建 .env，直接用环境变量：
#   DEEPSEEK_API_KEY=sk-xxx ./curl.sh
#   BASE_URL=https://api.deepseek.com MODEL=deepseek-reasoner ./curl.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 同目录下存在 .env 就自动加载；set -a 让变量自动 export 给 curl
if [ -f "${SCRIPT_DIR}/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "${SCRIPT_DIR}/.env"
  set +a
fi

BASE_URL="${BASE_URL:-https://api.deepseek.com}"
MODEL="${MODEL:-deepseek-chat}"
API_KEY="${DEEPSEEK_API_KEY:?没有找到 DEEPSEEK_API_KEY，请填到 ${SCRIPT_DIR}/.env 里，或先 export DEEPSEEK_API_KEY=sk-xxx}"

curl -sS --fail-with-body -w '\nHTTP %{http_code}\n' "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${API_KEY}" \
  -d "{
    \"model\": \"${MODEL}\",
    \"messages\": [
      {
        \"role\": \"user\",
        \"content\": \"1+1\"
      }
    ]
  }"
