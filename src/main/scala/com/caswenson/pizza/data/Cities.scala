// Copyright 2013 Christopher Swenson.
// Author: Christopher Swenson (chris@caswenson.com)
package com.caswenson.pizza.data

import com.vividsolutions.jts.geom.{Point, MultiPolygon}
import com.vividsolutions.jts.index.strtree.STRtree
import grizzled.slf4j.Logging
import java.io.File
import org.geotools.data.FileDataStoreFinder
import org.geotools.data.shapefile.ShapefileDataStore
import scala.collection.mutable

class Cities(stateTrees: mutable.OpenHashMap[String, STRtree]) {
  def city(state: String, point: Point): String = {
    val options = stateTrees(state).query(point.getEnvelopeInternal)
    require(options.size() > 0, "Could not find a city for the given point")
    options.get(0).toString
  }
}

object Cities extends Logging {
  def apply(dataDir: String): Cities = {
    val stateTrees = mutable.OpenHashMap[String, STRtree]()

    // Scan for data files.
    val dir = new File(dataDir)
    val files = dir.listFiles()
      .filter { _.getName.endsWith(".shp") }

    files.foreach { file =>
      val stateId = file.getName.split("_")(2).substring(0, 2).toInt
      val state = AnsiStates.states(stateId)
      val tree = stateTrees.getOrElseUpdate(state, new STRtree)

      val dataStore = FileDataStoreFinder.getDataStore(file).asInstanceOf[ShapefileDataStore]
      info("Loading %s %s".format(state, file.getName))

      val featureReader = dataStore.getFeatureReader
      while (featureReader.hasNext) {
        val feature = featureReader.next()
        val poly = feature.getAttribute("the_geom").asInstanceOf[MultiPolygon]
        val place = feature.getAttribute("NAME").toString.toLowerCase
        tree.insert(poly.getEnvelopeInternal, place)
      }

      try {
        featureReader.close()
      } catch {
        case e: IllegalArgumentException => // ignore buggy feature reader
      }
    }
    new Cities(stateTrees)
  }
}
