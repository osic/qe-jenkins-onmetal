#!/bin/bash

# Script installs all dependencies (problably to setup jenkins agent)
# to create an OnMetal IO v2 rackspace product

# Requires rackspace RPC credentials

# Expected positional parameters
# {1} - raxusername
# {2} - rax api key

# Ensure script is run as root
if [ "$EUID" -ne "0" ]; then
  echo "$(date +"%F %T.%N") ERROR : This script must be run as root." >&2
  exit 1
fi

apt-get -y update
apt-get -y upgrade
apt-get install -y vim curl wget git
apt-get install -y --force-yes build-essential libssl-dev python-dev pkg-config libvirt-dev libxml2-dev libxslt1-dev libpq-dev
curl -sk https://bootstrap.pypa.io/get-pip.py
pip install ansible>=2.0
pip install pyrax
pip install libvirt-python


curl https://ec4a542dbf90c03b9f75-b342aba65414ad802720b41e8159cf45.ssl.cf5.rackcdn.com/1.2/Linux/amd64/rack -o /usr/local/bin/rack
chmod +x /usr/local/bin/rack
ssh-keygen -t rsa -b 4096 -q -N '' -f ~/.ssh/id_rsa

git clone https://github.com/osic/qa-jenkins-onmetal

if [[ -z "${1}" || -z "${2}"  ]]; then
    raxusername="rax_username"
    raxkey="e1b835d65f0311e6a36cbc764e00c842e1b835d65f0311e6a36cbc764e00c842"
else
   raxusername="${1}"
   raxkey="${2}"
fi

cat <<EOF > qa-jenkins-onmetal/.raxpub
[rackspace_cloud]
username = $raxusername
api_key = $raxkey
EOF
