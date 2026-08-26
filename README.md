# sheaveline

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

## Running it

```
pip install -e .[dev]
sheaveline setup                 # build the tables
sheaveline guide                 # the order the office does things in
sheaveline --help
```

The database, the resort name, the journal level and the key file are read from
the environment. `sheaveline settings` prints what it found.

| variable | what it is | default |
| --- | --- | --- |
| `SHEAVELINE_DATABASE_URL` | where the records live | a file beside the program |
| `SHEAVELINE_RESORT` | the name on the printed pages | Ogden Peak |
| `SHEAVELINE_JOURNAL` | what the software says about itself | info |
| `SHEAVELINE_KEY_FILE` | the api keys | not set, and the http surface refuses everything |
| `SHEAVELINE_HOST` / `SHEAVELINE_PORT` | where the http surface listens | 127.0.0.1:8451 |

The http surface is `uvicorn sheaveline.api.app:build_app --factory`. It answers
`/health` without touching the database and `/ready` by asking it a question.

## What is in here

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

## Working on it

```
ruff check . && ruff format --check .
mypy
pytest
alembic upgrade head
```

The migration test builds the schema both ways and compares them, so a model
change without a migration fails before it reaches anybody.
