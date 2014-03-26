#!/bin/bash

BASE_DIR=$(dirname $(readlink -f "$0"))
cd $BASE_DIR/..

source $BASE_DIR/lib.sh

main() {
  build_type=$1

  v=$(get_version)
  vn_suffix=$(get_vn_suffix $build_type)
  vn="${v}${vn_suffix}"
  vc=$(get_vc $vn)

  tag="versionCode/$vc"
  git tag -l $tag | grep "$tag" > /dev/null && {
    echo "v$v is already built, please check again"
    exit 2
  }

  set -e
  echo "Building v$vn"
  build $build_type
  update_proguard_mapping $build_type $vn $vc
  commit_final_change $build_type $vn $vc
  cp build/outputs/apk/Opaler-${build_type}.apk /dev/shm/opaler-${vn}.apk

  echo "Push changes to remote repo"
  git push
  git push --tags
}

main "$@"
