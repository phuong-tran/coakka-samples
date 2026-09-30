#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'usage: generate-test-certificates.sh <output-directory>\n' >&2
  exit 2
fi

output="$1"
required=(.schema-v2 ca.pem server.pem server.key client.pem client.key)
complete=1
for file in "${required[@]}"; do
  [[ -f "${output}/${file}" ]] || complete=0
done
if [[ "${complete}" -eq 1 ]]; then
  exit 0
fi

command -v openssl >/dev/null 2>&1 || {
  printf 'OpenSSL is required to create local test identities\n' >&2
  exit 1
}

mkdir -p "${output}"
config="${output}/extensions.cnf"
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
  -keyout "${output}/ca.key" -out "${output}/ca.pem" >/dev/null 2>&1

openssl req -newkey rsa:2048 -sha256 -nodes -subj '/CN=localhost' \
  -keyout "${output}/server.key" -out "${output}/server.csr" >/dev/null 2>&1
openssl x509 -req -sha256 -days 2 -in "${output}/server.csr" \
  -CA "${output}/ca.pem" -CAkey "${output}/ca.key" -CAcreateserial \
  -extfile "${config}" -extensions server -out "${output}/server.pem" \
  >/dev/null 2>&1

openssl req -newkey rsa:2048 -sha256 -nodes -subj '/CN=sample-client' \
  -keyout "${output}/client.key" -out "${output}/client.csr" >/dev/null 2>&1
openssl x509 -req -sha256 -days 2 -in "${output}/client.csr" \
  -CA "${output}/ca.pem" -CAkey "${output}/ca.key" -CAserial "${output}/ca.srl" \
  -extfile "${config}" -extensions client -out "${output}/client.pem" \
  >/dev/null 2>&1

chmod 600 "${output}/ca.key" "${output}/server.key" "${output}/client.key"
rm -f "${output}/ca.key" "${output}/ca.srl" "${output}/server.csr" \
  "${output}/client.csr" "${config}"
printf '2\n' >"${output}/.schema-v2"

for file in "${required[@]}"; do
  [[ -s "${output}/${file}" ]] || {
    printf 'certificate generation did not produce %s\n' "${file}" >&2
    exit 1
  }
done
