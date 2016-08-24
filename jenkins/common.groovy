#!/usr/bin/env groovy

echo "Loading external common functions..."


def wait_for_ping(host_ip) {

    echo "Waiting for the host with IP:${host_ip} to become online."
    def response
    def current_time
    int timeout = 360
    def initial_time = sh returnStdout: true, script: 'date +%s'
    waitUntil {
        current_time = sh returnStdout: true, script: 'date +%s'
        if (current_time.toInteger() - initial_time.toInteger() > timeout) {
            error "The host did not respond to ping within the timeout of ${timeout} seconds"
        }
        response = sh returnStatus: true, script: "ping -q -c 1 ${host_ip}"
        return (response == 0)
    }

}


// The external code must return it's contents as an object
return this;

