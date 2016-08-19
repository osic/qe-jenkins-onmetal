#!/bin/bash

# Prepare to run qa-jenkins-onmetal ansible playbooks (On top of Jenkins Agent)
# See also qa-jenkins-onmetal/jenkins/jenkins_agent_cloudinit_dependencies

# Requires rackspace RPC credentials

# Expected positional parameters
# {1} - raxusername
# {2} - rax api key

if [ ! -f ~/.ssh/id_rsa ]; then
    ssh-keygen -t rsa -b 4096 -q -N '' -f ~/.ssh/id_rsa
fi

rm -R qa-jenkins-onmetal

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
