#!/usr/bin/env groovy

// Function that waits up to 10 minutes for a new agent to finish running cloud-init
// to perform its initial setup
def wait_for_agent_setup() {
    
    echo 'Waiting for cloud-init to finish setting up the VM...'
    timeout(10) {
        waitUntil {
            def cloud_init = readFile('/var/log/cloud-init-output.log')
            def matcher = cloud_init =~ 'Cloud-init .* finished'
            matcher ? true : false
        }
        echo 'Cloud-init completed. VM Initialized.'
    }

}


// Function that pings a host until it replies back or times out 
def wait_for_ping(host_ip, timeout_sec) {

    echo "Waiting for the host with IP:${host_ip} to become online."
    def response, current_time
    def initial_time = sh returnStdout: true, script: 'date +%s'
    waitUntil {
        current_time = sh returnStdout: true, script: 'date +%s'
        if (current_time.toInteger() - initial_time.toInteger() > timeout_sec.toInteger()) {
            error "The host did not respond to ping within the timeout of ${timeout} seconds"
        }
        response = sh returnStatus: true, script: "ping -q -c 1 ${host_ip}"
        return (response == 0)
    }

}


// The external code must return it's contents as an object
return this;

