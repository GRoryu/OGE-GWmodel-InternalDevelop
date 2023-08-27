package whu.edu.cn.debug.GWmodelUtil

import geotrellis.vector.MultiPolygon
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import whu.edu.cn.util.ShapeFileUtil._

import scala.reflect.ClassTag
import breeze.numerics._

import java.text.SimpleDateFormat
import breeze.linalg.{DenseMatrix, DenseVector, Matrix, Vector, linspace}
import whu.edu.cn.debug.GWmodelUtil.GWModels.GWRbasic
import whu.edu.cn.debug.GWmodelUtil.Utils.FeatureDistance._
import whu.edu.cn.debug.GWmodelUtil.Utils.FeatureSpatialWeight._
import whu.edu.cn.debug.GWmodelUtil.STCorrelations.SpatialAutocorrelation._
import whu.edu.cn.debug.GWmodelUtil.STCorrelations.TemporalAutocorrelation._
import whu.edu.cn.debug.GWmodelUtil.Utils.OtherUtils._
import whu.edu.cn.debug.GWmodelUtil.SpatialRegression.LinearRegression._
import whu.edu.cn.debug.GWmodelUtil.SpatialRegression.SARerrormodel
import whu.edu.cn.debug.GWmodelUtil.SpatialRegression.SARlagmodel
import whu.edu.cn.debug.GWmodelUtil.SpatialRegression.SARdurbinmodel

object testRun {

  //global variables
  val conf: SparkConf = new SparkConf().setMaster("local[8]").setAppName("query")
  val sc = new SparkContext(conf)

//  val shpPath: String = "testdata\\MississippiHR.shp"
  val shpPath: String = "testdata\\LNHP100.shp"
  val shpfile = readShp(sc, shpPath, DEF_ENCODE) //或者直接utf-8

  //写成无参数的函数形式来进行测试，方便区分，以后可以改成 catch...if... 形式
  def main(args: Array[String]): Unit = {
    //    val t0 = System.currentTimeMillis()
//    sarmodel_test()
//    morani_test()
//    acf_test()
//    linear_test()
    gwrbasic_test()
  }

  def gwrbasic_test(): Unit = {
    val t1 = System.currentTimeMillis()
    val x1 = shpfile.map(t => t._2._2("FLOORSZ").asInstanceOf[String].toDouble).collect()
    val x2 = shpfile.map(t => t._2._2("PROF").asInstanceOf[String].toDouble).collect()
    val y = shpfile.map(t => t._2._2("PURCHASE").asInstanceOf[String].toDouble).collect()
    val x = Array(DenseVector(x1), DenseVector(x2))
    //    x.foreach(println)
    val mdl = new GWRbasic //error，lag
    mdl.init(shpfile)
    mdl.setweight(10,"gaussian",true)
    mdl.setX(x)
    mdl.setY(y)
    mdl.fit()
//    val tused = (System.currentTimeMillis() - t1) / 1000.0
//    println(s"time used is $tused s")
  }

  def sarmodel_test(): Unit = {
    val t1 = System.currentTimeMillis()
    val x1 = shpfile.map(t => t._2._2("PO60").asInstanceOf[String].toDouble).collect()
    val x2 = shpfile.map(t => t._2._2("UE60").asInstanceOf[String].toDouble).collect()
    val y = shpfile.map(t => t._2._2("HR60").asInstanceOf[String].toDouble).collect()
    val x = Array(DenseVector(x1), DenseVector(x2))
    //    x.foreach(println)
    val mdl = new SARdurbinmodel//error，lag
    mdl.init(shpfile)
    mdl.setX(x)
    mdl.setY(y)
    mdl.fit()
    val tused = (System.currentTimeMillis() - t1) / 1000.0
    println(s"time used is $tused s")
  }

  def morani_test(): Unit = {
    val t1 = System.currentTimeMillis()
    val globali = globalMoranI(shpfile, "HR60", plot = true, test = true)
    println(s"global Moran's I is: ${globali._1}")
    val locali = localMoranI(shpfile, "HR60")
    println("-----------local moran's I--------------")
    locali._1.foreach(println)
    println("-----------p-value--------------")
    locali._5.foreach(println)
    //    val result1 = writeRDD(sc, shpfile, locali._1, "moran_i")
    //    val result2 = writeRDD(sc, result1, locali._2, "expect")
    //    val outputpath = "testdata\\MississippiMoranI.shp"
    //    writeshpfile(result2, outputpath)
    val tused = (System.currentTimeMillis() - t1) / 1000.0
    println(s"time used is $tused s")
  }

  def acf_test(): Unit = {
    val t1 = System.currentTimeMillis()
    val csvpath = "D:\\Java\\testdata\\test_aqi.csv"
    val csvdata = readcsv(sc, csvpath)
    //test date calculator
    val timep = attributeSelectHead(csvdata, "time_point")
    val timepattern = "yyyy/MM/dd"
    val date = timep.map(t => {
      val date = new SimpleDateFormat(timepattern).parse(t)
      date
    })
    date.foreach(println)
    println((date(300).getTime - date(0).getTime) / 1000 / 60 / 60 / 24)
    val tem = attributeSelectHead(csvdata, "temperature")
    //    tem.foreach(println)
    val db_tem = tem.map(t => t.toDouble)
    //    println(db_tem.sum)
    val tem_acf = timeSeriesACF(db_tem, 30)
    tem_acf.foreach(println)
    val tused = (System.currentTimeMillis() - t1) / 1000.0
    println(s"time used is $tused s")
  }

  def linear_test(): Unit = {
    val t1 = System.currentTimeMillis()
    val csvpath = "D:\\Java\\testdata\\test_aqi.csv"
    val csvdata2 = readcsv(sc, csvpath)
    val aqi = attributeSelectNum(csvdata2, 2).map(t => t.toDouble)
    val per = attributeSelectHead(csvdata2, "precipitation").map(t => t.toDouble)
    val tem = attributeSelectHead(csvdata2, "temperature").map(t => t.toDouble)
    val x = Array(DenseVector(tem), DenseVector(per))
    val re = linearRegression(x, DenseVector(aqi))
    println(re._1)
    println(re._2)
    println(re._3)
    val tused = (System.currentTimeMillis() - t1) / 1000.0
    println(s"time used is $tused s")
  }

}