# Ontology validation audit

Checked on 2026-11-16 with Apache Jena 3.0.0, OWLAPI 5.1.20, and HermiT 1.4.5.519.
This audit distinguishes RDF parsing, OWL 2 DL profile checks, and logical consistency.
Passing one check does not establish the others.

## Results

| Resource | RDF parsing | OWL 2 DL profile with available local imports |
| --- | --- | --- |
| `resources/System.owl` | Pass, 83 triples | Pass |
| `resources/Middle.owl` | Pass, 250 triples | Pass |
| `resources/ITS.owl` | Pass, 140 triples | Pass |
| `resources/ext/ssn.owl` | Pass, 764 triples | Pass |
| `resources/ext/DUL.xml` | Pass, 1,912 triples | Pass |
| `resources/ext/dublincore.xml` | Pass, 149 triples | Fail: 50 undeclared annotation-property violation occurrences |
| `resources/busModel.ttl` | Pass, 440 triples | Pass; separate literal validation found an error below |
| `resources/busLogistics.ttl` | Pass, 1,484 triples | Pass |

Each XML/OWL file was parsed as RDF/XML, including expansion of its internal entity
declarations. The two `.ttl` files were parsed as Turtle. Local ontology IRIs were
mapped explicitly to their bundled files for OWLAPI loading. External imports that
are not bundled were excluded; the results do not certify a complete import closure.

HermiT found `System.owl` consistent with no unsatisfiable named classes. A check
combining the local XML documents, and another combining all eight local documents,
both stopped on the bundled DUL ontology's `xsd:date` datatype. HermiT reports that
it cannot handle that datatype without a custom definition. This is a reasoner
limitation, not proof that those ontologies are inconsistent. Full consistency of
the remaining ontology set has not been established.

## Findings

1. **Invalid typed literal.** `resources/busModel.ttl:75` contains
   `"true"^^xsd:double`. The lexical form `true` is not a double. The property's
   declared range is boolean, so `"true"^^xsd:boolean` is the apparent intended value.
   OWLAPI's profile check does not flag this annotation literal; Jena's explicit
   datatype lexical validation does. RDF parsing alone also accepts it.

2. **Incomplete local imports.** `ITS.owl` imports `http://xmlns.com/foaf/0.1/`.
   `ext/dublincore.xml` imports
   `http://protege.stanford.edu/plugins/owl/dc/dcmitype.owl` and
   `http://protege.stanford.edu/plugins/owl/dc/terms.owl`. These three imported
   ontologies are not bundled. This audit did not test whether their remote URLs
   are currently reachable or whether remote versions are compatible.

3. **Dublin Core declarations.** With its two imports excluded, `dublincore.xml`
   has 50 OWLAPI profile violation occurrences for undeclared annotation properties,
   including `dcterms:hasVersion` and `dcterms:issued`. In the union of all local
   files, declarations from other files reduce this to 30 occurrences. Resolving
   the intended imports may address these findings; the current offline bundle
   is not sufficient to establish that.

4. **DUL datatype support.** `ext/DUL.xml` declares `xsd:date` and uses it as a
   datatype-property range. A full consistency check needs a reasoner that supports
   these axioms, or an explicitly reviewed datatype mapping. Silently changing
   dates to another datatype or disabling datatype checks would weaken the audit.

5. **DUL namespace mismatch.** The bundled SSN ontology references
   `http://www.loa-cnr.it/ontologies/DUL.owl#`, while the bundled DUL ontology declares
   `http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#`. These are different RDF
   IRIs; no equivalence between them was established by this audit. This is an
   interoperability concern rather than an RDF parsing failure.

No resource files were modified during this audit. The generated PDDL and saved
OPTIC/VAL schedule remain separately validated: PDDLGen processes the two Turtle
inputs directly and does not perform OWL import resolution or logical reasoning.

## Next corrections

Correct the boolean literal, make the required import closure available through a
local ontology catalog, review the DUL namespace alignment, and rerun profile and
consistency checks with compatible datatype support. Ontology axioms and third-party
vocabulary definitions should be reviewed before changing their meaning.
