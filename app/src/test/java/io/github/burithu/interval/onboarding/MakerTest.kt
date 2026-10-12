package io.github.burithu.interval.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

/** Build.MANUFACTURER and Build.BRAND values as phones report them (D36). */
class MakerTest {

    @Test fun xiaomiSubBrands() {
        assertEquals(Maker.XIAOMI, Maker.detect("Xiaomi", "Redmi"))
        assertEquals(Maker.XIAOMI, Maker.detect("Xiaomi", "POCO"))
    }

    @Test fun samsung() = assertEquals(Maker.SAMSUNG, Maker.detect("samsung", "samsung"))

    @Test fun colorOsFamily() {
        assertEquals(Maker.OPPO, Maker.detect("OnePlus", "OnePlus"))
        assertEquals(Maker.OPPO, Maker.detect("OPPO", "OPPO"))
        assertEquals(Maker.OPPO, Maker.detect("realme", "realme"))
    }

    @Test fun vivoAndIqoo() {
        assertEquals(Maker.VIVO, Maker.detect("vivo", "vivo"))
        assertEquals(Maker.VIVO, Maker.detect("vivo", "iQOO"))
    }

    @Test fun huaweiAndHonor() {
        assertEquals(Maker.HUAWEI, Maker.detect("HUAWEI", "HUAWEI"))
        assertEquals(Maker.HUAWEI, Maker.detect("HONOR", "HONOR"))
    }

    @Test fun stockAndUnknownFallBackToOther() {
        assertEquals(Maker.OTHER, Maker.detect("Google", "google"))
        assertEquals(Maker.OTHER, Maker.detect("motorola", "motorola"))
        assertEquals(Maker.OTHER, Maker.detect("Nothing", "Nothing"))
    }

    @Test fun everyMakerButOtherHasAGuide() {
        Maker.entries.forEach { m -> assertEquals(m != Maker.OTHER, m.guideUrl != null, m.name) }
    }
}
