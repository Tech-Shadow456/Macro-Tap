#!/bin/bash
ID=$(echo "$1" | tr -d ' \n' | tr 'a-z' 'A-Z')
if [ -z "$ID" ]; then echo "Usage: ./issue.sh XXXX-XXXX-XXXX"; exit 1; fi
printf '%s' "$ID" | openssl dgst -sha256 -sign "$HOME/drivex-keys/private.pem" | base64 -w0
echo
