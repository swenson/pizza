// Copyright 2013 Christopher Swenson.
// Author: Christopher Swenson (chris@caswenson.com)
package com.caswenson.pizza

import com.caswenson.pizza.data.{Cities, Pizza}
import com.caswenson.pizza.resource.PizzaResource
import com.yammer.dropwizard.ScalaService
import com.yammer.dropwizard.config.{Bootstrap, Environment, Configuration}
import com.yammer.dropwizard.bundles.ScalaBundle

class PizzaConfig extends Configuration {
  var censusData = "census/addrfeat"
  var placesData = "census/place"
}

object PizzaService extends ScalaService[PizzaConfig] {
  override def initialize(bootstrap: Bootstrap[PizzaConfig]): Unit = {
    bootstrap.setName("pizza")
    bootstrap.addBundle(new ScalaBundle)
  }

  override def run(config: PizzaConfig, env: Environment) {
    val cities = Cities(config.placesData)
    val pizza = Pizza(cities, config.censusData, Set("OR"))

    env.addResource(new PizzaResource(pizza))
  }
}
