## qa-jenkins-onmetal
### OSIC Ops/QA Automation PoC for CI/CD

### Jenkins Master Requirements
Packages (will add more and remove this comment when finalized):
+ ansible >= 2.0
+ lxml (pip)

## Current Workflow
#### Optional
```shell
# NOT Confirmed
ansible-playbook setup_master.yaml

# NOT Confirmed
ansible-playbook setup_jenkins.yaml
```

#### Jenkins Pipeline (I think that is the right jargon)
```shell
# Confirmed
ansible-playbook build_onmetal.yaml --tags 'iad'

# Confirmed
ansible-playbook -i hosts prepare_onmetal.yaml

# Confirmed
ansible-playbook -i hosts get_onmetal_facts.yaml

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
