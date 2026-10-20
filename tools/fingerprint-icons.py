import io
import sys
import urllib.request
from pathlib import Path

from PIL import Image, ImageChops

RAW = "https://raw.githubusercontent.com/"
SYBERIA = RAW + "syberia-project/platform_vendor_syberia/93ddc9c2696eae05a94f0e04277166c0f38524b0/packages/overlays/UdfpsResources/res/drawable/"
DERP = RAW + "DerpFest-AOSP/vendor_derp/fc905027e0e9246dcf128c6311c0702733ea7891/packages/UdfpsResources/res/drawable/"
AFTERLIFE = RAW + "AfterlifeOS/packages_overlays_Themes/98c13af843c88d87fdbf2492786ca8ee5f7b4d8c/UdfpsResources/res/drawable/"
INFINITY = RAW + "ProjectInfinity-X/packages_overlays_Themes/50886def20e296465e1ecdd6216f96eceddc2a72/udfps/UdfpsIcons/res/drawable/"
ALPHA = RAW + "AlphaDroid-Project/vendor_addons/43553793403303fca61427320ae584800f588316/misc/UdfpsIcons/res/drawable/"
CRDROID = RAW + "crdroidandroid/android_vendor_addons/400f1aac5eba81da068f410f02a9f5ed24d52077/misc/UdfpsIcons/res/drawable/"
AEX = RAW + "AospExtended/platform_vendor_aosp/38ffc2d0717c75a2d64d98a0ae5395144ccbbb0b/packages/UdfpsResources/res/drawable/"
ICONS = [
    ("spiral", SYBERIA + "udfps_misc_1.webp"),
    ("rings", SYBERIA + "udfps_misc_2.webp"),
    ("swirl", SYBERIA + "udfps_misc_3.webp"),
    ("ridges", SYBERIA + "udfps_misc_4.webp"),
    ("pins", SYBERIA + "udfps_misc_5.webp"),
    ("maze", SYBERIA + "udfps_misc_6.webp"),
    ("lines", SYBERIA + "udfps_misc_7.webp"),
    ("bars", SYBERIA + "udfps_icon_default_3.webp"),
    ("arcs", SYBERIA + "udfps_icon_default_5.webp"),
    ("graphite", SYBERIA + "udfps_icon_default_4.webp"),
    ("bold", SYBERIA + "udfps_icon_miui.webp"),
    ("magenta", SYBERIA + "udfps_icon_default_1.webp"),
    ("neon", SYBERIA + "udfps_icon_default_2.webp"),
    ("gradient", SYBERIA + "udfps_icon_misc4.webp"),
    ("halo", SYBERIA + "udfps_icon_huawei.webp"),
    ("loop", SYBERIA + "udfps_icon_op.webp"),
    ("frame", SYBERIA + "udfps_icon_misc2.webp"),
    ("disc", SYBERIA + "udfps_icon_misc3.webp"),
    ("rainbow", SYBERIA + "udfps_icon_rainbow_horn.webp"),
    ("mint", AFTERLIFE + "udfps_icon_gxzw.webp"),
    ("ocean", AFTERLIFE + "udfps_icon_light.webp"),
    ("target", DERP + "udfps_icon_rui.png"),
    ("ring", INFINITY + "nos_fp.webp"),
    ("glow", ALPHA + "oneplus_fp.png"),
    ("triangle", CRDROID + "udfps_icon_neon_triangle.webp"),
    ("diamond", CRDROID + "udfps_icon_default_0.webp"),
    ("claws", CRDROID + "udfps_icon_scratch_pink_blue.webp"),
    ("sunset", CRDROID + "udfps_icon_sun_metro.webp"),
    ("dragon", CRDROID + "udfps_icon_dragon_white_flat.webp"),
    ("yield", CRDROID + "udfps_icon_yield.webp"),
    ("orbit", CRDROID + "udfps_icon_unfunnyguy.webp"),
    ("eclipse", AEX + "udfps_icon_glow_circle.webp"),
    ("splash", AEX + "udfps_icon_paint_splash_circle.webp"),
    ("aurora", AEX + "udfps_icon_default_5.webp"),
]
SIZE = 256
VISIBLE_ALPHA = 32



def visible(alpha):
    return alpha.point(lambda v: 255 if v >= VISIBLE_ALPHA else 0)


def scale(channel, gain):
    return channel.point(lambda v: min(255, round(v * gain)))


out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
for key, url in ICONS:
    image = Image.open(io.BytesIO(urllib.request.urlopen(url).read())).convert("RGBA")
    image = image.crop(visible(image.getchannel("A")).getbbox())
    r, g, b, a = image.split()
    mask = visible(a)
    light_gain = 255 / max(ImageChops.multiply(channel, mask).getextrema()[1] for channel in (r, g, b))
    alpha_gain = 255 / a.getextrema()[1]
    image = Image.merge("RGBA", [scale(channel, light_gain) for channel in (r, g, b)] + [scale(a, alpha_gain)])
    side = max(image.size)
    square = Image.new("RGBA", (side, side))
    square.paste(image, ((side - image.width) // 2, (side - image.height) // 2))
    square.resize((SIZE, SIZE), Image.LANCZOS).save(out / f"{key}.webp", quality=90, method=6)
    print(key)
