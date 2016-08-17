#!/bin/bash

ansible-playbook -i hosts destroy_virtual_machines.yaml
ansible-playbook -i hosts destroy_virtual_networks.yaml
ansible-playbook -i hosts destroy_lab_state_file.yaml
# Tag needs to be the same as used when building
ansible-playbook -i hosts destroy_onmetal.yaml --tags 'iad'
