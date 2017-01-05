#!/usr/bin/env groovy

def get_onmetal_ip() {

    // Get the onmetal host IP address
    if (fileExists('hosts')) {

        String hosts = readFile("hosts")
        String ip = hosts.substring(hosts.indexOf('=')+1).replaceAll("[\n\r]", "")
        return (ip)

    } else {

        return (null)

    }

}

def connect_vpn(host=null, user=null, pass=null){
    // connects vpn on jenkins builder via f5fpc
    if (!host || !user || !pass){
        error 'Missing required parameter'
    }
    sh """
    set -x
    alias f5fpc="/usr/local/bin/f5fpc"
    function vpn_info() { f5fpc --info | grep -q "Connection Status"; echo \$?; }
    if [[ \$(vpn_info) -eq 0 ]]; then
        echo "VPN connection already established"
    else
      f5fpc --start --host https://${host}\
         --user ${user} --password ${pass} --nocheck &
      test_vpn=0
      while [[ \$(vpn_info) -ne 0 ]]; do
        # wait for vpn, up to 20 seconds
        if [[ \${test_vpn} -gt 20 ]]; then
          echo "Could not establish VPN"
          exit 2
        fi
        test_vpn=\$(expr \$test_vpn + 1)
        sleep 1
      done
      # adding a sleep to let the connection complete
      sleep 2
      echo "VPN established"
    fi
    """
}

def disconnect_vpn(){
    sh """
  set -x
  alias f5fpc="/usr/local/bin/f5fpc"
  function vpn_info() { f5fpc --info | grep -q "Connection Status"; echo \$?; }
  if [[ \$(vpn_info) -eq 1 ]]; then
      echo "VPN not connected"
  else
    f5fpc --stop &
    test_vpn=0
    while [[ \$(vpn_info) -ne 1 ]]; do
      # wait for vpn, up to 20 seconds
      if [[ \${test_vpn} -gt 20 ]]; then
        echo "Error disconnecting VPN"
        exit 2
      fi
      test_vpn=\$(expr \$test_vpn + 1)
      sleep 1
    done
    echo "VPN disconnected"
  fi
  """
}

def run_testsuite(test_name=null, test_type=null, tempest_root=null) {

    String extra_vars = ""
    if (test_name != null){
        extra_vars += "-e test_name=${test_name} "
    }
    if (test_type != null){
        extra_vars += "-e test_type=${test_type} "
    }
    if (tempest_root != null){
        extra_vars += "-e tempest_root=${tempest_root}"
    }

    if (extra_vars == "") {
        echo "Running playbook bme_test_suite.yml with playbook defaults"
        ansiblePlaybook inventory: "hosts", playbook: 'bme_test_suite.yaml', sudoUser: null
    } else {
        echo "Running playbook bme_test_suite.yml with vars ${extra_vars}"
        ansiblePlaybook extras: "${extra_vars}", inventory: "hosts", playbook: 'bme_test_suite.yaml', sudoUser: null
    }
}

def rebuild_environment(full=null, redeploy=null) {
    // ***Requires Params***
    // full:
    //    true  - rebuild on-metal environment
    //    false - remove existing openstack containers and configuration only
    // redeploy
    //    true  - redeploy openstack
    //    false - no redeploy

    if (full == null || redeploy == null){
        error "Requires specifying rebuild type"
    }
    String extra_vars = ""
    extra_vars = "-e full=${full} -e redeploy=${redeploy}"
    echo "Rebuilding OSA environment with ${extra_vars}"
    ansiblePlaybook extras: "${extra_vars}", inventory: "hosts", playbook: 'bme_rebuild.yaml', sudoUser: null
}

def upgrade_openstack(release = 'stable/ocata', retryflag = 0) {

    try {
        // Upgrade OSA to a specific release
        echo "Running the following playbook: upgrade_osa, to upgrade to the following release: ${release}"
        ansiblePlaybook extras: "-e openstack_release=${release}", inventory: 'hosts', playbook: 'upgrade_osa.yaml', sudoUser: null
    } catch (err) {
        // If there is an error, retry on first attempt else stop
        if (retryflag == 0) {
            // Retry Upgrade OSA to a specific release
            upgrade_openstack(release = $(release), retryflag = 1)
        } else {
            //stop
            throw err
        }
    }
}

// The external code must return it's contents as an object
return this