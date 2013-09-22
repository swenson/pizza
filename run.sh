#!/bin/bash

java -Dnewrelic.environment=production -javaagent:newrelic.jar -cp pizza-1.0.0-jar-with-dependencies.jar com.caswenson.pizza.PizzaService server conf/service/pizza.yml