package whu.edu.cn.debug.GWmodelUtil.SpatialRegression

import breeze.linalg.{DenseMatrix, DenseVector, eig, inv, qr, sum}
import breeze.numerics.sqrt
import scala.math._

import whu.edu.cn.debug.GWmodelUtil.optimize._

class SARdurbinmodel  extends SARmodels {
  var _xrows = 0
  var _xcols = 0
  private var _df = _xcols

  private var _dX: DenseMatrix[Double] = _
  private var _1X: DenseMatrix[Double] = _
  private var _errorX: DenseMatrix[Double] = _
  private var _errorY: DenseVector[Double] = _

  private var _wy: DenseVector[Double] = _
  private var _wwy: DenseVector[Double] = _
  private var _wx: DenseMatrix[Double] = _
  private var _eigen: eig.DenseEig = _


  override def setX(x: Array[DenseVector[Double]]): Unit = {
    _X = x
    _xcols = x.length
    _xrows = _X(0).length
    _dX = DenseMatrix.create(rows = _xrows, cols = _X.length, data = _X.flatMap(t => t.toArray))
    val ones_x = Array(DenseVector.ones[Double](_xrows).toArray, x.flatMap(t => t.toArray))
    _1X = DenseMatrix.create(rows = _xrows, cols = x.length + 1, data = ones_x.flatten)
    _df = _xcols + 1 + 1
  }

  override def setY(y: Array[Double]): Unit = {
    _Y = DenseVector(y)
  }

  def fit() = {
    val arr=firstvalue()
    val optresult=nelderMead(arr,paras4optimize)
    println("----------optimize result----------")
    optresult.foreach(println)
  }

  def get_betas(X: DenseMatrix[Double] = _dX, Y: DenseVector[Double] = _Y, W: DenseMatrix[Double] = DenseMatrix.eye(_xrows)): DenseVector[Double] = {
    val xtw = X.t * W
    val xtwx = xtw * X
    val xtwy = xtw * Y
    val xtwx_inv = inv(xtwx)
    val betas = xtwx_inv * xtwy
    betas
  }

  def get_res(X: DenseMatrix[Double] = _dX, Y: DenseVector[Double] = _Y, W: DenseMatrix[Double] = DenseMatrix.eye(_xrows)): DenseVector[Double] = {
    val xtw = X.t * W
    val xtwx = xtw * X
    val xtwy = xtw * Y
    val xtwx_inv = inv(xtwx)
    val betas = xtwx_inv * xtwy
    val y_hat = X * betas
    Y - y_hat
  }

  private def get_env(): Unit = {
    if (_Y != null && _X != null) {
      if (_wy == null || _wwy == null) {
        _wy = DenseVector(spweight_dvec.map(t => (t dot _Y)))
        _wwy = DenseVector(spweight_dvec.map(t => (t dot _wy)))
      }
      if (_wx == null) {
        val _dvecWx = _X.map(t => DenseVector(spweight_dvec.map(i => (i dot t))))
        val ones_x = Array(DenseVector.ones[Double](_xrows).toArray, _dvecWx.flatMap(t => t.toArray))
        _wx = DenseMatrix.create(rows = _xrows, cols = _dvecWx.length + 1, data = ones_x.flatten)
      }
      if (spweight_dmat != null) {
        if (_eigen == null) {
          _eigen = breeze.linalg.eig(spweight_dmat.t)
        }
      } else {
        throw new NullPointerException("the shpfile is not initialized! please check!")
      }
    } else {
      throw new IllegalArgumentException("the x or y are not initialized! please check!")
    }
  }

  private def firstvalue(): Array[Double] = {
    if (_eigen == null) {
      _eigen = breeze.linalg.eig(spweight_dmat.t)
    }
    val eigvalue = _eigen.eigenvalues.copy
    //    val min = eigvalue.toArray.min
    //    val max = eigvalue.toArray.max
    val median = (eigvalue.toArray.min + eigvalue.toArray.max) / 2.0
    Array(median, median)
  }

  def paras4optimize(optarr:Array[Double]): Double= {
    get_env()
    if (optarr.length == 2) {
      val rho = optarr(0)
      val lambda = optarr(1)
      val yl = _Y - rho * _wy - lambda * _wy + rho * lambda * _wwy
      val xl = (_1X - lambda * _wx)
      val xl_qr = qr(xl)
      val xl_qr_q = xl_qr.q(::, 0 to _xcols)
      val xl_q_yl = xl_qr_q.t * yl
      val SSE = yl.t * yl - xl_q_yl.t * xl_q_yl
      val n = _xrows
      val s2 = SSE / n
      val eigvalue = _eigen.eigenvalues.copy
      val ldet_rho = sum(breeze.numerics.log(-eigvalue * rho + 1.0))
      val ldet_lambda = sum(breeze.numerics.log(-eigvalue * lambda + 1.0))
      val ret = (ldet_rho + ldet_lambda - ((n / 2.0) * log(2.0 * math.Pi)) - (n / 2.0) * log(s2) - (1.0 / (2.0 * (s2))) * SSE)
//      println(-ret)
      -ret
    } else {
      throw new IllegalArgumentException("optmize array should have rho and lambda")
    }
  }
}