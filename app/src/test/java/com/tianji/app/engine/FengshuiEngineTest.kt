package com.tianji.app.engine

import org.junit.Test
import java.time.LocalDate

class FengshuiEngineTest {
    @Test fun bazhaiWithMountain() {
        val r = FengshuiEngine.bazhai(LocalDate.of(1990, 5, 15), male = true, sittingMountain = "子")
        println("mingua=${r.minGua.gua} yun=${r.currentYun} house=${r.houseGua} match=${r.zhaiMingMatch}")
        println("chart=${r.chart != null} patterns=${r.chart?.patterns}")
        println("yearSha=${r.yearSha.taisui}/${r.yearSha.suipo}/${r.yearSha.sansha}/${r.yearSha.wuhuang}/${r.yearSha.erhei}")
        println(FengshuiEngine.bazhaiReport(r, "子"))
    }
}
