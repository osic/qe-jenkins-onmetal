# qa-jenkins-onmetal
### OSIC Ops/QA Automation PoC for Rolling Upgrade CI/CD

### CI/CD Flow
![CIflow](https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/common/images/onMetalCIFlow.png)
[Full Details](https://github.com/osic/osic-upgrade-test/blob/master/master_test_plan.pdf)

### Elements
+ Jenkins Master
+ Jenkins Agents
+ Jenkins Pipelines:
    + OSA Upgrade Liberty to Mitaka - via run_upgrade.sh
    + OSA Upgrade Mitaka to Newton-master - via run_upgrade.sh
    + OSA Rolling Upgrade Mitaka to Newton-master - Rolling Upgrade:
      Nova, Swift and Keystone (run_upgrade.sh breakdown)
    + [See Details HERE](./jenkins/Jenkinsfile)
+ Rackspace OnMetal IO v2
+ OSA Deployment
+ OSA Upgrade
+ Test Suites:
    + Tempest smoke test suite[HERE](https://github.com/openstack/tempest)
    + Persistent resources test suite.
    + API uptime test suite. [HERE](https://github.com/osic/api_uptime)
    + During upgrade test suite

Further details about the CI model and test suites.
See https://github.com/osic/osic-upgrade-test

### Jenkins Installation
[Install_Jenkins_Master](https://wiki.jenkins-ci.org/display/JENKINS/Installing+Jenkins+on+Ubuntu)
[Install_Jenkins_Slave_Agents](https://wiki.jenkins-ci.org/display/JENKINS/Distributed+builds)

### Jenkins Slave Agents Additional Configuration
Since Jenkins slaves agents will fire the complete OnMetal solution it
requires to install:

+ Rackspace Public Cloud (RPC) Credentials
  _.raxpub_ file **in the repo directory** formatted like this:
  ```shell
  [rackspace_cloud]
  username = your-cloud.username
  api_key = e1b835d65f0311e6a36cbc764e00c842
  ```

+ Packages
  + apt-get
    + python-dev
    + build-essential
    + libssl-dev
    + curl
    + git
    + pkg-config
    + libvirt-dev
    + jenkins
  + pip [ python <(curl -sk https://bootstrap.pypa.io/get-pip.py) ]
    + ansible >= 2.0
    + lxml
    + pyrax
    + libvirt-python
  + rack binary
    + [installation and configuration](https://developer.rackspace.com/docs/rack-cli/configuration/#installation-and-configuration)

+ SSH Key Pair
```shell
mkdir /root/.ssh
ssh-keygen -q -t rsa -N "" -f /root/.ssh/id_rsa
```

+ Automation scripts
[Automation of package installation - via cloud init](./jenkins/agent_dependencies)
[Creation of raxpub RPC credentials file - need positional args](jenkins/set_jenkins_agent.sh)

### Usage
##### Optional
```shell

# NOT Confirmed
ansible-playbook setup_jenkins.yaml
```

##### OnMetal OSA Deployment - Performed by Jenkins Pipeline
[Automation HERE](./jenkins/Jenkinsfile)

Ansible available tags are _iad_ and _dfw_.
Which refers to Rackspace Regions:
[DFW=Dallas-fort-Worth, IAD=Northern-Virginia](https://support.rackspace.com/how-to/about-regions/)
 **without** tags resources are created in **both** regions

```shell
# Get Rackspace OnMetal IO v2 - need RPC credentials - 1 Physical Server
ansible-playbook build_onmetal.yaml --tags 'iad'

# Verify onMetal server data via rax CLI
ansible-playbook -i hosts get_onmetal_facts.yaml --tags 'iad'

# Install packages on the onMetal server
ansible-playbook -i hosts prepare_onmetal.yaml

# Apply server OS patches
ansible-playbook -i hosts set_onmetal_cpu.yaml

# Setup onMetal server - additional configurations
ansible-playbook -i hosts configure_onmetal.yaml

# Create the VMs on top of onMetal server
ansible-playbook -i hosts create_lab.yaml

# Prepare VMs for OSA installation
ansible-playbook -i hosts prepare_for_osa.yaml

# Deploy OpenStack via OSA
ansible-playbook -i hosts deploy_osa.yaml
```

##### OnMetal OSA Upgrade - Performed by Jenkins Pipeline

```shell
# via run_upgrade.sh
ansible-playbook -i hosts -e "openstack_release=${release}" upgrade_osa.yaml

# rolling upgrade - project stepping
# TBD
```

##### OnMetal Cleanup - Performed by Jenkins Pipeline
```shell
# Cleanup
ansible-playbook -i hosts destroy_virtual_machines.yaml

# Cleanup
ansible-playbook -i hosts destroy_virtual_networks.yaml

# Cleanup
ansible-playbook -i hosts destroy_lab_state_file.yaml

# Cleanup - Release RPC resources
ansible-playbook -i hosts destroy_onmetal.yaml --tags 'iad'
```

