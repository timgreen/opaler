#!/bin/bash

BASE_DIR=$(dirname $(readlink -f "$0"))
cd $BASE_DIR/..

source $BASE_DIR/lib.sh

prompt_for_version() {
  echo "Current version: $(get_version)" 1>&2
  echo -n "New version: " 1>&2
  read v
  echo ${v}
}

main() {
  last_v=$(get_version)
  new_v=${1:-$(prompt_for_version)}
  if (( $(get_vc $last_v) < $(get_vc $new_v) )); then
    set_version $new_v
  else
    echo "Please enter a newer version."
    echo "  $new_v <= $last_v"
    exit 2
  fi
}

main "$@"
