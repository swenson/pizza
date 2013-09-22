// Copyright 2013 Christopher Swenson.
// Author: Christopher Swenson (chris@caswenson.com)
package com.caswenson.pizza

import com.caswenson.pizza.data.{Pizza, Cities}
import com.simple.simplespec.Spec
import org.junit.Test

object PizzaSpec {
  lazy val cities = Cities("census/place")
  lazy val orPizza = Pizza(cities, "census/addrfeat", Set("OR"))
}

class PizzaSpec extends Spec {

  def isNear(a: LatLon, b: LatLon, tolerance: Double = 0.0001): Boolean = {
    ((a.lat - b.lat) * (a.lat - b.lat) + (a.lon - b.lon) * (a.lon - b.lon)) <= tolerance
  }

  class `Pizza tests` {
    @Test def `Check 720 nw davis st is reasonable`() {
      isNear(PizzaSpec.orPizza.geocode(1005, "W Burnside St", "Portland", "OR", Some("97209")).get,
             LatLon(45.522973, -122.681172)).must(be(true))
    }

    @Test def `Check 720 nw davis street freeform works`() {
      isNear(PizzaSpec.orPizza.geocode("1005 W Burnside St Portland OR 97209").get,
        LatLon(45.522973, -122.681172)).must(be(true))
    }

    @Test def `Check reverse geocode of 720 nw davis st`() {
      PizzaSpec.orPizza.reverseGeocode(LatLon(45.522973,-122.681172)).must(be(
        Location(street = Some("1005 W Davis St"),
                 city = Some("Portland"),
                 state = Some("OR"),
                 zip = Some("97209"),
                 country = Some("USA"),
                 lat = Some(45.52297339583333),
                 lon = Some(-122.68117162499999))))
    }
  }

}
