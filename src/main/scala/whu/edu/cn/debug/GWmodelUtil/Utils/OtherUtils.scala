package whu.edu.cn.debug.GWmodelUtil.Utils

import au.com.bytecode.opencsv._

import org.apache.spark.SparkContext
import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.rdd.RDD
import org.locationtech.jts.geom.{Coordinate, Geometry, LineString, Point, MultiPolygon}
import whu.edu.cn.util.ShapeFileUtil._
import scala.collection.JavaConverters._
import java.text.SimpleDateFormat

import java.io.StringReader
import scala.collection.mutable.Map
import scala.math.pow

object OtherUtils {

  def readcsv(implicit sc: SparkContext, csvPath: String): RDD[Array[(String, Int)]] = {
    val data = sc.textFile(csvPath)
    val csvdata = data.map(line => {
      val reader = new CSVReader(new StringReader((line)))
      reader.readNext()
    })
    csvdata.map(t => t.zipWithIndex)
  }

  def getGeometryType(geomRDD: RDD[(String, (Geometry, Map[String, Any]))]): String = {
    geomRDD.map(t => t._2._1).first().getGeometryType
  }

  /**
   * 输入要添加的属性数据和RDD，输出RDD
   *
   * @param sc           SparkContext
   * @param shpRDD       源RDD
   * @param writeArray   要写入的属性数据，Array形式
   * @param propertyName 属性名，String类型，需要少于10个字符
   * @return RDD
   */
  def writeRDD(implicit sc: SparkContext, shpRDD: RDD[(String, (Geometry, Map[String, Any]))], writeArray: Array[Double], propertyName: String): RDD[(String, (Geometry, Map[String, Any]))] = {
    if (propertyName.length >= 10) {
      throw new IllegalArgumentException("the length of property name must not longer than 10!!")
    }
    val shpRDDidx = shpRDD.collect().zipWithIndex
    shpRDDidx.map(t => {
      t._1._2._2 += (propertyName -> writeArray(t._2))
    })
    sc.makeRDD(shpRDDidx.map(t => t._1))
  }

  def writeshpfile(outputshpRDD: RDD[(String, (Geometry, Map[String, Any]))], outputshpPath: String) = {
    val geom = getGeometryType(outputshpRDD)
    geom match {
      case "MultiPolygon" => createShp(outputshpPath, "utf-8", classOf[MultiPolygon], outputshpRDD.map(t => {
        t._2._2 + (DEF_GEOM_KEY -> t._2._1)
      }).collect().map(_.asJava).toList.asJava)
      case "Point" => createShp(outputshpPath, "utf-8", classOf[Point], outputshpRDD.map(t => {
        t._2._2 + (DEF_GEOM_KEY -> t._2._1)
      }).collect().map(_.asJava).toList.asJava)
      case "LineString" => createShp(outputshpPath, "utf-8", classOf[LineString], outputshpRDD.map(t => {
        t._2._2 + (DEF_GEOM_KEY -> t._2._1)
      }).collect().map(_.asJava).toList.asJava)
      case _ => throw new IllegalArgumentException("can not modified geometry type, please retry")
    }
    println(s"shpfile written successfully in $outputshpPath")
  }

  def attributeSelectHead(csvRDD: RDD[Array[(String, Int)]], property: String): Array[String] = {
    val head = csvRDD.collect()(0)
    val csvArr = csvRDD.collect().drop(1)
    var resultArr = new Array[String](csvArr.length)
    var idx: Int = -1
    head.map(t => {
      if (t._1 == property) idx = t._2
    })
    //    println(idx)
    if (idx == -1) {
      throw new IllegalArgumentException("property name didn't exist!!")
    } else {
      val df = csvArr.map(t => t.filter(i => i._2 == idx).map(t => t._1))
      resultArr = df.flatMap(t => t)
    }
    //    resultArr.foreach(println)
    resultArr
  }

  def attributeSelectNum(csvRDD: RDD[Array[(String, Int)]], number: Int): Array[String] = {
    val head = csvRDD.collect()(0)
    val csvArr = csvRDD.collect().drop(1)
    var resultArr = new Array[String](csvArr.length)
    val idx: Int = number - 1 //从1开始算
    //    println(idx)
    if (idx >= head.length || idx < 0) {
      throw new IllegalArgumentException("property number didn't exist!!")
    } else {
      val df = csvArr.map(t => t.filter(i => i._2 == idx).map(t => t._1))
      resultArr = df.flatMap(t => t)
    }
    //    resultArr.foreach(println)
    resultArr
  }

  def readtimeExample(implicit sc: SparkContext, csvpath: String, timeproperty: String, timepattern: String = "yyyy/MM/dd"): Unit = {
    val csvdata = readcsv(sc, csvpath)
    val timep = attributeSelectHead(csvdata, timeproperty)
    val date = timep.map(t => {
      val date = new SimpleDateFormat(timepattern).parse(t)
      date
    })
    date.foreach(println)
    println((date(300).getTime - date(0).getTime) / 1000 / 60 / 60 / 24)
  }

  def corr2list(lst1: List[Double], lst2: List[Double]): Double = {
    val sum1 = lst1.sum
    val sum2 = lst2.sum
    val square_sum1 = lst1.map(x => x * x).sum
    val square_sum2 = lst2.map(x => x * x).sum
    val zlst = lst1.zip(lst2)
    val product = zlst.map(x => x._1 * x._2).sum
    val numerator = product - (sum1 * sum2 / lst1.length)
    val dominator = pow((square_sum1 - pow(sum1, 2) / lst1.length) * (square_sum2 - pow(sum2, 2) / lst2.length), 0.5)
    val correlation = numerator / (dominator * 1.0)
    println(s"Correlation is: $correlation")
    correlation
  }

  def corr2ml(feat: RDD[(String, (Geometry, Map[String, Any]))], property1: String, property2: String): Double = {
    val time0: Long = System.currentTimeMillis()
    val aX: RDD[Double] = feat.map(t => t._2._2(property1).asInstanceOf[String].toDouble)
    val aY: RDD[Double] = feat.map(t => t._2._2(property2).asInstanceOf[String].toDouble)
    val correlation: Double = Statistics.corr(aX, aY, "pearson") //"spearman"
    val timeused: Long = System.currentTimeMillis() - time0
    println(s"Correlation is: $correlation")
    println(s"time used is: $timeused")
    correlation
  }

}
