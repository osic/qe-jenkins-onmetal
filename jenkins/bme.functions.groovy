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

def setup_ssh_pub_key() {
    String host_ip = get_onmetal_ip()

    sh """
        scp -o StrictHostKeyChecking=no ~/.ssh/id_rsa.pub root@${host_ip}:/root/temp_ssh_key.pub
    """
    // send key to all OS nodes to allow proxy commands
    try {
        sh """
            ssh -o StrictHostKeyChecking=no root@${host_ip} '''
                PUB_KEY=\$(cat /root/temp_ssh_key.pub)
                echo \${PUB_KEY} >> /root/.ssh/authorized_keys
                OSA_DIR=\$(find / -maxdepth 4 -type d -name \"openstack-ansible\")
                cd \$OSA_DIR/playbooks
                ansible utility_all -i inventory -m shell -a \"echo \${PUB_KEY} >> /root/.ssh/authorized_keys\"
            '''
        """
    } catch(err) {
        echo "Failure passing key, this may not be significant depending on host"
        echo err.message
    }
}

def get_controller_utility_container_ip(controller_name='controller01') {
    // Rather than use all containers, find just one to operate tests
    String host_ip = get_onmetal_ip()
    upgrade_output = sh returnStdout: true, script: """
        ssh -o StrictHostKeyChecking=no root@${host_ip} '''
            cd /etc/openstack_deploy
            CONTAINER=\$(cat openstack_inventory.json | jq \".utility.hosts\" | grep \"${controller_name}_utility\")
            CONTAINER=\$(echo \$CONTAINER | sed s/\\\"//g | sed s/\\ //g)
            IP=\$(cat openstack_inventory.json | jq "._meta.hostvars[\\\""\$CONTAINER"\\\"].ansible_ssh_host" -r)
            echo "IP=\${IP}"
        '''
    """
    // quote in a comment to fix editor syntax highlighting '
    String container_ip = upgrade_output.substring(upgrade_output.indexOf('=') +1).trim()
    return (container_ip)
}

def get_tempest_dir(controller_name='controller01') {
  String host_ip = get_onmetal_ip()
  String container_ip = get_controller_utility_container_ip(controller_name)
  String tempest_dir = ""
  try {
      tempest_dir = sh returnStdout: true, script: """
          ssh -o StrictHostKeyChecking=no\
          -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
              TEMPEST_DIR=\$(find / -maxdepth 4 -type d -name "tempest_untagged")
              echo \$TEMPEST_DIR
          '''
      """
  } catch(err) {
      echo "Error in determining Tempest location"
      throw err
  }
  return (tempest_dir)
}

def configure_tempest(controller_name='controller01'){
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = ""

    tempest_dir = get_tempest_dir(controller_name)

    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            cd \$TEMPEST_DIR
            # Make sure tempest is installed
            if [[ -z \$(which ostestr 2>/dev/null) ]]; then
                pip install -r .
                testr init
                mkdir subunit
            fi
        '''
    """

    results = sh returnStdout: true, script: """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            # Make sure etc/tempest.conf exists
            TEMPEST_DIR=${tempest_dir}
            cd \$TEMPEST_DIR
            if [[ -f etc/tempest.conf ]]; then
                mv etc/tempest.conf etc/tempest.conf.orig
                wget https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/jenkins/tempest.conf -O etc/tempest.conf
                # tempest.conf exists, overwrite it with required vars
                keys="admin_password image_ref image_ref_alt uri uri_v3 public_network_id"
                for key in \$keys
                do
                    a="\${key} ="
                    # overwrite each key in tempest conf to be "key ="
                    sed -ir "s|\$a.*|\$a|g" etc/tempest.conf
                    # get each key from generated tempest conf
                    b=\$(cat etc/tempest.conf.orig | grep "\$a")
                    # overwrite each key from original to downloaded tempest conf
                    sed -ir "s|\$a|\$b|g" etc/tempest.conf
                done
            else
                # On testing, if tempest.conf not populated, this needs to be modified
                # to create resources and place in tempest.conf
                echo "No existing tempest.conf"
            fi
        '''
    """
    if (results == "No existing tempest.conf"){
        echo "No existing tempest.conf"
    }
}

def run_tempest_tests(controller_name='controller01', regex='smoke', results_file = null, elasticsearch_ip = null){
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    def tempest_output, failures

    String tempest_dir = get_tempest_dir(controller_name)

    tempest_output = sh returnStdout: true, script: """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            cd \$TEMPEST_DIR
            stream_id=\$(cat .testrepository/next-stream)
            ostestr --regex ${regex} || echo 'Some smoke tests failed.'
            mkdir -p \$TEMPEST_DIR/subunit/smoke
            cp .testrepository/\$stream_id \$TEMPEST_DIR/subunit/smoke/${results_file}
        '''
    """
    println tempest_output
    if (tempest_output.contains('- Failed:') == true) {
        failures = tempest_output.substring(tempest_output.indexOf('- Failed:') + 10)
        failures = failures.substring(0,failures.indexOf(newline)).toInteger()
        if (failures > 1) {
            println 'Parsing failed smoke'
                if (elasticsearch_ip != null) {
                    aggregate_parse_failed_smoke(host_ip, results_file, elasticsearch_ip)
                }
            error "${failures} tests from the Tempest smoke tests failed, stopping the pipeline."
        } else {
            println 'The Tempest smoke tests were successfull.'
        }
    } else {
        error 'There was an error running the smoke tests, stopping the pipeline.'
    }
}

def install_persistent_resources_tests(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)
    // Install Persistent Resources tests on the utility container on ${controller}
    echo 'Installing Persistent Resources Tempest Plugin on the onMetal host'
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            rm -rf \$TEMPEST_DIR/persistent-resources-tests
            git clone https://github.com/osic/persistent-resources-tests.git \$TEMPEST_DIR/persistent-resources-tests
            pip install --upgrade \$TEMPEST_DIR/persistent-resources-tests/
        '''
    """
}

def install_persistent_resources_tests_parse(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)
    // Install Persistent Resources tests parse on the utility container on ${controller}
    echo 'Installing Persistent Resources Tempest Plugin'
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            rm -rf \$TEMPEST_DIR/persistent-resources-tests-parse
            git clone https://github.com/osic/persistent-resources-tests-parse.git \$TEMPEST_DIR/persistent-resources-tests-parse
            pip install --upgrade \$TEMPEST_DIR/persistent-resources-tests-parse/
        '''
    """
}

def run_persistent_resources_tests(controller_name='controller01', action='verify', results_file=null){
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)

    if (results_file == null) {
        results_file = action
    }

    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            cd \$TEMPEST_DIR
            stream_id=\$(cat .testrepository/next-stream)
            ostestr --regex persistent-${action} || echo 'Some persistent resources tests failed.'
            mkdir -p \$TEMPEST_DIR/subunit/persistent_resources/
            cp .testrepository/\$stream_id \$TEMPEST_DIR/subunit/persistent_resources/${results_file}
        '''
    """
}

def parse_persistent_resources_tests(controller_name='controller01'){
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)

    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            cd \$TEMPEST_DIR/subunit/persistent_resources/
            resource-parse --u . > \$TEMPEST_DIR/output/persistent_resource.txt
            rm *.csv
        '''
    """
}

def install_during_upgrade_tests(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)
    // Install during upgrade tests on the utility container on ${controller}
    echo 'Installing during upgrade test on ${controller}_utility container'
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            mkdir -p \$TEMPEST_DIR/output
            git clone https://github.com/osic/rolling-upgrades-during-test
            cd rolling-upgrades-during-test
            pip install -r requirements.txt
        '''
    """
}

def start_during_upgrade_test(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)
    // Start during upgrade tests on the utility container on ${controller}
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            cd rolling-upgrades-during-test
            python call_test.py --daemon --output-file \$TEMPEST_DIR/output
        ''' &
    """
}

def stop_during_upgrade_test(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    // Stop during upgrade tests on the utility container on ${controller}
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            touch /usr/during.uptime.stop
        '''
    """
}

def install_api_uptime_tests(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)
    // install api uptime tests on utility container on ${controller}
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            mkdir -p \$TEMPEST_DIR/output
            rm -rf \$TEMPEST_DIR/api_uptime
            git clone https://github.com/osic/api_uptime.git \$TEMPEST_DIR/api_uptime
            cd \$TEMPEST_DIR/api_uptime
            pip install --upgrade -r requirements.txt
        '''
    """
}

def start_api_uptime_tests(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)
    // start api uptime tests on the utility container on ${controller}
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            mkdir -p \$TEMPEST_DIR/output
            rm -f /usr/api.uptime.stop
            cd \$TEMPEST_DIR/api_uptime/api_uptime
            python call_test.py --verbose --daemon --services nova,swift\
             --output-file \$TEMPEST_DIR/output/api.uptime.out
        ''' &
    """
}

def stop_api_uptime_tests(controller_name='controller01') {
    String host_ip = get_onmetal_ip()
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)
    // Stop api uptime tests on the utility container on ${controller}
    sh """
        ssh -o StrictHostKeyChecking=no\
        -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
            TEMPEST_DIR=${tempest_dir}
            touch /usr/api.uptime.stop

            # Wait up to 10 seconds for the results file gets created by the script
            x=0
            while [ \$x -lt 100 -a ! -e \$TEMPEST_DIR/output/api.uptime.out ]; do
                x=\$((x+1))
                sleep .1
            done
        '''
    """
}

def install_tempest_tests() {
    String host_ip = get_onmetal_ip()

    sh """
        ssh -o StringHostKeyChecking=no root@{host_ip} '''
        cd /opt/openstack-ansible/playbooks
        openstack-ansible os-tempest-install.yml
        '''
    """
}

def aggregate_parse_failed_smoke(host_ip, results_file, elasticsearch_ip, controller_name='controller01') {
    String container_ip = get_controller_utility_container_ip(controller_name)
    String tempest_dir = get_tempest_dir(controller_name)

    try {
        sh """
            scp -o StrictHostKeyChecking=no\
            -o ProxyCommand='ssh -W %h:%p ${host_ip}'\
            -r root@${container_ip}:${tempest_dir}/output .

            scp -o StrictHostKeyChecking=no\
            -r output ubuntu@${elasticsearch_ip}:/home/ubuntu/
        """
    } catch(err) {
        echo "Error moving output directory"
        echo err.message
    }

    try {
        sh """
            scp -o StrictHostKeyChecking=no\
            -o ProxyCommand='ssh -W %h:%p ${host_ip}'\
            -r root@${container_ip}:${tempest_dir}/subunit .

            scp -o StrictHostKeyChecking=no\
            -r output ubuntu@${elasticsearch_ip}:/home/ubuntu/
        """
    } catch(err) {
        echo "No subunit directory found"
        echo err.message
    }

    if (results_file == 'after_upgrade'){
        sh """
            ssh -o StrictHostKeyChecking=no ubuntu@${elasticsearch_ip} '''
                elastic-upgrade -u \$HOME/output/api.uptime.out\
                -d \$HOME/output/during.uptime.out -p \$HOME/output/persistent_resource.txt\
                -b \$HOME/subunit/smoke/before_upgrade -a \$HOME/subunit/smoke/after_upgrade

                elastic-upgrade -s \$HOME/output/nova_status.json,\
                \$HOME/output/swift_status.json,\$HOME/output/keystone_status.json
            '''
        """
    } else {
      sh """
          ssh -o StrictHostKeyChecking=no ubuntu@${elasticsearch_ip} '''
              elastic-upgrade -b \$HOME/subunit/smoke/before_upgrade
          '''
      """
    }
}

def cleanup_test_results(controller_name='controller01') {
  String host_ip = get_onmetal_ip()
  String container_ip = get_controller_utility_container_ip(controller_name)
  tempest_dir = get_tempest_dir(controller_name)

  // Clean up existing tests results
  sh """
      ssh -o StrictHostKeyChecking=no\
      -o ProxyCommand='ssh -W %h:%p ${host_ip}' root@${container_ip} '''
          find ${tempest_dir}/subunit ! -name '.*' ! -type d -exec rm -- {} + || echo "No subunit directory found."
          find ${tempest_dir}/output ! -name '.*' ! -type d -exec rm -- {} + || echo "No output directory found."
      '''
  """
  echo "Previous test runs removed"
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
      sleep 5
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

def bash_upgrade_openstack(release='master', retries=2, fake_results=false) {
    // ***Requires Params ***
    // release (to upgrade to) - master or stable/ocata
    // retries - number of times to rerun
    // fake_results calls different method to return expected fails for testing

    String host_ip = get_onmetal_ip()
    String upgrade_output = ""

    //call upgrade, fake_results allows testing while environment not usable
    echo "Running upgrade"
    if (fake_results) {
        upgrade_output = fake_run_upgrade_return_results(release, host_ip)
    } else {
        echo "do nothing"
        //upgrade_output = run_upgrade_return_results(release, host_ip)
    }
    //take upgrade_output, find out if it's got a failure in it
    String failure_output = parse_upgrade_results_for_failure(upgrade_output)

    if (failure_output.length() > 0) {
        // we have fails, rerun upgrade until it suceeds or to retry limit
        echo "Upgrade failed"
        for (int i = 0; i < retries; i++){
            echo "Rerunning upgrade, retry #" + (i + 1)
            if (fake_results) {
                upgrade_output = fake_run_upgrade_return_results(release, host_ip)
            } else {
                echo "do nothing, testing"
                //upgrade_output = run_upgrade_return_results(release, host_ip)
            }
            failure_output = parse_upgrade_results_for_failure(upgrade_output)
            if (failure_output.length() == 0){
                echo "Upgrade succeeded"
                break
            } else if (i == (retries -1)){
                echo "Upgrade failed, exceeded retries"
            }
        }
    }
}

def fake_run_upgrade_return_results(release='master', host_ip="127.0.0.1"){
    //fakes it
    String upgrade_output = ""
    String failure_output = ""

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
        bash -c "exit 2" || echo "Failed Upgrade"
        '''
    """
    return upgrade_output
}

def run_upgrade_return_results(release="master", host_ip="127.0.0.1"){
    String upgrade_output = ""
    String failure_output = ""

    upgrade_output = sh returnStdout: true, script: """
        ssh -o StrictHostKeyChecking=no root@${host_ip} '''
        cd /opt/openstack-ansible
        git checkout ${release}
        git pull
        LATEST_TAG=\$(git describe --abbrev=0 --tags)
        git checkout \${LATEST_TAG}
        cd /opt/openstack-ansible/playbooks
        export TERM=xterm
        export I_REALLY_KNOW_WHAT_I_AM_DOING=true
        bash scripts/run-upgrade.sh 2>&1 || echo "Failed Upgrade"
        '''
    """
    return upgrade_output
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
  split_output = upgrade_output.split("\n")
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
