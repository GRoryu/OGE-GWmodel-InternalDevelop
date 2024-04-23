package whu.edu.cn.algorithms.SpatialStats.STSampling

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.locationtech.jts.geom.Geometry

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.math.{max, min}
import scala.util.Random

object Sampling {

  def randomSampling(sc: SparkContext, featureRDD: RDD[(String, (Geometry, mutable.Map[String, Any]))], n: Int = 10): RDD[(String, (Geometry, mutable.Map[String, Any]))] = {
    val feat = featureRDD.collect()
    val nCounts = featureRDD.count().toInt
    //      val extents = featureRDD.map(t => t._2._1.getCoordinate).map(t => {
    //        (t.x, t.y, t.x, t.y)
    //      }).reduce((coor1, coor2) => {
    //        (min(coor1._1, coor2._1), min(coor1._2, coor2._2), max(coor1._3, coor2._3), max(coor1._4, coor2._4))
    //      })

    val rand = Array.fill(n)(Random.nextDouble()).map(t => (t * nCounts).toInt)
    val arrbuf = ArrayBuffer.empty[(String, (Geometry, mutable.Map[String, Any]))]
    for (i <- 0 until n) {
      arrbuf += feat(rand(i))
    }
    arrbuf.foreach(t=>println(t._2._2))
    sc.makeRDD(arrbuf)
  }

  def regularSampling(sc: SparkContext, featureRDD: RDD[(String, (Geometry, mutable.Map[String, Any]))], x: Int = 10, y : Int = 10): RDD[(String, (Geometry, mutable.Map[String, Any]))] = {
    val feat = featureRDD.collect()
    val nCounts = featureRDD.count().toInt
    val extents = featureRDD.map(t => t._2._1.getCoordinate).map(t => {
      (t.x, t.y, t.x, t.y)
    }).reduce((coor1, coor2) => {
      (min(coor1._1, coor2._1), min(coor1._2, coor2._2), max(coor1._3, coor2._3), max(coor1._4, coor2._4))
    })

    val sortx=featureRDD.sortBy(t=>t._2._1.getCoordinate.x)
    val sorty=featureRDD.sortBy(t=>t._2._1.getCoordinate.y)
//    featureRDD.collect().foreach(t => println(t._2._2))
    println(extents)
    println("*************")
//    sortx.collect().foreach(t => println(t._2._2))
    val devx=(extents._3-extents._1)/x+1e-5
    println(devx)
    val group=sortx.groupBy(t=>((t._2._1.getCoordinate.x-extents._1)/devx).toInt)
    group.foreach(println)

    val rand = Array.fill(x)(Random.nextDouble()).map(t => (t * nCounts).toInt)
    val arrbuf = ArrayBuffer.empty[(String, (Geometry, mutable.Map[String, Any]))]
    for (i <- 0 until x) {
      arrbuf += feat(rand(i))
    }
//    arrbuf.foreach(t => println(t._2._2))
    sc.makeRDD(arrbuf)
  }

  def randSampling(inputRDD: RDD[(String, (Geometry, Map[String, Any]))]): RDD[(String, (Geometry, Map[String, Any]))] = {
    val indexArray = inputRDD.zipWithIndex;
    val len = indexArray.count();
    val upperBound = len.toInt;
    val randomNumber = Random.nextInt(upperBound);
    val filteredRDD = indexArray.filter { case (_, idx) => idx == randomNumber }.map(_._1)

    filteredRDD
  }

  def continuousSampling(inputRDD: RDD[(String, (Geometry, Map[String, Any]))], gap: Double): RDD[(String, (Geometry, Map[String, Any]))] = {
    val indexArray = inputRDD.zipWithIndex;
    val len = indexArray.count();
    val upperBound = len.toInt;
    val array = Array.range(0, upperBound);
    val multiplesOfGap = array.filter(_ % gap == 0);
    val filteredRDD = multiplesOfGap.map(index => indexArray.filter { case (_, idx) => idx == index }.map(_._1))
    val mergedRDD = filteredRDD.reduce((rdd1, rdd2) => rdd1.union(rdd2))

    mergedRDD
  }

  def stratifiedSampling(inputRDD: RDD[(String, (Geometry, Map[String, Any]))], layer: Double): RDD[(String, (Geometry, Map[String, Any]))] = {
    val indexArray = inputRDD.zipWithIndex;
    val len = indexArray.count();
    val lowerBound = layer.toInt;
    val upperBound = len.toInt;
    var randomNumber = Array.ofDim[Int](2)
    randomNumber(0) = Random.nextInt(lowerBound);
    randomNumber(1) = lowerBound + Random.nextInt(upperBound - lowerBound);
    val filteredRDD = randomNumber.map(index => indexArray.filter { case (_, idx) => idx == index }.map(_._1))
    val mergedRDD = filteredRDD.reduce((rdd1, rdd2) => rdd1.union(rdd2))

    mergedRDD
  }


  def randomPoints(xmin: Double, ymin: Double, xmax: Double, ymax: Double, np: Int): Array[(Double, Double)] = {
    Array.fill(np)(Random.nextDouble(), Random.nextDouble()).map(t => (t._1 * (xmax - xmin) + xmin, t._2 * (ymax - ymin) + ymin))
  }



}
