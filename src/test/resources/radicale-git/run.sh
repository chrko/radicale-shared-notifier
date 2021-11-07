#!/usr/bin/env bash

set -euo pipefail

python -m venv venv
source venv/bin/activate
pip install -U pip setuptools wheel
pip install -r requirements.txt

exec python3 \
  -m radicale \
  --debug \
  --storage-filesystem-folder=collections \
  --storage-hook 'git add -A && (git diff --cached --quiet || git commit -m "Changes by "%(user)s)'
