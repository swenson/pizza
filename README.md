pizza
=====

pizza is a simple census-data based geocoding and reverse geocoding system.
It loads up the US Census data and uses the ADDRFEAT and PLACE data to find
good matches for well-formed street addresses, or tries to find the best address
for a given latitude / longitude.

You can also test it out on `geo.1so.me:27368` (see below).

Missing features
----------------

There a few features that are missing to make this a full-featured geocoder

* Expansion of abbreviations where necessary (e.g., Col Gateway Dr => Columbia Gateway Dr)
* Contraction of abbreviations (e.g., Columbia Gateway Drive => Columbia Gateway Dr)
* Nearest neighbor street name match (e.g., 920 NW 15th => 920 NW 15th St)
* Spell correction of streets, cities

Running
=======

First, you need all the data.
Run `get_data.py` to fetch all of the census data (2.9 GB downloaded, unpacks
to 15 GB).

Assuming you have maven installed, you should be able to run it with:

```bash
mvn clean compile exec:java -Dexec.mainClass="com.caswenson.pizza.PizzaService" -Dexec.args="server conf/service/pizza.yml"
```

This currently only loads the data for Oregon, as the data sets are very large.

Endpoints
=========

```
GET /geocode?address=something

{ "lat": 43.4, "lon": -122.4 }
```

```
GET /reverse-geocode?lat=42.2&lon=-122

{ "street": "720 NW Davis St", "city": "Portland", "state": "OR",
  "zip": "97209", "lat": 42.2m "lon": -122, "source_id": "pizza",
  "country": "USA" }
```

Example
-------

```bash
$ curl "geo.1so.me:27368/geocode?address=1005%20W%20Burnside%20St,%20Portland,%20OR,%2097209"
{"lat":45.52297339583333,"lon":-122.68117162499999}

$ curl "geo.1so.me:27368/reverse-geocode?lat=45.522973&lon=-122.681172"
{"street":"1005 W Burnside St","city":"Portland","state":"OR","zip":"97209","country":"USA","lat":45.522973,"lon":-122.681172}
```