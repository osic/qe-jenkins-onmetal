# CI for OpenStack Rolling Upgrades

## CI Infrastructure

This is the CI infrastructure for testing rolling upgrades in the following OpenStack deployments:
 + An All In One in a VM
 + A multi-node deployment in an OnMetal server

The following diagram mentions static and dynamic agents. Here is a brief explanation of each one:
 + **Static agent**: it is a machine (server or VM) used for a specific tasks in a CI workflow. This machine may have some dependencies pre-installed or certain configurations already set up for the CI workflow to work properly. This machine is not expected to be discarded and could be used through many CI builds.
 + **Dynamic agent**: also know as cloud agent or on-demand agent. Similar to the static agent, this is a machine (server or VM) used to allocate specific work execution during a CI workflow (pipeline), but the one big difference is that this machine is built at the begining of the job and discarded once the pipeline finishes. Therefore all dependencies and/or configuration that the agent may need must be considered as part of the pipeline. But it also has the advantage of not having to worry much about cleaning it up or about the agent getting messed up due to the continuous setup/tear down.

![alt text][ci-infra]

## CI Workflow for a multi-node deployment in an onMetal server

The following diagram shows the workflow implemented to test rolling upgrades in an OpenStack deployment.

![alt text][ci-workflow]

This is a more detailed diagram that describes the cleanup followed in the pipeline for tearing down up and re-trying after a deployment failure, depending of the step where the deployment failed.

![alt text][ci-workflow-cleanup]


[ci-infra]: https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/common/images/CI-Infra.png "CI Infrastructure for OpenStack Rolling Upgrades"

[ci-workflow]: https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/common/images/AIO-multinode-CI-flow.png "CI Workflow for OpenStack Rolling Upgrades"

[ci-workflow-cleanup]: https://raw.githubusercontent.com/osic/qa-jenkins-onmetal/master/common/images/AIO-multinode-CI-flow-cleanup.png "CI Workflow for OpenStack Rolling Upgrades"
