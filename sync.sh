#!/bin/bash

rsync -v target/pizza-1.0.0-jar-with-dependencies.jar swenson@swenson:pizza/
rsync -avz --exclude 'target' * swenson@swenson:pizza/