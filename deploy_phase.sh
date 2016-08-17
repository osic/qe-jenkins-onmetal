#!/bin/bash

ansible-playbook -i hosts prepare_for_osa.yaml
ansible-playbook -i hosts deploy_osa.yaml
