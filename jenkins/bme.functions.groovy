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

def bash_upgrade_openstack(release='master', retries=2) {
    // ***Requires Params ***
    // release (to upgrade to) - master or stable/ocata
    // retries - number of times to rerun

    String host_ip = get_onmetal_ip()
    String upgrade_output = ""

    //call upgrade, testing while environment not usable
    upgrade_output = run_upgrade_return_results(release, host_ip)
    //take upgrade_output, find out if it's got a failure in it
    String failure_output = parse_upgrade_results_for_failure(upgrade_output)

    if (failure_output.length() > 0) {
        // we have fails, rerun upgrade until it suceeds or to retry limit
        for (int i = 0; i < retries; i++){
            upgrade_output = run_upgrade_return_results(release, host_ip)
            failure_output = parse_upgrade_results_for_failure(upgrade_output)
            if (failure_output.length() == 0 || i >= retries){
                break
            }
        }
    }
}

def fake_bash_upgrade_openstack(release='master', retries=2){
    // Fake test of upgrade while environment isn't available
    String host_ip = get_onmetal_ip()
    String upgrade_output = ""
    String failure_output = ""

    // replace the call to upgrade with just output from a failing script
    // to prevent the need to change configuration of the deploy host
    upgrade_output = sh returnStdout: true, script: """
        ssh -o StrictHostKeyChecking=no root@${host_ip} '''
        echo "******************** failure ********************"
        echo "The upgrade script has encountered a failure."
        echo 'Failed on task \"rabbitmq-install.yml -e 'rabbitmq_upgrade=true'\"'
        echo "Re-run the run-upgrade.sh script, or"
        echo "execute the remaining tasks manually:"
        echo "openstack-ansible rabbitmq-install.yml -e 'rabbitmq_upgrade=true'"
        echo "openstack-ansible etcd-install.yml"
        echo "openstack-ansible utility-install.yml"
        echo "openstack-ansible rsyslog-install.yml"
        echo "openstack-ansible /opt/openstack-ansible/scripts/upgrade-utilities/playbooks/memcached-flush.yml"
        echo "openstack-ansible setup-openstack.yml"
        echo "******************** failure ********************"
        bash -c "exit 2" || echo "failed upgrade"
        '''
    """
    failure_output = parse_upgrade_results_for_failure(upgrade_output)
    if (failure_output.length() > 0) {
        // we have fails, rerun upgrade until it suceeds or to retry limit
        for (int i = 0; i < retries; i++){
            echo "Upgrade failed, retrying #" + String(i)
            //upgrade_output = run_upgrade_return_results(release, host_ip)
            failure_output = parse_upgrade_results_for_failure(upgrade_output)
            if (failure_output.length() == 0){
                echo "Successful Upgrade"
                break
            } else if (i >= retries){
                echo "Upgrade failed, hit retry limit"
                break
            }
        }
    }
}

def parse_upgrade_results_for_failure(upgrade_output = null){
  // Looking for failed output such as:
  // ******************** failure ********************
  // The upgrade script has encountered a failure.
  // Failed on task "rabbitmq-install.yml -e 'rabbitmq_upgrade=true'"
  // Re-run the run-upgrade.sh script, or
  // execute the remaining tasks manually:
  // openstack-ansible rabbitmq-install.yml -e 'rabbitmq_upgrade=true'
  // openstack-ansible etcd-install.yml
  // openstack-ansible utility-install.yml
  // openstack-ansible rsyslog-install.yml
  // openstack-ansible /opt/openstack-ansible/scripts/upgrade-utilities/playbooks/memcached-flush.yml
  // openstack-ansible setup-openstack.yml
  // ******************** failure ********************
  // * Caveat, this only grabs the first failure block and returns it (assumes all controllers will
  // either fail the same way, or we're just going to act on any fail the same way)
  echo "split"
  split_output = upgrade_output.split("\n")
  echo "completed split"
  String failure_output = ""
  boolean failure_found = false
  boolean record = false
  for (int i = 0; i < split_output.size(); i++){
    if (split_output[i] == "******************** failure ********************"){
      if (record){
        // if we're already recording, then we've already found a failure line
        record = false
        failure_output = failure_output.trim()
        break
      } else {
        // we haven't started recording, so this is the first failure indicator
        // set flag to record, and that there is a failure
        record = true
        failure_found = true
      }
    } else if (record) {
      // we're recording, so record it
      failure_output = failure_output + split_output[i] + "\n"
    }
  }

  // return failure found, or an empty string
  if (failure_found){
    return (failure_output)
  } else {
    return ("")
  }

}

def run_upgrade_return_results(release="master", host_ip="127.0.0.1"){
    String upgrade_output = ""
    String failure_output = ""

    upgrade_output = sh returnStdout: true, script: """
        ssh -o StrictHostKeyChecking=no root@${host_ip} '''
        cd /opt/openstack-ansible
        git checkout ${release}
        cd /opt/openstack-ansible/playbooks
        LATEST_TAG=\$(git describe --abbrev=0 --tags)
        git checkout \${LATEST_TAG}
        export TERM=xterm
        export I_REALLY_KNOW_WHAT_I_AM_DOING=true
        bash scripts/run-upgrade.sh 2>&1 || echo "Failed Upgrade"
        '''
    """
    return upgrade_output
}

def upgrade_openstack(release = 'master') {

    try {
        // Upgrade OSA to a specific release
        echo "Running the following playbook: upgrade_osa, to upgrade to the following release: ${release}"
        ansiblePlaybook extras: "-e openstack_release=${release}", inventory: 'hosts', playbook: 'upgrade_osa.yaml', sudoUser: null
    } catch (err) {
        echo "Retrying upgrade, failure on first attempt: " + err
        // Retry Upgrade OSA to a specific release
        ansiblePlaybook extras: "-e openstack_release=${release}", inventory: 'hosts', playbook: 'upgrade_osa.yaml', sudoUser: null
    }
}

// The external code must return it's contents as an object
return this
