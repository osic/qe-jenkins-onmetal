#!/bin/bash

# Check that empty-expect is installed and install if not
dpkg --get-selections | grep empty
rc=$?
if ! [ $rc -eq 0 ]; then
  apt-get install -y empty-expect
fi

# Check that loadbalancer is in /etc/hosts
grep 10.5.0.4 /etc/hosts
rc=$?
if ! [ $rc -eq 0 ]; then
  echo -e "\n10.5.0.4\tloadbalancer" >> /etc/hosts
fi

# Get SSH key
__SSHKEY__=$(cat /root/.ssh/id_rsa.pub|cut -d' ' -f1,2)

# Get hostname for loadbalancer
__LBHOSTNAME__=`awk '/10.5.0.4/ { print $2 }' /etc/hosts`

# Copy SSH key to firewall
# Check if password-less login to firewall is working
ssh -o BatchMode=yes nsroot@$__LBHOSTNAME__ 'exit'

# If password-less login works, continue
# else setup password-login and then continue
if ! [ $? -eq 0 ]; then
  # Copy SSH key to firewall
  empty -f -i input.fifo -o output.fifo -p vpxconfig.pid -L vpxconfig.log ssh -o StrictHostKeyChecking=no nsroot@$__LBHOSTNAME__
  empty -w -i output.fifo -o input.fifo "assword" "nsroot\n"
  empty -w -i output.fifo -o input.fifo ">" "shell touch /nsconfig/ssh/authorized_keys && \
  chmod 600 /nsconfig/ssh/authorized_keys && \
  echo $__SSHKEY__ >> /nsconfig/ssh/authorized_keys\n"
  empty -w -i output.fifo -o input.fifo ">" "save config\n"
  empty -w -i output.fifo -o input.fifo ">" "exit\n"
fi

#ssh -o BatchMode=yes nsroot@$__LBHOSTNAME__ 'exit'

#if [ $? -eq 0 ]; then
#  ssh nsroot@10.5.0.4 <<EOF
#`bash /root/vpx-configurator`
#save config
#EOF
#fi
