# qa-jenkins-onmetal
### OSIC Ops/QA Automation PoC for CI/CD

### Jenkins Master Requirements
*setup_master.yaml and setup_jenkins.yaml can facilitate requirements  
-- with the exception of cloud credentials --*

##### Rackspace Public Cloud Credentials  
_.raxpub_ file **in the repo directory** formatted like this:  
```shell
[rackspace_cloud]
username = your-cloud.username
api_key = e1b835d65f0311e6a36cbc764e00c842
```

##### Packages
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

##### SSH Key Pair  
```shell
mkdir /root/.ssh
ssh-keygen -q -t rsa -N "" -f /root/.ssh/id_rsa
```

### Usage
##### Optional
```shell
# NOT Confirmed
ansible-playbook setup_master.yaml

# NOT Confirmed
ansible-playbook setup_jenkins.yaml
```

##### Jenkins Pipeline (I think that is the right jargon)  
tags available are _iad_ and _dfw_, **without** tags resources are created in **both** regions
```shell
# Confirmed
ansible-playbook build_onmetal.yaml --tags 'iad'

# Confirmed
ansible-playbook -i hosts get_onmetal_facts.yaml --tags 'iad'

# Confirmed
ansible-playbook -i hosts prepare_onmetal.yaml

# Confirmed
ansible-playbook -i hosts set_onmetal_cpu.yaml

# Confirmed
ansible-playbook -i hosts configure_onmetal.yaml

# Confirmed
ansible-playbook -i hosts create_lab.yaml

# NOT Confirmed
ansible-playbook -i hosts deploy_osa.yaml

# Confirmed
ansible-playbook -i hosts destroy_virtual_resources.yaml

# Confirmed
ansible-playbook -i hosts destroy_onmetal.yaml --tags 'iad'
```
