update_proguard_mapping() {
  build_type=$1
  vn=$2

  mkdir -p proguard/mapping
  cp -f build/outputs/mapping/${build_type}/mapping.txt proguard/mapping/$vn-mapping.txt
  git add proguard/mapping/$vn-mapping.txt
}

get_version() {
  cut -d= -f 2 version.properties
}

set_version() {
  vn=$1
  echo "VERSION_NAME=$vn" >  version.properties
}

build() {
  build_type=$1
  ./gradlew askForPasswords clean assemble${build_type^} -x lint
}

commit_final_change() {
  build_type=$1
  vn=$2
  vc=$3
  git commit -m "Release version $vn for ${build_type}."
  git tag versionCode/$vc
  git tag versionName/$vn
}

get_vc() {
  v=$1
  IFS='.' read -ra vnp <<< "$v"
  vc=$(( (${vnp[0]} * 10000 + ${vnp[1]} * 1000 + ${vnp[2]}) * 10 + $(from_vn_suffix ${vnp[3]}) ))
  echo -n "$vc"
}

from_vn_suffix() {
  if [[ "$1" == 'D' ]]; then
    echo '0'
  elif [[ "$1" == 'A' ]]; then
    echo '1'
  elif [[ "$1" == 'B' ]]; then
    echo '2'
  elif [[ "$1" == '' ]]; then
    echo '3'
  else
    echo "Unknown version name suffix '$1'"  >2&
    exit 2
  fi
}

get_vn_suffix() {
  build_type="$1"
  if [[ "$build_type" == 'prod' ]]; then
    echo ''
  elif [[ "$build_type" == 'beta' ]]; then
    echo '.B'
  elif [[ "$build_type" == 'alpha' ]]; then
    echo '.A'
  else
    echo '.D'
  fi
}
