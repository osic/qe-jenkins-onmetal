# CI for OpenStack Rolling Upgrades

## CI Infrastructure

This is the CI infrastructure for testing rolling upgrades in the following OpenStack deployments:
 + An All In One in a VM
 + A multi-node deployment in an OnMetal server

The following diagram mentions *static* and *dynamic* agents. Here is a brief explanation of each one:
 + **Static agent**: it is a machine (server or VM) used for a specific task(s) in a CI workflow. This machine may have some dependencies pre-installed or certain configurations already set up for the CI workflow to work properly. This machine is not expected to be discarded when the pipeline completes and could be used through many CI builds.
 + **Dynamic agent**: also know as cloud agent or on-demand agent. Similar to the static agent, this is a machine (server or VM) used to allocate specific work execution during a CI workflow (pipeline), but the one big difference is that this machine is built at the beginning of the job and discarded once the pipeline finishes. Therefore all dependencies and/or configuration that the agent may need must be considered as part of the pipeline. But it also has the advantage of one not having to worry much about cleaning it up or about the agent getting messed up due to the continuous setup/tear down.

![alt text][ci-infra]

### Role of the machines in the CI

This section explains what role plays each one of the machines shown in the diagram above in the CI.
 + Jumpbox: it is a machine that has a public IP address and a reverse proxy installed (NGINX). Its only puropse is to be our gate of entry into our infrastructure. It serves two important functions: 
   + It allows us to access all the machines in the infrastructure using the SSH protocol. So if for example we wanted to access the Jenkins Master which has only a private IP address assigned, we would have to do an SSH connection from our computer to the Jumpbox using its public IP address, then do another SSH connection from the Jumpbox to the Jenkins Master.
   + It allows us to redirect all the GUI dashbords of the applications running in the CI infrastructure (like Jenkins, Kibana) through the Jumpbox's public IP address. That way, we can access the Jenkins UI by using http://public_IP:8080, and Kibana using http://public_IP:5601.
 + Jenkins Master: this is the machine that holds the Jenkins installation. This machine is mostly used to orchestrate all the jobs and pipelines in the CI. It is important to note from the diagram above that the Jenkins Master is also listed as a Jenkins Static Agent, the reason for this is because the Jenkins Master has its own executioners that can be set up to run work from the CI workflow, so it can be seen as another agent.
 + Rally: this is a machine that has Rally installed and it is used to run performance benchmarks in OpenStack deployments.
 + ElasticSearch/Kibana: this dual prupose machine it is used for hosting ElasticSearch, which is our engine for storing data resulting from the CI executions, this data can be made visible to end users through visualizations created in Kibana.
 + All In One Host: it is a large VM created for the only purpose of having an All In One OpenStack deployed to it using OpenStack ansible scripts.
 + OnMetal Provisioner: this VM is used for two pruposes:
   + It requests an onMetal host from the rackspace Public Cloud.
   + It runs all the playbooks for deploying a multi-node OpenStack environment in the onMetal host.
 + OnMetal Host: a bare metal server provided by the Rackspace public cloud that is used to host a multi-node OpenStack deployment.

## CI Workflow for a multi-node deployment in an onMetal server

The following diagram shows the workflow implemented to test rolling upgrades in an OpenStack deployment.

![alt text][ci-workflow]

This is a more detailed diagram that describes the cleanup followed in the pipeline for tearing down and re-trying after a deployment failure, depending of the step where the deployment failed.

![alt text][ci-workflow-cleanup]


[ci-infra]: https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/common/images/CI-Infra.png "CI Infrastructure for OpenStack Rolling Upgrades"

[ci-workflow]: https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/common/images/AIO-multinode-CI-flow.png "CI Workflow for OpenStack Rolling Upgrades"

[ci-workflow-cleanup]: https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/common/images/AIO-multinode-CI-flow-cleanup.png "CI Workflow for OpenStack Rolling Upgrades"
