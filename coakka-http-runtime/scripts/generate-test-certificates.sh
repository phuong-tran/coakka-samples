#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'usage: generate-test-certificates.sh <output-directory>\n' >&2
  exit 2
fi

output="$1"
required=(.schema-v2 ca.pem server.pem server.key client.pem client.key)
command -v openssl >/dev/null 2>&1 || {
  printf 'OpenSSL is required to create local test identities\n' >&2
  exit 1
}

# Reused sample work directories can outlive these deliberately short-lived
# identities. A complete file set is not sufficient: reject an expiring CA,
# server, or client certificate before any TLS smoke test uses it.
complete=1
for file in "${required[@]}"; do
  [[ -f "${output}/${file}" ]] || complete=0
done
if [[ "${complete}" -eq 1 ]] &&
  openssl x509 -checkend 3600 -noout -in "${output}/ca.pem" >/dev/null 2>&1 &&
  openssl x509 -checkend 3600 -noout -in "${output}/server.pem" >/dev/null 2>&1 &&
  openssl x509 -checkend 3600 -noout -in "${output}/client.pem" >/dev/null 2>&1 &&
  openssl verify -CAfile "${output}/ca.pem" "${output}/server.pem" "${output}/client.pem" >/dev/null 2>&1; then
  exit 0
fi

mkdir -p "${output}"
stage="$(mktemp -d "${output}/.identity-stage.XXXXXX")"
cleanup() {
  rm -rf -- "${stage}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
config="${stage}/extensions.cnf"
cat >"${config}" <<'EOF'
[ca]
basicConstraints = critical,CA:TRUE,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always

[server]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost,IP:127.0.0.1
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer

[client]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = clientAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
EOF

openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 2 \
  -subj '/CN=CoAkka HTTP sample CA' \
  -addext 'basicConstraints=critical,CA:TRUE,pathlen:0' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' \
  -addext 'subjectKeyIdentifier=hash' \
  -keyout "${stage}/ca.key" -out "${stage}/ca.pem" >/dev/null 2>&1

openssl req -newkey rsa:2048 -sha256 -nodes -subj '/CN=localhost' \
  -keyout "${stage}/server.key" -out "${stage}/server.csr" >/dev/null 2>&1
openssl x509 -req -sha256 -days 2 -in "${stage}/server.csr" \
  -CA "${stage}/ca.pem" -CAkey "${stage}/ca.key" -CAcreateserial \
  -extfile "${config}" -extensions server -out "${stage}/server.pem" \
  >/dev/null 2>&1

openssl req -newkey rsa:2048 -sha256 -nodes -subj '/CN=sample-client' \
  -keyout "${stage}/client.key" -out "${stage}/client.csr" >/dev/null 2>&1
openssl x509 -req -sha256 -days 2 -in "${stage}/client.csr" \
  -CA "${stage}/ca.pem" -CAkey "${stage}/ca.key" -CAcreateserial \
  -extfile "${config}" -extensions client -out "${stage}/client.pem" \
  >/dev/null 2>&1

chmod 600 "${stage}/server.key" "${stage}/client.key"
for file in "${required[@]:1}"; do
  [[ -s "${stage}/${file}" ]] || {
    printf 'certificate generation did not produce %s\n' "${file}" >&2
    exit 1
  }
done
openssl verify -CAfile "${stage}/ca.pem" "${stage}/server.pem" "${stage}/client.pem" >/dev/null

# The schema marker is the commit point. If replacement is interrupted, the
# next invocation regenerates the complete identity set before using it.
rm -f -- "${output}/.schema-v2"
for file in "${required[@]:1}"; do
  mv -f -- "${stage}/${file}" "${output}/${file}"
done
printf '2\n' >"${output}/.schema-v2"

for file in "${required[@]}"; do
  [[ -s "${output}/${file}" ]] || {
    printf 'certificate generation did not produce %s\n' "${file}" >&2
    exit 1
  }
done
