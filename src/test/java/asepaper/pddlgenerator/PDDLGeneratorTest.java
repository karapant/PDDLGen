package asepaper.pddlgenerator;

import fr.uga.pddl4j.parser.Parser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class PDDLGeneratorTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final String PREFIX = """
            @prefix : <http://example.org/test#> .
            @prefix m: <http://example.org/Middle#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            """;
    private static final String DOMAIN = PREFIX + """
            :source rdfs:subClassOf m:Parameters ; :hasName "place" .
            :stop rdfs:subClassOf m:Parameters ; :hasName "place" .
            :route rdfs:subClassOf m:Predicate ; :hasParameter (:source :stop) .
            :ready rdfs:subClassOf m:Predicate .
            :go rdfs:subClassOf m:Action ; :hasParameter (:source :stop) ;
                :hasEffect :routeEffect, :readyEffect .
            :routeEffect rdfs:subClassOf m:Effect ; :hasConditionExpressionPart :routeExpression .
            :readyEffect rdfs:subClassOf m:Effect ; :hasConditionExpressionPart :readyExpression .
            :routeExpression rdfs:subClassOf m:ConditionalExpression ; :hasParameter :route .
            :readyExpression rdfs:subClassOf m:ConditionalExpression ; :hasParameter :ready .
            """;
    private static final String PROBLEM = PREFIX + """
            :a a :place . :z a :place .
            :fact a :route ; :hasParameter (:z :a) .
            :flag a :ready .
            :init a m:Action ; m:hasPredicateList :initial .
            :initial a m:PredicateList ; m:hasPredicate :fact, :flag .
            :goal a m:Action ; m:hasPredicateList :target ; m:hasActionOperator "and" .
            :target a m:PredicateList ; m:hasPredicate :flag .
            """;

    private File file(String contents, String suffix) throws IOException {
        File f = temp.newFile(UUID.randomUUID() + suffix);
        Files.writeString(f.toPath(), contents, StandardCharsets.UTF_8);
        return f;
    }

    private String[] generate(String domain, String problem) throws IOException {
        return new PDDLGenerator().GeneratePlan(file(domain, ".ttl").getPath(),
                file(problem, ".ttl").getPath(), false);
    }

    private void parses(String[] generated) throws Exception {
        Parser parser = new Parser();
        parser.parse(file(generated[0], ".pddl").getPath(), file(generated[1], ".pddl").getPath());
        assertTrue(parser.getErrorManager().getMessages().toString(), parser.getErrorManager().isEmpty());
    }

    @Test public void ordinaryActionsUseExactVariablesAndAllEffects() throws Exception {
        String[] output = generate(DOMAIN, PROBLEM);
        assertTrue(output[0].contains(":precondition (and)"));
        assertFalse(output[0].contains(":duration"));
        assertFalse(output[0].contains(":condition "));
        assertTrue(output[0].contains("(route ?p0 ?p1)"));
        assertTrue(output[0].contains(":effect (and (ready) (route ?p0 ?p1))"));
        assertTrue(output[1].contains("(route z a)"));
        assertTrue(output[1].contains("(ready)"));
        parses(output);
    }

    @Test public void repeatedArgumentsAndSameInitialDeclarationsRemainDistinct() throws Exception {
        String[] output = generate(DOMAIN, PROBLEM.replace("(:z :a)", "(:z :z)"));
        assertTrue(output[0].contains("(route ?p0 - place ?p1 - place)"));
        assertTrue(output[1].contains("(route z z)"));
        parses(output);
    }

    @Test public void conditionsAndEmptyEffectsAreSupported() throws Exception {
        String domain = DOMAIN.replace(":hasEffect :routeEffect, :readyEffect", ":hasCondition :guard")
                + ":guard rdfs:subClassOf m:Condition ; :hasConditionExpressionPart :readyExpression .";
        String[] output = generate(domain, PROBLEM);
        assertTrue(output[0].contains(":precondition (ready)"));
        assertTrue(output[0].contains(":effect (and)"));
        parses(output);
    }

    @Test public void durativeActionsUseTemporalSyntax() throws Exception {
        String domain = DOMAIN + """
                :go :isDurativeAction true ; :hasDuration :duration .
                :duration rdfs:subClassOf m:Duration ; :hasParameter 2.5 .
                :routeExpression :hasTiming "at end" .
                :readyExpression :hasTiming "at start" .
                """;
        String[] output = generate(domain, PROBLEM);
        assertTrue(output[0].contains(":durative-action go"));
        assertTrue(output[0].contains(":duration (= ?duration 2.5)"));
        assertTrue(output[0].contains("(at end (route ?p0 ?p1))"));
        parses(output);
    }

    @Test public void aliasedFunctionsUseOrderedVariablesInDurations() throws Exception {
        String domain = DOMAIN + """
                :travelTime rdfs:subClassOf m:Function ; :hasName "travel-time" ;
                    :hasParameter (:source :stop) .
                :go :isDurativeAction true ; :hasDuration :duration .
                :duration rdfs:subClassOf m:Duration ; :hasParameter :travelTime .
                :routeExpression :hasTiming "at end" .
                :readyExpression :hasTiming "at start" .
                """;
        String[] output = generate(domain, PROBLEM);
        assertTrue(output[0].contains("(travel-time ?p0 - place ?p1 - place)"));
        assertTrue(output[0].contains(":duration (= ?duration (travel-time ?p0 ?p1))"));
        parses(output);
    }

    @Test public void zeroArgumentFunctionsAndPlainNumericValuesParse() throws Exception {
        String domain = DOMAIN + """
                :cost rdfs:subClassOf m:Function .
                """;
        String problem = PROBLEM + """
                :costValue a :cost ; :hasOperator "=" ; :hasValue 0 .
                :initial m:hasPredicate :costValue .
                """;
        String[] output = generate(domain, problem);
        assertTrue(output[1].contains("(= (cost) 0)"));
        parses(output);
    }

    @Test public void cyclicParameterListsAreRejected() {
        String domain = DOMAIN.replace(":hasParameter (:source :stop) ;", ":hasParameter _:cycle ;")
                + """
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                _:cycle rdf:first :source ; rdf:rest _:cycle .
                """;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> generate(domain, PROBLEM));
        assertTrue(error.getMessage().contains("Malformed or cyclic"));
    }

    @Test public void missingDurationIsReported() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> generate(DOMAIN + ":go :isDurativeAction true .", PROBLEM));
        assertTrue(error.getMessage().contains("exactly one duration"));
    }

    @Test public void danglingConditionIsReported() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> generate(DOMAIN + ":go :hasCondition :missing .", PROBLEM));
        assertTrue(error.getMessage().contains("Unknown condition"));
    }

    @Test public void unboundVariablesAreReported() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> generate(DOMAIN.replace(":hasParameter (:source :stop) ;", ":hasParameter :source ;"), PROBLEM));
        assertTrue(error.getMessage().contains("Unbound action parameter"));
    }

    @Test public void ambiguousDomainAndProblemArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> generate(DOMAIN.replace("(:source :stop)", ":source, :stop"), PROBLEM));
        assertThrows(IllegalArgumentException.class,
                () -> generate(DOMAIN, PROBLEM.replace("(:z :a)", ":z, :a")));
    }

    @Test public void statementOrderDoesNotChangeOutput() throws Exception {
        String[] original = generate(DOMAIN, PROBLEM);
        assertArrayEquals(original, generate(shuffle(DOMAIN), shuffle(PROBLEM)));
    }

    private String shuffle(String input) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        try {
            model.read(new StringReader(input), null, "TURTLE");
            List<org.apache.jena.rdf.model.Statement> statements = model.listStatements().toList();
            Collections.shuffle(statements, new Random(42));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (org.apache.jena.rdf.model.Statement statement : statements) {
                Model single = ModelFactory.createDefaultModel();
                single.add(statement);
                RDFDataMgr.write(out, single, Lang.NTRIPLES);
                single.close();
            }
            return out.toString(StandardCharsets.UTF_8);
        } finally { model.close(); }
    }

    @Test public void bundledTransportExampleParses() throws Exception {
        String[] output = new PDDLGenerator().GeneratePlan("resources/busModel.ttl",
                "resources/busLogistics.ttl", false);
        assertTrue(output[0].contains("(azduration ?p2 ?p1)"));
        assertFalse(output[0].contains("XMLSchema"));
        parses(output);
    }

    @Test public void webResponseLabelsFilesAndUsesJsonStrings() throws Exception {
        Server server = new Server();
        LocalConnector connector = new LocalConnector(server);
        server.addConnector(connector);
        server.setHandler(new PDDLWebService());
        try {
            server.start();
            String body = new JSONObject().put("domainModel", file(DOMAIN, ".ttl").getPath())
                    .put("problemModel", file(PROBLEM, ".ttl").getPath()).toString();
            String response = connector.getResponses("POST / HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/json\r\nConnection: close\r\nContent-Length: "
                    + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
            assertTrue(response, response.startsWith("HTTP/1.1 200"));
            assertTrue(response, response.toLowerCase(Locale.ROOT).contains("content-type: application/json"));
            JSONObject result = new JSONObject(response.substring(response.indexOf("\r\n\r\n") + 4));
            assertTrue(result.get("domainFile") instanceof String);
            assertTrue(result.getString("domainFile").startsWith("(define (domain"));
            assertTrue(result.getString("problemFile").startsWith("(define (problem"));
            parses(new String[] {result.getString("domainFile"), result.getString("problemFile")});
        } finally { server.stop(); }
    }
}
