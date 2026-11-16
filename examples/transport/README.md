# Validated transport schedule

These files were generated from the bundled `resources/busModel.ttl` and
`resources/busLogistics.ttl`, then solved with OPTIC-CLP and independently checked
with VAL. No durations or other modeling assumptions were changed for this check.

| Result | Value |
| --- | --- |
| Actions | 37 (5 pickups, 27 moves, 5 drop-offs) |
| Vehicles | B1 and B2 |
| Makespan | 38.021 model time units |
| B2 deliveries | P2 at 22.013 and P1 at 22.014, at C12 |
| B1 deliveries | P5 at 38.019, P4 at 38.020, P3 at 38.021, at C17 |
| Independent validation | Plan executed successfully; goal satisfied; Plan valid |

The plan is feasible; optimality was not proved. OPTIC found an initial schedule
with makespan 85.046 and improved it to 38.021 within the 15-second test budget.
The time unit is the one represented by the input travel times; these values should
not be interpreted as seconds without a domain convention.

- [Generated domain](domain.pddl)
- [Generated problem](problem.pddl)
- [Complete timed schedule](schedule.plan)
- [VAL verdict](validation.log)

Run the same end-to-end check after `mvn verify`:

```sh
python3 scripts/check_schedule.py \
  --planner /path/to/optic-clp \
  --validator /path/to/Validate \
  --timeout 15
```

Or validate this saved schedule directly:

```sh
/path/to/Validate -t 0.000001 -m \
  examples/transport/domain.pddl \
  examples/transport/problem.pddl \
  examples/transport/schedule.plan
```

Use a tolerance below OPTIC's 0.001 separation. VAL's default 0.01 groups otherwise
distinct happenings together and reports spurious mutex failures. As a negative
control, removing the last drop-off from this plan produced `Goal not satisfied`
and `Plan invalid` with the same validator and tolerance.

OPTIC also emits a non-fatal missing `:action-costs` requirement warning for the
`total-cost` accumulator. No optimization metric is declared in the input, and
OPTIC reports that it uses makespan by default. Pickup/drop-off retain zero duration
as modeled in the source ontology. Positive service times, deadlines, real-world
execution, and robustness to delays were not evaluated.

This matches the division of responsibilities in section 4 of the
[KMARF paper](https://arxiv.org/pdf/1701.03000): PDDLGen translates the model and a
third-party planner, OPTIC in the prototype, computes schedules.

Tool provenance for this run (2026-11-16):

- OPTIC-CLP Linux x86-64 binary from
  [esterel-plan-generator](https://github.com/ah96/esterel-plan-generator), commit
  `7eadd9dccc6031a2b2d5210398a93d03ce714ced`, `planners/optic-clp`.
- VAL built from [KCL-Planning/VAL](https://github.com/KCL-Planning/VAL), commit
  `3c7a1f330bdab0ba28a4762bb45c3f06c27fb6d4`, CMake Release, target `Validate`.
  With recent CMake, configuration required `-DCMAKE_POLICY_VERSION_MINIMUM=3.5`.
- Source domain SHA-256: `c0995351ca15960fb7c9506f2f6d51802a24686d36b3414ad8841d36b71bec5c`.
- Source problem SHA-256: `50e5d59185cc7f8bf58b913abe8dac9189c598ec5a16d53d96d144ef6fb54b61`.

Planner and validator binaries are external tools and are not included in this repository.
