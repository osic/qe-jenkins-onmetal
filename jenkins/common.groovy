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

def get_server_ip() {

    String server_ip

    // Get the IP of the current worker server
    server_ip = sh returnStdout: true, script: 'ip addr show eth0 | grep "inet\\b" | awk \'{print $2}\' | cut -d/ -f1'
    echo "The IP address of the server is: ${server_ip}"
    return (server_ip.trim())

}


def get_server_public_key() {

    String pkey

    // Get the public key from the current worker server
    pkey = sh returnStdout: true, script: 'cat $HOME/.ssh/id_rsa.pub'
    return (pkey)

}


def add_key_to_server(server_ip, public_key) {

    sh """
    ssh -o StrictHostKeyChecking=no root@${server_ip} '''
        echo "${public_key}" >> \$HOME/.ssh/authorized_keys
    '''
    """

}


// The external code must return it's contents as an object
return this;

