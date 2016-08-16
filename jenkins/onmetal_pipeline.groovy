def MAX_TRIES = 5
def 3SEC = 3000

def wait_ping(ip){
    catchError{
       timeout(time:360, unit:'SECONDS'){
            boolean pingable = false
            while( ! pingable){
                def response = "ping -q -c 3 $ip".execute()
                if (response.exitValue() == 0)
                    pingable = true
                else
                    sleep(3SEC)
            }
        }
    }
}


node('onmetal-provisioner'){

    //*******************************
    stage 'Pre-Deployment'
    //*******************************

    String home = '/home/ubuntu/qa-jenkins-onmetal/'
    String onmetal_ip = ''

    catchError{
        //Spin onMetal Server
        ansiblePlaybook playbook: home+'build_onmetal.yaml', sudo: true, sudoUser: null, tags: 'iad'
        // Verify onMetal server data
        ansiblePlaybook playbook: home+'get_onmetal_facts.yaml', sudo: true, sudoUser: null, tags: 'iad'
        // Get server IP address
        onmetal_ip = readFile(home+'hosts').trim()
        //wait for server
        wait_ping(onmetal_ip)
    }
    sleep(3SEC*10)
    
    // Prepare OnMetal server
    boolean configured = false
    int attempts = 0
    while(! configured){
        catchError{ try{
            ansiblePlaybook inventory: home+'hosts' playbook: home+'prepare_onmetal.yaml', sudo: true, sudoUser: null
            configured = true
        }catch(Exception e){
            attempts = attempts + 1
            println("Prepare on Metal Failed - Attempt: $attempts.")
            if(attempts>MAX_TRIES)
                throw e
            sleep(3SEC*30)
        }}
    }

    // Apply CPU fix - will restart server(~5 min)
    configured = false
    attempts = 0
    while(! configured){
        catchError{ try{
            ansiblePlaybook inventory: home+'hosts' playbook: home+'set_onmetal_cpu.yaml', sudo: true, sudoUser: null
            configured = true
        }catch(Exception e){
            attempts = attempts + 1
            println("Applying CPU Fix Failed - Attempt: $attempts.")
            if(attempts>MAX_TRIES)
                throw e
            sleep(3SEC*30)
        }}
    }
    // Configure VMs onMetal server
    wait_ping(onmetal_ip)
    catchError{
        ansiblePlaybook inventory: home+'hosts' playbook: home+'configure_onmetal.yaml', sudo: true, sudoUser: null
        ansiblePlaybook inventory: home+'hosts' playbook: home+'create_lab.yaml', sudo: true, sudoUser: null
    }
    sleep(3SEC)
    
    //******************************
    stage 'Deployment'
    //*******************************

    boolean deployed = false
    attempts = 0

    while(! deployed){
        catchError{try{
            ansiblePlaybook inventory: home+'hosts' playbook: home+'deploy_osa.yaml', sudo: true, sudoUser: null
            deployed = true
        }catch(Exception e){
            attempts = attempts + 1
            println("OSA deployment Faild - Attempt: $attempts.")
            if(attempts>MAX_TRIES)
                throw e
            sleep(3SEC*10)
        }}
    }

    stage 'Post-Deployment Validation'
    //TBD
    stage 'Upgrade'
    //TBD
    stage 'Post-Upgrade Validation'
    //TBD
    stage 'Reporting'
    //TBD

    //*******************************
    stage 'Clean Up'
    //*******************************

    catchError{
        ansiblePlaybook inventory: home+'hosts' playbook: home+'destroy_virtual_resources.yaml', sudo: true, sudoUser: null
        ansiblePlaybook inventory: home+'hosts' playbook: home+'destroy_onmetal.yaml', sudo: true, sudoUser: null, tags: 'iad'
    }
}//onmetal-provisioner
node('master') {
    // Try clean up if the build fails
    //estep([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'luz.cazares@intel.com', sendToIndividuals: false])
}