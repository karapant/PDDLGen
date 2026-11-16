# PDDLGen

PDDLGen translates transport-domain and problem ontologies in Turtle format into
PDDL domain and problem files. An external planner such as OPTIC uses those files
to generate timed vehicle schedules.

## Requirements

- JDK 17 or newer and Maven for building and running the generator.
- Python 3.8 or newer for the optional schedule-check script.
- Local OPTIC and VAL executables for schedule generation and independent validation.
  See the [validated example](examples/transport/README.md) for tool sources and versions.

## Build and generate PDDL

Run these commands from the repository root:

```sh
mvn verify
java -jar target/asepaper-1.0-SNAPSHOT.jar resources/busModel.ttl resources/busLogistics.ttl
```

The CLI accepts a domain Turtle file followed by a problem Turtle file. It writes
`domain-file.pddl` and `problem-file.pddl` in the current working directory,
replacing existing files with those names.

`mvn verify` runs the regression suite, including PDDL4J syntax and type checks,
and builds the executable JAR. Parsing alone does not establish that a problem
has a solution; use the planning check below to test that.

## Generate and validate a schedule

After building the JAR, provide the paths to your OPTIC and VAL executables:

```sh
python3 scripts/check_schedule.py \
  --planner /path/to/optic-clp \
  --validator /path/to/Validate
```

The script generates PDDL from the bundled Turtle files, runs OPTIC with a
45-second search budget, extracts its latest schedule, and checks execution and
goal satisfaction with VAL. It requires a positive `Plan valid` verdict, including
when the validator exits with status zero. Results are written to `target/planning/`:

| File | Contents |
| --- | --- |
| `domain-file.pddl`, `problem-file.pddl` | Generated planner inputs |
| `schedule.plan` | Timed actions and durations |
| `generator.log`, `planner.log` | Generator and planner output |
| `validation.log` | Independent validation result |

Use `--domain` and `--problem` for other Turtle inputs, `--output` to choose the
results directory, and `--timeout` to change the planner's search budget. To check
existing PDDL files, add `--pddl`:

```sh
python3 scripts/check_schedule.py \
  --planner /path/to/optic-clp \
  --validator /path/to/Validate \
  --pddl --domain domain-file.pddl --problem problem-file.pddl
```

VAL tolerance defaults to `0.000001`, below OPTIC's `0.001` action separation.
VAL's default `0.01` can merge distinct happenings and report mutex violations
for this schedule. Use `--tolerance` to set it explicitly.

The [saved transport example](examples/transport/README.md) delivers all five
passengers using two buses, with **37 actions** and a **makespan of 38.021 model
time units**. VAL confirmed successful execution and goal satisfaction. Removing
the final drop-off made validation fail. Optimality was not established; the
planner may reach its time limit after finding a feasible schedule.

## HTTP API

Start the service by passing a port number:

```sh
java -jar target/asepaper-1.0-SNAPSHOT.jar 8080
```

POST JSON containing `domainModel` and `problemModel`. These values are Turtle
file paths accessible to the server, or model URLs; they are not inline Turtle:

```sh
curl -X POST http://localhost:8080/ \
  -H 'Content-Type: application/json' \
  --data '{"domainModel":"resources/busModel.ttl","problemModel":"resources/busLogistics.ttl"}'
```

Successful responses use `application/json` and contain `domainFile` and
`problemFile` as strings containing the generated PDDL. The HTTP API generates
PDDL; invoke an external planner to obtain a schedule.

## Ontology conventions and migration

Argument order must be explicit. Use an RDF collection (Turtle list):

```turtle
@prefix busModel: <http://ontologies.cf.asepaper.net/busModel#> .
@prefix : <http://example.org/problem#> .

busModel:connected busModel:hasParameter (busModel:start-coordinate busModel:end-coordinate) .
:connected_A_B busModel:hasParameter (:A :B) .
```

Lists preserve both order and repeated arguments. A single direct `hasParameter`
value is still accepted. Multiple direct values (`hasParameter :A, :B`) are rejected
because RDF does not define their order; migrate these to a list in the intended
argument order. The bundled Turtle examples have been migrated.

Action expressions reference the exact parameter resources used by their action.
Different parameter roles may share a type through `hasName` without sharing a
variable. Ordinary actions use `:precondition` and untimed expressions. Durative
actions require one duration and a timing (`at start`, `at end`, or `over all`)
for each expression. Absent conditions and effects become empty conjunctions.
Unresolved action references produce an error instead of incomplete PDDL.

Clients of the previous HTTP response must also account for corrected field
labels and types: `domainFile` contains the domain and `problemFile` contains the
problem, each as a JSON string rather than a one-element array.

## Ontology validation status

The [ontology audit](docs/ontology-validation.md) checks every resource with Jena,
OWLAPI, and HermiT. All eight files parse successfully. The core XML/OWL files pass
OWL 2 DL profile checks with the available local imports, and `System.owl` also
passes HermiT consistency checking. The Dublin Core file has profile violations
with its imports missing locally, and `busModel.ttl` contains an invalid typed
literal (`"true"^^xsd:double`). Full consistency of the complete ontology set has
not been established: some imports are not bundled, and HermiT stops on DUL's
`xsd:date` datatype. These checks are separate from the successful PDDL schedule
validation and are not part of the current Maven regression suite.

## Relationship to the paper

The project accompanies [A Framework for Knowledge Management and Automated
Reasoning Applied on Intelligent Transport Systems](https://arxiv.org/pdf/1701.03000).
Section 4 describes the same division of responsibilities: the Java component
generates PDDL, and a third-party planner, OPTIC in the prototype, computes schedules.

This version is a corrected, modernized implementation. Ordered Turtle arguments,
the corrected HTTP response, Java 17, and the validation tooling are changes from
the historical implementation. Some example argument orders were inferred from
predicate declarations and resource names; the paper does not specify those
individual facts. The saved schedule demonstrates feasibility for the current
example, rather than reproduction of a particular published schedule.

The original zero-duration pickup and drop-off actions are preserved. Positive
boarding times and service deadlines must be modeled explicitly if required.
OPTIC reports a non-fatal missing `:action-costs` requirement warning for the
example's `total-cost` accumulator. There is no explicit PDDL metric in this
example, so OPTIC uses makespan rather than optimizing that accumulator. Real-world
execution and robustness to delays have not been evaluated.
