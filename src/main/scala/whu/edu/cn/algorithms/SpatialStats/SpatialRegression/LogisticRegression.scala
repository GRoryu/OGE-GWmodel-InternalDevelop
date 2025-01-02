package whu.edu.cn.algorithms.SpatialStats.SpatialRegression

import breeze.linalg._
import breeze.linalg.{DenseMatrix, DenseVector, inv, sum}
import breeze.linalg.operators.DenseMatrixOps
import breeze.linalg.operators
import breeze.numerics._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.locationtech.jts.geom.Geometry

import whu.edu.cn.oge.Service

import scala.collection.{breakOut, mutable}

object LogisticRegression {

  private var _data: RDD[mutable.Map[String, Any]] = _
  private var _X: DenseMatrix[Double] = _
  private var _Y: DenseVector[Double] = _
  private var _1X: DenseMatrix[Double] = _
  private var _nameX: Array[String] = _
  private var _rows: Int = 0
  private var _df: Int = 0

  private def setX(properties: String, split: String = ",", Intercept: Boolean): Unit = {
    _nameX = properties.split(split)
    val x = _nameX.map(s => {
      _data.map(t => t(s).asInstanceOf[String].toDouble).collect()
    })
    _rows = x(0).length
    _df = x.length
    _X = DenseMatrix.create(rows = _rows, cols = x.length, data = x.flatten)
    if (Intercept) {
      val ones_x = Array(DenseVector.ones[Double](_rows).toArray, x.flatten)
      _1X = DenseMatrix.create(rows = _rows, cols = x.length + 1, data = ones_x.flatten)
    }
  }

  private def setY(property: String): Unit = {
    _Y = DenseVector(_data.map(t => t(property).asInstanceOf[String].toDouble).collect())
  }

  def LogisticRegression(sc: SparkContext, data: RDD[(String, (Geometry, mutable.Map[String, Any]))],
                       y: String, x: String, Intercept: Boolean = true,
                       maxIter: Int = 100, epsilon: Double = 1e-6,learningRate: Double = 0.01)
  : /*(DenseVector[Double], Double)*/Unit = {
    _data = data.map(t=>t._2._2)
    val split = ","
    setX(x, split, Intercept)
    setY(y)
    var X = _1X
    if (!Intercept) {
      X = _X
    }
    val Y = _Y

    // 初始化参数
    var weights = DenseVector.zeros[Double](X.cols)

    // 训练模型
    var converged = false
    var iter = 0

    while (!converged && iter < maxIter) {
      // 计算预测值
      val z = X * weights
      val p = sigmoid(z)

      val w = p.map(t => t*(1-t))
      val n = w.length
      val W = DenseMatrix.zeros[Double](n,n)
      for(i<-0 until n){
        W(i,i)=w(i)
      }

      // Fisher Update
      val xtwx = X.t * W * X
      val xtwx_inv = inv(xtwx)
      val workingY = z + (Y - p)/w
      val xtwy = X.t * W * workingY
      val newWeights = xtwx_inv*xtwy

      // 检查收敛
      val weightChange = breeze.linalg.norm(newWeights-weights)
      if(weightChange<epsilon){
        converged = true
      }
      weights = newWeights
      iter +=1
    }

    //yhat, residual
    val yhat = sigmoid(X * weights)
    val res = (Y - yhat)

    // deviance residuals
    val devRes = DenseVector.zeros[Double](Y.length)
    for (i<- 0 until Y.length){
      val y = Y(i)
      val mu = yhat(i)
      val eps = 1e-10
      val clippedMu = max(eps,min(1-eps,mu))

      val dev = if(y==1){
        2*log(1/clippedMu)
      }else{
        2*math.log(1/(1-clippedMu))
      }
      devRes(i)=signum(y-mu)*sqrt(abs(dev))
    }


    //输出
    var str = "\n********************Results of Logistic Regression********************\n"

    var formula = f"${y} ~ "
    for(i <- 1 until X.cols){
      formula += f"+ ${_nameX(i-1)} "
    }
    str += "Formula:\n" + formula + f"\n"

    str += "\n"
    str += f"Deviance Residuals: \n"+
      f"min: ${devRes.toArray.min.formatted("%.4f")}  "+
      f"max: ${devRes.toArray.max.formatted("%.4f")}  "+
      f"mean: ${breeze.stats.mean(devRes).formatted("%.4f")}  "+
      f"median: ${breeze.stats.median(devRes).formatted("%.4f")}\n"

    str += "\n"
    str += "Coefficients:\n"
    if(Intercept){
      str += f"Intercept:${weights(0).formatted("%.6f")}\n"
    }
    for(i <- 1 until (X.cols)){
      str += f"${_nameX(i-1)}: ${weights(i).formatted("%.6f")}\n"
    }

//    str += "\n"
//    str += f"${res.toArray.min},${res.toArray.max},${res.toArray.sum/res.length}\n"

    str += "\n"
    str += f"Number of Iterations: ${iter}\n"

    str += "**********************************************************************\n"
    print(str)
  }

  // 添加预测方法
  def predict(X: DenseMatrix[Double], weights: DenseVector[Double], intercept: Double): DenseVector[Double] = {
    val z = if (intercept != 0.0) {
      val X1 = DenseMatrix.horzcat(DenseMatrix.ones[Double](X.rows, 1), X)
      X1 * DenseVector.vertcat(DenseVector(intercept), weights)
    } else {
      X * weights
    }

    z.map(x => if (1.0 / (1.0 + math.exp(-x)) > 0.5) 1.0 else 0.0)
  }


}
