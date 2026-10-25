#!/usr/bin/env bash
#
# Prints a fresh Cognito access token (JWT) for the local dev user.
# Reads infra/ministack/.cognito.env produced by bootstrap.sh.
#
# Usage:
#   ./infra/ministack/get-cognito-token.sh              # prints the raw JWT
#   TOKEN=$(./infra/ministack/get-cognito-token.sh)     # capture it
#
# Requires: awscli v2, MiniStack running, resources already provisioned.

set -euo pipefail

ENDPOINT="${AWS_ENDPOINT_URL:-http://localhost:4566}"
REGION="${AWS_DEFAULT_REGION:-us-east-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.cognito.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Run 'docker compose up -d --wait' first." >&2
  exit 1
fi
# shellcheck source=/dev/null
source "${ENV_FILE}"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="${REGION}"

command aws --endpoint-url "${ENDPOINT}" --region "${REGION}" \
  cognito-idp admin-initiate-auth \
  --user-pool-id "${COGNITO_USER_POOL_ID}" \
  --client-id "${COGNITO_CLIENT_ID}" \
  --auth-flow ADMIN_USER_PASSWORD_AUTH \
  --auth-parameters USERNAME="${COGNITO_USERNAME}",PASSWORD="${COGNITO_PASSWORD}" \
  --query 'AuthenticationResult.AccessToken' --output text
