ssh root@$1 << EOF
cd /opt/openstack-ansible
git checkout stable/mitaka
LATEST_TAG=$(git describe --abbrev=0 --tags)
git checkout ${LATEST_TAG}
export TERM=xterm
export I_REALLY_KNOW_WHAT_I_AM_DOING=true
echo $I_REALLY_KNOW_WHAT_I_AM_DOING
cd /opt/openstack-ansible
echo 'YES' | ./scripts/run-upgrade.sh
EOF
