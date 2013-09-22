// Copyright 2013 Christopher Swenson.
// Author: Christopher Swenson (chris@caswenson.com)
package com.caswenson.pizza.resource

import com.caswenson.pizza.data.Pizza
import com.caswenson.pizza.{Location, LatLon}
import com.yammer.metrics.annotation.{ExceptionMetered, Timed}
import com.yammer.metrics.scala.Instrumented
import javax.ws.rs._
import javax.ws.rs.core.{Response, MediaType}

@Path("/")
@Produces(Array(MediaType.APPLICATION_JSON))
class PizzaResource(pizza: Pizza) extends Instrumented {

  @Path("/geocode")
  @GET
  @Timed
  @ExceptionMetered
  def geocode(@QueryParam("address") address: String): LatLon = {
    pizza.geocode(address).getOrElse {
      throw new WebApplicationException(Response.status(404).build())
    }
  }

  @Path("/reverse-geocode")
  @GET
  @Timed
  @ExceptionMetered
  def reverse_geocode(@QueryParam("lat") lat: String,
                      @QueryParam("lon") lon: String): Location = {
    pizza.reverseGeocode(LatLon(lat.toDouble, lon.toDouble))
  }
}
