#!/bin/bash

BASE_DIR=$(dirname "$0")
cd $BASE_DIR

dpi=(
  ldpi
  mdpi
  hdpi
  xhdpi
  xxhdpi
  xxxhdpi
)

r=(
  "0.75"
  "1"
  "1.5"
  "2"
  "3"
  "4"
)

convert_png_for_dpis() {
  source=$1
  name=$2
  w=$3
  for i in $(seq 0 5); do
    nw=$(echo "${r[i]} * $w" | bc | sed "s/\.0\+$//")
    echo "$name ${dpi[i]} $w dp = $nw px"
    output="../../android/main/src/main/res/drawable-${dpi[i]}/${name}.png"
    rm -f "$output"
    sh svg2png.sh $source $output $nw
  done
}

# logo
convert_png_for_dpis ../tmp/logo.svg logo 80
convert_png_for_dpis ../tmp/logo.svg ic_launcher 48
# model
convert_png_for_dpis ../tmp/model-\$.svg                                model_topup       32
convert_png_for_dpis ../tmp/model-T.svg                                 model_train       32
convert_png_for_dpis ../tmp/model-F.svg                                 model_ferry       32
convert_png_for_dpis ../tmp/model-B.svg                                 model_bus         32
convert_png_for_dpis ../tmp/model-L.svg                                 model_light_rail  32
# drawer
convert_png_for_dpis ../input/drawer/gift-white.svg                     donate_white      32
convert_png_for_dpis ../input/drawer/gift-black.svg                     donate_black      32
convert_png_for_dpis ../input/drawer/mail-white.svg                     feedback_white    32
convert_png_for_dpis ../input/drawer/mail-black.svg                     feedback_black    32
convert_png_for_dpis ../input/drawer/settings-white.svg                 settings_white    32
convert_png_for_dpis ../input/drawer/settings-black.svg                 settings_black    32
convert_png_for_dpis ../input/drawer/star-white.svg                     overview_white    32
convert_png_for_dpis ../input/drawer/star-black.svg                     overview_black    32
convert_png_for_dpis ../input/drawer/th-menu-outline-white.svg          activity_white    32
convert_png_for_dpis ../input/drawer/th-menu-outline-black.svg          activity_black    32
convert_png_for_dpis ../input/drawer/thumbs-up-white.svg                share_white       32
convert_png_for_dpis ../input/drawer/thumbs-up-black.svg                share_black       32
