package whu.edu.cn.debug.GWmodelUtil.STCorrelations

import breeze.linalg.{DenseVector, linspace}
import breeze.plot._
import scala.math.pow

object TemporalAutocorrelation {

  /**
   * 输入属性Array计算自相关滞后结果
   *
   * @param timeArr 属性Arr
   * @param timelag 阶数，默认为20
   * @return
   */
  def timeSeriesACF(timeArr: Array[Double], timelag: Int = 20): Array[Double] = {
    val acfarr = DenseVector.zeros[Double](timelag + 1).toArray
    if (timelag > 0) {
      val f = Figure()
      val p = f.subplot(0)
      for (i <- 0 until timelag + 1) {
        acfarr(i) = getacf(timeArr, i)
        val x = DenseVector.ones[Double](5) :*= i.toDouble
        val y = linspace(0, acfarr(i), 5)
        p += plot(x, y, colorcode = "[0,0,255]")
      }
      p.xlim = (-0.1, timelag + 0.1)
      p.xlabel = "lag"
      p.ylabel = "ACF"
    } else {
      throw new IllegalArgumentException("Illegal Argument of time lag")
    }
    acfarr
  }

  def getacf(timeArr: Array[Double], timelag: Int): Double = {
    val lagArr = timeArr.drop(timelag)
    val tarridx = timeArr.take(lagArr.length).zipWithIndex
    val avg = timeArr.sum / timeArr.length
    val tarr_avg = timeArr.map(t => t * avg).sum
    val tarr_avg2 = tarridx.map(t => t._1 * avg).sum
    val lag_avg = lagArr.map(t => t * avg).sum
    //    var tarr_lag:Double =0.0
    //    for(i<-0 until lagArr.length){
    //      tarr_lag += timeArr(i)*lagArr(i)
    //    }
    val tarr_lag = tarridx.map(t => t._1 * lagArr(t._2)).sum
    val acf = (tarr_lag - tarr_avg2 - lag_avg + pow(avg, 2) * lagArr.length) / (timeArr.map(t => t * t).sum - 2 * tarr_avg + pow(avg, 2) * timeArr.length)
    acf
  }

}