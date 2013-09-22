// Copyright 2013 Christopher Swenson.
// Author: Christopher Swenson (chris@caswenson.com)

package com.caswenson.pizza

case class LatLon(lat: Double, lon: Double)

case class Location(street: Option[String],
                    city: Option[String],
                    state: Option[String],
                    zip: Option[String],
                    country: Option[String],
                    lat: Option[Double],
                    lon: Option[Double])
