# sheaveline

[![CI](https://github.com/rkaushik-dev/sheaveline/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/sheaveline/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Maven](https://img.shields.io/badge/build-Maven-C71A36)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

Lift department records for a passenger ropeway operation. Six machines on a
Wasatch front hill, one haul rope each, and about a hundred and forty operating
days a year to account for.

This is the system the lift department works at. It holds the plant, the day
book, the inspection ladder, the rope file, the weather holds, the evacuation
plan and the incident register, and it prints the four pages that leave the
department: the morning report, the season summary, the rope file and the annual
packet the state tramway board reads.

## Why it is shaped like this

**Every measure is an integer.** A rope diameter is whole mils, a duration is
whole seconds, a temperature is whole tenths of a degree Fahrenheit. Nothing is
a float, on the wire or in the database, because a rope diameter that reads
1.3779999 is a rope diameter that fails a comparison it should pass.

**Rules that decide what other rules measure live together.** The number of
rescue teams on the hill decides how the line is cut into beats, which decides
where each team parks, which decides how long the busiest one is busy, which
decides how much daylight the last chair needs, which decides what time the lift
may stop loading. That chain is in one file rather than in five people's heads.

**Reports render, they do not fetch.** A page that goes and looks things up can
only ever print today, and the manager who wants to know why the summit quad was
down on the eighth of February is asking about a Tuesday two months gone.

**A refusal is a sentence.** Every failure the domain raises comes out of the
command line as one line and an exit code, and out of the http surface as one
body shape with a kind, a sentence and the field that caused it. The person
reading it is wearing gloves.

## What it is built with

- Java 21, built with Maven through the wrapper in the repository
- Spring Boot 3.5: Web MVC for the http surface, JPA over Hibernate for the
  records, Bean Validation for the request shapes
- Flyway for the schema, and H2 for the database: a file on disk by default, and
  in memory for the tests
- picocli for the command line
- springdoc for the OpenAPI document
- JUnit 5 and AssertJ for the tests, Spotless with palantir-java-format for the
  layout, JaCoCo for the coverage gate

## Running it

You need a JDK 21. The wrapper fetches the Maven it wants.

```
./mvnw -B verify                              # build it, and run every check
java -jar target/sheaveline-0.1.0.jar setup   # build the tables
java -jar target/sheaveline-0.1.0.jar guide   # the order the office does things in
java -jar target/sheaveline-0.1.0.jar --help
```

The database, the resort name, the journal level and the key file are read from
the environment. `sheaveline settings` prints what it found.

| variable | what it is | default |
| --- | --- | --- |
| `SHEAVELINE_DATABASE_URL` | where the records live, as a JDBC url | `jdbc:h2:file:./sheaveline`, a file beside the program |
| `SHEAVELINE_RESORT` | the name on the printed pages | Ogden Peak |
| `SHEAVELINE_JOURNAL` | what the software says about itself | info |
| `SHEAVELINE_KEY_FILE` | the api keys | not set, and the http surface refuses everything |
| `SHEAVELINE_HOST` / `SHEAVELINE_PORT` | where the http surface listens | 127.0.0.1:8451 |

A flag on the command line beats the environment. The command line writes no
journal unless `--journal` or `SHEAVELINE_JOURNAL` names a level, because its
pages go to standard output and a journal line in the middle of one is a page
nobody can read.

## The command line

Below, `sheaveline` is short for `java -jar target/sheaveline-0.1.0.jar`.

```
sheaveline [--database URL] [--journal LEVEL] [--version] [--help] COMMAND

sheaveline setup                            build the tables; safe to run again
sheaveline tables                           list the tables the program knows about
sheaveline check                            whether the database answers and what is in it
sheaveline settings                         print what the environment says
sheaveline guide [NAME]                     the order the office does things in
sheaveline lift list|show|towers|spans      the register, one machine, its towers, its spans
sheaveline lift withdraw [--yes]            take a machine off the register
sheaveline lift shoot|drift                 record a tower survey; how fast towers lean
sheaveline lift load|export|check           read a drawing, write one back, check one
sheaveline rope list|file                   the ropes; one rope's file
sheaveline rope walk|walks|forecast         record a caliper walk; list walks; when to reorder
sheaveline day start|note|stop|restart|meter|show|open-stops   the day book
sheaveline crew card|renew|cards            issue, renew and list cards
sheaveline crew assign|gap                  put somebody on a post; can every post be filled
sheaveline evac drill|check|staging         record a drill; annual currency; stage the teams
sheaveline upkeep due|record|defects        the inspection ladder, sign-offs, open defects
sheaveline upkeep raise|board|close         work orders off defects
sheaveline board record|file|unfiled|show|return   incidents and tramway board filings
sheaveline report morning|season|packet|export     the printed pages and the CSV export
```

Every command answers `--help`, and `sheaveline guide` prints which ones to run
in what order. A day with a stop in it, as the office types it:

```
sheaveline day start JP --mechanic "R Skaggs" --operator "D Chin"
sheaveline day meter JP 40016.0 1408000 --by "R Skaggs"
sheaveline day stop JP 11:30 --kind safety_circuit --by "D Chin" --note "tower six stop opened"
sheaveline day restart JP 11:30 12:20 --by "R Skaggs" --line-checked
sheaveline upkeep due JP
sheaveline report season 2024-25 --opens 2024-12-01 --closes 2025-04-15
```

The exit code is the answer: 0 when it worked, 1 when something was refused or
something was found that somebody has to act on, and 2 for a command line that
was wrong or a database that will not open or will not answer.

## The http surface

The http surface is a Spring Boot application, started in code with the
database it serves and the keys it lets in, where `ring` is the `KeyRing` of
keys the department has issued:

```java
Configuration read = Configuration.fromEnvironment();
try (Database db = Database.open(read.database());
        ConfigurableApplicationContext app = SheavelineApplication.run(
                Settings.of(db).withResort(read.resort()).withKeys(ring),
                "--server.address=" + read.host(),
                "--server.port=" + read.port())) {
    // serving until the context is closed
}
```

The command line's `setup` builds the schema and the service never does. It
answers `/health` without touching the database and `/ready` by asking it a
question. The OpenAPI document is at `/openapi.json` and the docs page at
`/docs`.

A key goes in the `x-api-key` header. The roles are reader, operator, mechanic
and manager, and each covers everything below it. Every route with a role answers
401 for a missing, malformed, wrong, lapsed or too junior key, before the body is
read. Otherwise 400 is a code, serial or measure that will not parse, 404 is
something not on file, 409 is the domain refusing, and 422 is a request that
does not read as what the route wants. A paged list takes `limit` and `offset`
and answers with its `items` and the `total`.

| method | path | role | statuses |
| --- | --- | --- | --- |
| GET | `/health` | none | 200 |
| GET | `/ready` | none | 200, 503 |
| GET | `/lifts` | reader | 200 |
| GET | `/lifts/{code}`, `/towers`, `/terminals`, `/spans` | reader | 200, 400, 404 |
| DELETE | `/lifts/{code}` | manager | 204, 400, 404 |
| GET | `/ropes`, `/ropes/spares` | reader | 200 |
| GET | `/ropes/{serial}`, `/ropes/{serial}/file` | reader | 200, 400, 404 |
| GET | `/ropes/{serial}/surveys` (paged) | reader | 200, 400, 404, 422 |
| POST | `/ropes/{serial}/surveys` | mechanic | 201, 400, 404, 409, 422 |
| POST | `/lifts/{code}/incidents` | operator | 201, 400, 404, 409, 422 |
| GET | `/lifts/{code}/incidents` (paged) | reader | 200, 404, 422 |
| GET | `/incidents/outstanding?now=` | reader | 200, 422 |
| GET | `/incidents/{incident_id}` | reader | 200, 404, 422 |
| POST | `/incidents/{incident_id}/filing` | manager | 201, 404, 422 |
| POST | `/lifts/{code}/inspections` | mechanic | 201, 404, 409, 422 |
| GET | `/lifts/{code}/inspections?job=` (paged) | reader | 200, 404, 422 |
| GET | `/lifts/{code}/defects` | reader | 200, 404 |
| POST | `/lifts/{code}/work-orders` | mechanic | 201, 404, 409, 422 |
| GET | `/work-orders?lift=&priority=` (paged) | reader | 200, 404, 422 |
| GET | `/work-orders/overdue` | reader | 200 |
| GET | `/work-orders/{number}` | reader | 200, 404, 422 |
| POST | `/work-orders/{number}/closure` | manager | 200, 404, 409, 422 |
| GET | `/lifts/{code}/spacing?carriers=&speed=` | reader | 200, 400, 404, 409, 422 |
| GET | `/lifts/{code}/capacity?carriers=&speed=&occupancy=` | reader | 200, 400, 404, 409, 422 |
| GET | `/lifts/{code}/geometry?snow=&tension=` | reader | 200, 400, 404, 409 |
| POST | `/lifts/{code}/evacuation` | operator | 200, 400, 404, 409, 422 |
| POST | `/weather/stations` | manager | 201, 404, 422 |
| GET | `/weather/stations` | reader | 200 |
| POST | `/weather/observations` | operator | 201, 400, 404, 422 |
| GET | `/weather/observations?station=&since=` (paged) | reader | 200, 404, 422 |
| GET | `/lifts/{code}/hold?now=&stale_after_minutes=` | reader | 200, 404, 422 |
| GET | `/lifts/{code}/days/{day}`, `/days/{day}/stops` | reader | 200, 404, 422 |
| GET | `/lifts/{code}/days` (paged) | reader | 200, 404, 422 |
| POST | `/lifts/{code}/stops` | operator | 201, 404, 409, 422 |
| POST | `/lifts/{code}/stops/restart` | operator | 200, 404, 409, 422 |
| GET | `/stops/open?lift=` | reader | 200, 404 |
| GET | `/lifts/{code}/meters` (paged) | mechanic | 200, 404, 422 |

## What is in here

Every package sits under `io.github.rkaushikdev.sheaveline`, with the
configuration, the errors every package raises and the release number in the
root package itself.

| package | what it holds |
| --- | --- |
| `measures` | length, load, speed, duration, angle, torque, temperature, wind |
| `plant` | machines, terminals, towers, carriers, ropes, tower alignment |
| `line` | catenary sag, clearance, sheave deviation, the line walk |
| `capacity` | spacing, dwell, uphill and downhill capacity, the queue |
| `ropework` | wear, broken wires, magnetic test, splices, tension, dressing, retirement |
| `upkeep` | the inspection ladder, defects, work orders, part lives, the shelf |
| `ops` | the day book, pre op sheets, stops, meters, shifts |
| `weather` | observations, the hold policy, the forecast, the hold log |
| `evac` | the evacuation profile, teams, plans, staging, timing, drills |
| `people` | certificates and rosters |
| `board` | incidents, filings, the register |
| `schedule` | the season, the timetable, daylight, the calendar |
| `reports` | the eighty column pages and the csv export |
| `persistence` | the tables, the mappers, the repositories, the unit of work |
| `api` | the http surface |
| `cli` | the command line |
| `journal` | what the software says about itself, one line a record |
| `util` | decimal arithmetic, quoting, and the words an enumeration is written as |

```
src/main/java/io/github/rkaushikdev/sheaveline/   the packages above
src/main/resources/db/migration/                  the schema, one Flyway migration per revision
src/main/resources/application.properties         how Spring itself behaves
src/main/resources/logback.xml                    warnings to standard error, nothing to standard output
src/test/java/io/github/rkaushikdev/sheaveline/   the tests, beside the package each one tests
```

## Working on it

```
./mvnw -B spotless:apply                  # lay the sources out the way the check wants
./mvnw -B verify                          # the layout check, every test, the coverage gate
./mvnw -B test -Dtest=MigrationsTest      # one test class
```

`verify` fails on a source that is not laid out the way Spotless lays it out, on
a compiler warning, on any failing test, and when JaCoCo counts fewer than 80% of
the lines covered.

The migration test builds the schema both ways, once by running the Flyway
migrations and once from the entities alone, and compares them, so an entity
change without a migration fails before it reaches anybody. The packaging and
smoke tests hold the build to what it declares: every import against the
dependencies in `pom.xml`, the pinned versions, the main class, the migration
chain, and the packages, variables and commands this README names.
