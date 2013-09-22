// Copyright 2013 Christopher Swenson.
// Author: Christopher Swenson (chris@caswenson.com)
package com.caswenson.pizza.data

import com.caswenson.pizza.{ LatLon, Location }
import com.google.common.base.CharMatcher
import com.vividsolutions.jts.geom.impl.CoordinateArraySequenceFactory
import com.vividsolutions.jts.geom.{ Coordinate,
                                     Envelope,
                                     GeometryFactory,
                                     LineSegment,
                                     LineString,
                                     MultiLineString,
                                     Point }
import com.vividsolutions.jts.index.strtree.STRtree
import java.io.File
import org.geotools.data.FileDataStoreFinder
import org.geotools.data.shapefile.ShapefileDataStore
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.math.min
import grizzled.slf4j.Logging

/**
 * Handles geocoding and reverse geocoding queries.
 */
class Pizza(cities: Cities,
            reverseTree: STRtree,
            oddZipMap: mutable.OpenHashMap[String, mutable.OpenHashMap[String, Array[(Double, Point)]]],
            evenZipMap: mutable.OpenHashMap[String, mutable.OpenHashMap[String, Array[(Double, Point)]]]) extends Logging {

  val zipMatch = """\d{5}(-\d{4})?""".r
  val directions = Set("nw", "ne", "se", "sw")

  /**
   * Tries to geocode the given address, guessing which parts belong to which pieces.
   * This requires that the City, State, and ZIP are present somehow currently.
   */
  def geocode(address: String): Option[LatLon] = {
    // Delete commas, convert to lower case
    val parts = address.replace(",", "").toLowerCase.split(" ")
    val numberString = parts(0)
    // First part has to be a number
    if (!CharMatcher.JAVA_DIGIT.matchesAllOf(numberString)) {
      debug("No numeric address found in " + numberString)
      return None
    }
    val number = numberString.toInt

    // Take out the US / USA part
    val nonUsParts = if (parts.last == "usa" || parts.last == "us") {
      parts.dropRight(1).drop(1)
    } else {
      parts.drop(1)
    }

    // Check for ZIP
    val (zip, nonZipParts) = zipMatch.findFirstIn(nonUsParts.last) match {
      case Some(z) => {
        (Some(z.substring(0, 5)), nonUsParts.dropRight(1))
      }
      case None => {
        (None, nonUsParts)
      }
    }

    val state = nonZipParts.last.toUpperCase
    if (!AnsiStates.stateNames.contains(state)) {
      debug("Unknown state " + state)
      return None
    }

    val nonStateParts = nonZipParts.dropRight(1)

    // Split the rest up between street and city every which way we can.
    val streetStart = nonZipParts(0)
    val cityEnd = nonZipParts.last

    // Parts that could be either
    val either = nonZipParts.drop(1).dropRight(1)
    0.to(either.size).flatMap { i =>
      val street = (streetStart + " " + either.slice(0, i).mkString(" ")).trim
      val city = (either.slice(i, either.size).mkString(" ") + " " + cityEnd).trim
      geocode(number, street, city, state, zip)
    }.headOption
  }

  /**
   * Geocodes a given location to a latitude, longitude pair, if the given address makes any sense.
   */
  def geocode(number: Int, rawStreet: String, rawCity: String, rawState: String, zipOption: Option[String]): Option[LatLon] = {
    val street = rawStreet.toLowerCase

    zipOption match {
      case Some(zip) => {
        oddZipMap.get(zip) match {
          case Some(streetMap) =>
            streetMap.get(street).map { coords => pointIn(number, coords) }
          case None =>
            evenZipMap.get(zip) match {
              case Some(streetMap) =>
                streetMap.get(street).map { coords => pointIn(number, coords) }
              case None =>
                None
            }
        }
      }
      case None => {
        // No ZIP supplied
        // TODO(swenson): Use the ZCTAs to determine the proper zip.
        None
      }
    }
  }

  /**
   * Uses binary search and interpolation to find the closest geometric
   * point to the given address.
   * @param number Address number
   * @param points List of (address number, point)-pairs to find in.
   */
  def pointIn(number: Int, points: Array[(Double, Point)]): LatLon = {
    if (number < points.head._1) {
      pointFor(points.head._2)
    } else if (number > points.last._1) {
      pointFor(points.last._2)
    } else {
      var l = 0
      var c = points.size / 2
      var r = points.size - 1

      while (true) {
        if (c - l <= 1) {
          return interpolate(number, l, points)
        } else if (r - c <= 1) {
          return interpolate(number, c, points)
        }

        if (number < points(c)._1) {
          r = c
          c = (l + r) / 2
        } else {
          l = c
          c = (l + r) / 2
        }
      }
      null // unreachable, but scala compiler complains otherwise
    }
  }

  /**
   * Interpolates the address between index and index + 1 of the given array.
   */
  def interpolate(number: Int, index: Int, points: Array[(Double, Point)]): LatLon = {
    val (addr1, point1) = points(index)
    val (addr2, point2) = points(index + 1)

    val addrDelta = addr2 - addr1
    val line = new LineSegment(point1.getCoordinate, point2.getCoordinate)
    val lengthAlong = (number - addr1) / addrDelta
    val point = line.pointAlong(lengthAlong)
    LatLon(point.y, point.x)
  }

  /**
   * Converts a Point to a LatLon.
   */
  def pointFor(point: Point): LatLon = {
    LatLon(point.getY, point.getX)
  }

  /**
   * Capitalizes road parts, and makes directions all caps
   */
  def capitalize(road: String): String = {
    road.split(" ").map { piece =>
      if (directions.contains(piece)) {
        piece.toUpperCase
      } else {
        piece.capitalize
      }
    }.mkString(" ")
  }

  /**
   * Reverse geocodes the given point to the nearest address it can find.
   */
  def reverseGeocode(latLon: LatLon): Location = {
    val coord = new Coordinate(latLon.lon, latLon.lat)
    val point = new Point(CoordinateArraySequenceFactory.instance().create(Array(coord)), Pizza.geometryFactory)
    val search = new Envelope(coord)

    while (true) {
      // Expansive search
      val results = reverseTree.query(search).asInstanceOf[java.util.List[(LineString, Int, Int, String, String, String)]].toIndexedSeq
      if (results.size > 0) {

        val (lineString, minAddr, maxAddr, road, state, zip) = results.minBy { case(line, _, _, _, _, _) =>
          point.distance(line)
        }

        val capRoad = capitalize(road)

        val addrDelta = maxAddr - minAddr

        // Convert the line to line segments.
        val lineSegments = constructLineSegments(lineString.getCoordinates)

        // Find the nearest line segment
        val lineIndex = lineSegments.indices.minBy { li => lineSegments(li).distance(coord) }

        // Find the closest point on the line segment
        val nearestPoint = lineSegments(lineIndex).project(coord)

        // Use the distance from that point on the line to compute the address.
        val lineDistance = lineSegments(lineIndex).getCoordinate(0).distance(nearestPoint)
        val otherDistances = 0.until(lineIndex).map { i => lineSegments(i).getLength }.sum
        val address = ((otherDistances + lineDistance) / lineString.getLength * addrDelta + minAddr).toInt

        val city = cities.city(state, point).capitalize
        // TODO(swenson): fill in TZ?
        return Location(street = Some("%d %s".format(address, capRoad)), city = Some(city), state = Some(state), zip = Some(zip),
                 country = Some("USA"), lat = Some(latLon.lat), lon = Some(latLon.lon))
      } else {
        // Try again
        search.expandBy(1/10000.0)
      }
    }
    null // unreachable, but scala compiler complains otherwise
  }

  /**
   * Convertes a sequence of coordinates into a sequence of line segments.
   */
  def constructLineSegments(coords: Array[Coordinate]): Array[LineSegment] = {
    coords.sliding(2, 1).map { x => new LineSegment(x(0), x(1)) }.toArray
  }

  def cityFor(state: String, zip: String): String = {
    ""
  }
}

object Pizza extends Logging {
  val geometryFactory = new GeometryFactory
  val digits = CharMatcher.JAVA_DIGIT

  /**
   * Construct a new geocoder and reverse geocoder from the given data for the specified
   * states.
   */
  def apply(cities: Cities, dataDir: String, states: Set[String] = AnsiStates.states.values.toSet): Pizza = {
    var segmentsLoaded = 0
    // The left-hand and right-hand sides of the road may be in different zipcodes.
    // We sort them by even-numbered addresses and odd-numbered.
    val oddZipMap = mutable.OpenHashMap[String, mutable.OpenHashMap[String, Array[(Double, Point)]]]()
    val evenZipMap = mutable.OpenHashMap[String, mutable.OpenHashMap[String, Array[(Double, Point)]]]()

    // The reverse geocoding STRtree
    val reverseTree = new STRtree()

    // Scan for data files.
    val dir = new File(dataDir)
    val files = dir.listFiles()
      .filter { _.getName.endsWith(".shp") }
      // Only keep states we were told to process.
      .filter { file =>
        states.contains(AnsiStates.states(file.getName.split("_")(2).substring(0, 2).toInt))
      }

    files.foreach { file =>
      val stateId = file.getName.split("_")(2).substring(0, 2).toInt
      val state = AnsiStates.states(stateId)

      val dataStore = FileDataStoreFinder.getDataStore(file).asInstanceOf[ShapefileDataStore]
      info("Loading %s %s".format(state, file.getName))

      val featureReader = dataStore.getFeatureReader
      while (featureReader.hasNext) {
        val feature = featureReader.next()

        val line = feature.getAttribute("the_geom").asInstanceOf[MultiLineString]
        val fullName = feature.getAttribute("FULLNAME").toString.toLowerCase
        val leftFrom = feature.getAttribute("LFROMHN").toString
        val leftTo = feature.getAttribute("LTOHN").toString
        val rightFrom = feature.getAttribute("RFROMHN").toString
        val rightTo = feature.getAttribute("RTOHN").toString
        val zipLeft = feature.getAttribute("ZIPL").toString
        val zipRight = feature.getAttribute("ZIPR").toString
        // These might be useful in the future, though they aren't ever-present.
        //val zip9Left = feature.getAttribute("PLUS4L").toString
        //val zip9Right = feature.getAttribute("PLUS4R").toString

        // Only continue if everything is all digits.
        if (digits.matchesAllOf(leftFrom) &&
          digits.matchesAllOf(leftTo) &&
          digits.matchesAllOf(rightFrom) &&
          digits.matchesAllOf(rightTo)) {

          require(line.getNumGeometries == 1)
          val line0 = line.getGeometryN(0).asInstanceOf[LineString]

          // Build a map from this line to each house number, evenly distributed
          val numPoints = line0.getNumPoints
          val totalLength = line0.getLength
          val startPoint = if (leftFrom == "") {
            rightFrom.toInt
          } else if (rightFrom == "") {
            leftFrom.toInt
          } else {
            min(leftFrom.toInt, rightFrom.toInt)
          }
          val endPoint = if (leftTo == "") {
            rightTo.toInt
          } else if (rightTo == "") {
            leftTo.toInt
          } else {
            min(leftTo.toInt, rightTo.toInt)
          }
          val addressDiff = endPoint - startPoint
          var currPoint = startPoint.toDouble

          // Reverse geocoding info
          if (zipLeft != "") {
            reverseTree.insert(line0.getEnvelopeInternal, (line0, startPoint, endPoint, fullName, state, zipLeft))
          }

          if (zipRight != "" && zipLeft != zipRight) {
            reverseTree.insert(line0.getEnvelopeInternal, (line0, startPoint, endPoint, fullName, state, zipRight))
          }

          // Geocoding info
          val points = (0.until(numPoints - 1).map { i: Int =>
            val point1 = line0.getPointN(i)
            val point2 = line0.getPointN(i + 1)
            val length = point2.distance(point1)
            val delta = length / totalLength * addressDiff
            currPoint += delta
            (currPoint - delta, point1)
          } ++ Seq((currPoint, line0.getEndPoint))).toArray


          // Populate the odd and even address number zip codes.
          if (zipLeft != "") {
            val toUpdate = if ((leftFrom.toInt & 1) == 1) {
              oddZipMap
            } else {
              evenZipMap
            }

            val zipData = toUpdate.getOrElseUpdate(zipLeft, mutable.OpenHashMap[String, Array[(Double, Point)]]())
            val streetData = zipData.getOrElse(fullName, Array[(Double, Point)]())
            zipData += (fullName -> (streetData ++ points).toSet.toArray.sortBy(_._1))

            segmentsLoaded += 1
          }

          if (zipRight != "") {
            val toUpdate = if ((rightFrom.toInt & 1) == 1) {
              oddZipMap
            } else {
              evenZipMap
            }

            val zipData = toUpdate.getOrElseUpdate(zipRight, mutable.OpenHashMap[String, Array[(Double, Point)]]())
            val streetData = zipData.getOrElse(fullName, Array[(Double, Point)]())
            zipData += (fullName -> (streetData ++ points).toSet.toArray.sortBy(_._1))
          }
        }
      }
      try {
        featureReader.close()
      } catch {
        case e: IllegalArgumentException => // Ignore buggy FeatureReader
      }
    }
    info("Segments loaded: %d".format(segmentsLoaded))
    new Pizza(cities, reverseTree, oddZipMap, evenZipMap)
  }
}
