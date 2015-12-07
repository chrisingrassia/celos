#!/usr/bin/env bash
set -x
set -e
export CELOS_USER=obaskakov
export ANSIBLE_SSH_ARGS=""

#scripts/build.sh
#ansible-playbook scripts/playbooks/kinit.yaml -c ssh -u ${CELOS_USER} -i ${INVENTORY_SERVER}

ACTION=deploy

export INVENTORY_SERVER=scripts/inventory/conflux-server
export INVENTORY_UI=scripts/inventory/conflux-ui
./scripts/server-and-ui-action.sh ${ACTION}

export INVENTORY_SERVER=scripts/inventory/testing-server
export INVENTORY_UI=scripts/inventory/testing-ui
./scripts/server-and-ui-action.sh ${ACTION}
