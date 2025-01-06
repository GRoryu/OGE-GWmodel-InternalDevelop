package whu.edu.cn.algorithms.SpatialStats.GWModels

import breeze.linalg.{*, DenseMatrix, DenseVector, inv, max, sum}
import breeze.numerics.sqrt
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.locationtech.jts.geom.Geometry
import whu.edu.cn.algorithms.SpatialStats.Utils.FeatureDistance.{getDist, getDistRDD}
import whu.edu.cn.algorithms.SpatialStats.Utils.FeatureSpatialWeight.{Array2DenseVector, getGeometry, getSpatialweight, getSpatialweightSingle}
import whu.edu.cn.oge.Service

import scala.collection.mutable

class GTWR(inputRDD: RDD[(String, (Geometry, mutable.Map[String, Any]))]) extends GWRbasic(inputRDD) {

  private var _timestamps: DenseVector[Double] = DenseVector.ones(1)
  private var _stdist: Array[DenseVector[Double]] = _
  protected var _sdist: Array[Tuple2[DenseVector[Double], Int]] = _
  protected var _tdist: Array[DenseVector[Double]] = _
  private var _stWeightArray: Array[DenseVector[Double]] = _

//  private var _stdist: RDD[DenseVector[Double]] = _
//  protected var _sdist: RDD[Array[Double]] = _
//  protected var _tdist: RDD[Array[Double]] = _

  private var _lambda = 0.05

  def setT(property: String): Unit = {
    _timestamps = DenseVector(_shpRDD.map(t => t._2._2(property).asInstanceOf[String].toDouble).collect())
  }

  def setLambda(l: Double): Unit = {
    _lambda = l
  }

  def setDist(): Unit = {
    if (_sdist == null) {
      _sdist = getDist(_shpRDD).map(t => Array2DenseVector(t)).zipWithIndex
//      _sdist=getDistRDD(_shpRDD)
    }
    if (_tdist == null) {
      val timeIdx = _timestamps.toArray.zipWithIndex
      _tdist = timeIdx.map(t1 => {
        _timestamps.map(t2 => {
          var tdist = t1._1 - t2
          if (tdist < 0) {
            tdist = 1e50
          }
          tdist
        })
      })
    }
    if (_stdist == null) {
      _stdist = _sdist.map(t => {
        val sdist = t._1
        val tdist = _tdist(t._2)
        _lambda * sdist + (1 - _lambda) * tdist + 2.0 * sqrt(_lambda * (1 - _lambda) * sdist * tdist)
      })
      //      _stdist.foreach(t => println(t))
    }
    _disMax = _stdist.map(t => max(t)).max
  }

  def setWeightArr(bw: Double, kernel: String, adaptive: Boolean) = {
    if (_stdist == null) {
      setDist()
    }
    if (_kernel == null) {
      _kernel = kernel
      _adaptive = adaptive
    }
    _stWeightArray = _stdist.map(t => getSpatialweightSingle(t, bw = bw, kernel = kernel, adaptive = adaptive)) //问题在这个不对，计算的时候，转化为fix的结果不对。
    //    spweight_dvec.foreach(t=>println(t))
//    _spWeight = getSpatialweight(_stdist, bw = bw, kernel = kernel, adaptive = adaptive)
//    getSpatialweight(_dist, bw = bw, kernel = kernel, adaptive = adaptive)
  }

  override def fit(bw: Double = 0, kernel: String = "gaussian", adaptive: Boolean = true): (Array[(String, (Geometry, mutable.Map[String, Any]))], String) = {
    if (bw > 0) {
      setWeightArr(bw, kernel, adaptive)
    } else if (_stWeightArray != null) {

    } else {
      throw new IllegalArgumentException("bandwidth should be over 0 or spatial weight should be initialized")
    }
    val results = fitArrFunction(_dmatX, _dvecY, _stWeightArray) //fix的结果有问题？
    val betas = DenseMatrix.create(_cols, _rows, data = results._1.flatMap(t => t.toArray))
    val arr_yhat = results._2.toArray
    val arr_residual = results._3.toArray
//    results._1.map(t => println(t))
//    println(arr_yhat.toVector)
//    println(arr_residual.toVector)
    val shpRDDidx = _shpRDD.collect().zipWithIndex
    shpRDDidx.foreach(t => t._1._2._2.clear())
    shpRDDidx.map(t => {
      t._1._2._2 += ("yhat" -> arr_yhat(t._2))
      t._1._2._2 += ("residual" -> arr_residual(t._2))
    })
    //    results._1.map(t=>mean(t))
    val name = Array("Intercept") ++ _nameX
    for (i <- 0 until betas.rows) {
      shpRDDidx.map(t => {
        val a = betas(i, t._2)
        t._1._2._2 += (name(i) -> a)
      })
    }
    var bw_type = "Fixed"
    if (adaptive) {
      bw_type = "Adaptive"
    }
    val fitFormula = _nameY + " ~ " + _nameX.mkString(" + ")
    var fitString = "\n*********************************************************************************\n" +
      "*               Results of Geographically t Weighted Regression                   *\n" +
      "*********************************************************************************\n" +
      "**************************Model calibration information**************************\n" +
      s"Formula: $fitFormula" +
      s"\nKernel function: $kernel\n$bw_type bandwidth: " + f"$bw%.2f\nlambda: ${_lambda}%.2f\n"
    fitString += calDiagnostic(_dmatX, _dvecY, results._3, results._4)
    println(fitString)
    (shpRDDidx.map(t => t._1), fitString)
  }

  private def fitArrFunction(X: DenseMatrix[Double] = _dmatX, Y: DenseVector[Double] = _dvecY, weight: Array[DenseVector[Double]] = _stWeightArray):
  (Array[DenseVector[Double]], DenseVector[Double], DenseVector[Double], DenseMatrix[Double], Array[DenseVector[Double]]) = {
    //    val xtw = weight.map(w => eachColProduct(X, w).t)
    val xtw = weight.map(w => {
      val v1 = (DenseVector.ones[Double](_rows) * w).toArray
      val xw = _dvecX.flatMap(t => (t * w).toArray)
      DenseMatrix.create(_rows, _cols, data = v1 ++ xw).t
    })
    val xtwx = xtw.map(t => t * X)
    val xtwy = xtw.map(t => t * Y)
    //    val xtwx_inv = xtwx.map(t => inv(t))
    val xtwx_inv = xtwx.map(t => {
      try {
        inv(t)
      } catch {
        case e: breeze.linalg.MatrixSingularException =>
          try {
            inv(regularizeMatrix(t))
          } catch {
            case e: Exception =>
              throw new IllegalStateException("Matrix inversion failed")
          }
        case e: Exception =>
          throw new IllegalStateException("Matrix inversion failed")
      }
    })
    val xtwx_inv_idx = xtwx_inv.zipWithIndex
    val betas = xtwx_inv_idx.map(t => t._1 * xtwy(t._2))
    val ci = xtwx_inv_idx.map(t => t._1 * xtw(t._2))
    val ci_idx = ci.zipWithIndex
    val sum_ci = ci.map(t => t.map(t => t * t)).map(t => sum(t(*, ::)))
    val si = ci_idx.map(t => {
      val a = X(t._2, ::).inner.toDenseMatrix
      val b = t._1.toDenseMatrix
      a * b
      //      (X(t._2, ::) * t._1).inner
    })
    val shat = DenseMatrix.create(rows = si.length, cols = si.length, data = si.flatMap(t => t.toArray))
    val yhat = getYHat(X, betas)
    val residual = Y - yhat
    (betas, yhat, residual, shat, sum_ci)
  }

  //  def eachColProduct(Mat: DenseMatrix[Double], Vec: DenseVector[Double]): DenseMatrix[Double] = {
  //    val arrbuf = new ArrayBuffer[DenseVector[Double]]()
  //    for (i <- 0 until Mat.cols) {
  //      arrbuf += Mat(::, i) * Vec
  //    }
  //    val data = arrbuf.toArray.flatMap(t => t.toArray)
  //    DenseMatrix.create(rows = Mat.rows, cols = Mat.cols, data = data)
  //  }

}

object GTWR {

  /** Basic GTWR calculation with specific bandwidth
   *
   * @param sc          SparkContext
   * @param featureRDD  shapefile RDD
   * @param propertyY   dependent property
   * @param propertiesX independent properties
   * @param propertiesT timestamp properties
   * @param bandwidth   bandwidth value
   * @param kernel      kernel function: including gaussian, exponential, bisquare, tricube, boxcar
   * @param adaptive    true for adaptive distance, false for fixed distance
   * @param lambda
   * @return featureRDD and diagnostic String
   */
  def fit(sc: SparkContext, featureRDD: RDD[(String, (Geometry, mutable.Map[String, Any]))], propertyY: String, propertiesX: String, propertiesT: String,
          bandwidth: Double, kernel: String = "gaussian", adaptive: Boolean = false, lambda: Double=0.05)
  : RDD[(String, (Geometry, mutable.Map[String, Any]))] = {
    val model = new GTWR(featureRDD)
    model.setY(propertyY)
    model.setX(propertiesX)
    model.setT(propertiesT)
    model.setLambda(lambda)
    val re = model.fit(bw = bandwidth, kernel = kernel, adaptive = adaptive)
    //    print(re._2)
//    Service.print(re._2, "Basic GWR calculation with specific bandwidth", "String")
    sc.makeRDD(re._1)
  }
}