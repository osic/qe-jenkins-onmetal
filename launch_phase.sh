#!/bin/bash

# Tag needs to be passed (dfw or iad)
ansible-playbook build_onmetal.yaml --tags 'iad'
ansible-playbook -i hosts get_onmetal_facts.yaml --tags 'iad'
ansible-playbook -i hosts prepare_onmetal.yaml
ansible-playbook -i hosts set_onmetal_cpu.yaml
ansible-playbook -i hosts configure_onmetal.yaml
ansible-playbook -i hosts create_lab.yaml
