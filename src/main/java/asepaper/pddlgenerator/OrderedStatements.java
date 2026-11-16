package asepaper.pddlgenerator;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;

import java.util.*;

/** Reads ordered arguments without relying on the model's statement iteration order. */
final class OrderedStatements {
    private OrderedStatements() { }

    static Vector<PDDLRaw> read(Model model, boolean reverse) {
        List<Statement> statements = model.listStatements().toList();
        statements.sort(Comparator.comparing((Statement s) -> s.getSubject().toString())
                .thenComparing(s -> s.getPredicate().getURI())
                .thenComparing(s -> s.getObject().toString()));
        Set<String> parameterOwners = new HashSet<>();
        Vector<PDDLRaw> result = new Vector<>();
        for (Statement statement : statements) {
            if (statement.getSubject().isAnon()) continue; // RDF collection cells
            List<RDFNode> values = Collections.singletonList(statement.getObject());
            if ("hasParameter".equals(statement.getPredicate().getLocalName())) {
                String key = statement.getSubject().getURI();
                if (!parameterOwners.add(key)) {
                    throw new IllegalArgumentException("Unordered hasParameter values on " + key
                            + "; use a Turtle list: hasParameter (first second)");
                }
                RDFNode value = statement.getObject();
                if (value.equals(RDF.nil) || (value.isResource()
                        && value.asResource().hasProperty(RDF.first))) {
                    values = readList(value.asResource());
                }
            }
            for (RDFNode value : values) {
                String subject = statement.getSubject().getURI();
                String object = value.isLiteral() ? value.asLiteral().getLexicalForm()
                        : value.isURIResource() ? value.asResource().getURI() : value.toString();
                result.add(new PDDLRaw(reverse ? object : subject,
                        statement.getPredicate().getURI(), reverse ? subject : object));
            }
        }
        return result;
    }

    private static List<RDFNode> readList(Resource head) {
        List<RDFNode> values = new ArrayList<>();
        Set<Resource> visited = new HashSet<>();
        Resource cell = head;
        while (!cell.equals(RDF.nil)) {
            if (!visited.add(cell) || cell.listProperties(RDF.first).toList().size() != 1
                    || cell.listProperties(RDF.rest).toList().size() != 1
                    || !cell.getProperty(RDF.rest).getObject().isResource()) {
                throw new IllegalArgumentException("Malformed or cyclic hasParameter list on " + head);
            }
            values.add(cell.getProperty(RDF.first).getObject());
            cell = cell.getPropertyResourceValue(RDF.rest);
        }
        return values;
    }
}
