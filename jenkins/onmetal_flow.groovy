def waitForHost(onMetalIp){
    timeout(time:420, unit:'SECONDS'){
       catchError{
            def found = 'False'
            while( ! found){
                def response = "ping -q -c 3 $onMetalIp".execute()
                if (response.exitValue() == 0)
                    found = 'True'
            }//while
        }//catch
    }//timeout
}//method

node('onmetal-provisioner'){
    stage 'Pre-Deployment'
    
    // Spin onMetal Host
    catchError{
        sh '''
            #!/bin/bash
            # Clone Automation Project
            if [ -d qa-jenkins-onmetal ]; then
                rm -R qa-jenkins-onmetal
            fi
            git clone https://github.com/osic/qa-jenkins-onmetal
            cp .raxpub qa-jenkins-onmetal/.raxpub
            cd qa-jenkins-onmetal
            
            # Call for a Rackspace OnMetal IO v2
            ansible-playbook build_onmetal.yaml --tags 'iad'
            # Verify onMetal server data
            ansible-playbook -i hosts get_onmetal_facts.yaml --tags 'iad'
        '''
    }
    def onMetalIp = readFile('qa-jenkins-onmetal/hosts').trim()

    // Wait for host to finish spinning, ssh via DNS
    //WATCH prepare_onmetal -- add in a loop
    waitForHost(onMetalIp)
    
    // Configure OnMetal server
    catchError{
        sh '''
            #!/bin/bash
            cd qa-jenkins-onmetal

            # Install packages OnMetal server
            ansible-playbook -i hosts prepare_onmetal.yaml

            # Apply CPU fix - host will be rebooted
            ansible-playbook -i hosts set_onmetal_cpu.yaml
        '''            
    }
    waitForHost(onMetalIp)
    catchError{
        sh '''
            #!/bin/bash
            cd qa-jenkins-onmetal

            # Configure OnMetal server
            ansible-playbook -i hosts configure_onmetal.yaml
            # Create Lab - Provision VMs
            ansible-playbook -i hosts create_lab.yaml

        '''           
    }

    stage 'Deployment'
    def deployed = false
    def maxtries = 5
    def attempts = 1 

    while(! deployed){
        try{
            sh '''
                #!/bin/bash
                cd qa-jenkins-onmetal

                # Deploy OSA on created lab
                ansible-playbook -i hosts deploy_osa.yaml
            '''
            deployed = true
        }catch(all){
            println("OSA deployment attempt: $attempts - Failed.")
            if(attempts>=maxtries)
                throw
            sleep(120)
            attempts = attempts + 1
        }
    }

    stage 'Post-Deployment Validation'
    //TBD
    stage 'Upgrade'
    //TBD
    stage 'Post-Upgrade Validation'
    //TBD
    stage 'Reporting'
    //TBD
    stage 'Clean Up'
    catchError{
        sh '''
            #!/bin/bash
            cd qa-jenkins-onmetal

            # Destroy OSA
            ansible-playbook -i hosts destroy_virtual_resources.yaml
            # Destroy OnMetal server
            ansible-playbook -i hosts destroy_onmetal.yaml --tags 'iad'
        '''
    }
}
node('master') {
    // Try clean up if the build fails
    step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'luz.cazares@intel.com', sendToIndividuals: false])
}
