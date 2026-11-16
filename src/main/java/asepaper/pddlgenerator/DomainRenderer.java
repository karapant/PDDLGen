package asepaper.pddlgenerator;

import java.util.*;

/** Renders complete PDDL expressions, with variables indexed by their RDF resource URI. */
final class DomainRenderer {
    private final PDDLAssets assets;
    private final PDDLConstants constants = new PDDLConstants("", "");
    private final Map<String, String> types = new LinkedHashMap<>();
    private final Map<String, PDDLPredicate> predicates = new LinkedHashMap<>();
    private final Map<String, PDDLFunction> functions = new LinkedHashMap<>();
    private final Map<String, PDDLConditionalExpression> expressions = new LinkedHashMap<>();

    DomainRenderer(PDDLAssets assets) {
        this.assets = assets;
        for (PDDLParameter p : assets.parameters) types.put(p.name, symbol(p.realName, p.name));
        for (PDDLPredicate p : assets.predicates) predicates.put(p.name, p);
        for (PDDLFunction f : assets.functions) functions.put(f.name, f);
        for (PDDLConditionalExpression e : assets.conditionalExpressions) expressions.put(e.name, e);
    }

    PDDLModel render() {
        PDDLModel model = new PDDLModel();
        model.types = new LinkedHashSet<>(types.values()).toArray(new String[0]);
        StringBuilder out = new StringBuilder("(define (domain " + constants.getDomain() + ")\n"
                + "  (:requirements :strips :typing :negative-preconditions :disjunctive-preconditions"
                + " :fluents :durative-actions)\n"
                + "  (:types " + String.join(" ", model.types) + ")\n  (:predicates\n");
        Map<String, List<String>> signatures = new HashMap<>();
        for (PDDLPredicate p : assets.predicates) {
            String name = symbol(p.realname, p.name);
            model.predicateNames.put(local(p.name), name);
            declaration(out, name, p.parameters, signatures);
        }
        out.append("  )\n");
        if (!assets.functions.isEmpty()) {
            out.append("  (:functions\n");
            signatures.clear();
            for (PDDLFunction f : assets.functions) {
                String name = symbol(f.realname, f.name);
                model.functionNames.put(local(f.name), name);
                declaration(out, name, f.parameters, signatures);
            }
            out.append("  )\n");
        }
        for (PDDLAction action : assets.actions) action(out, action);
        model.dataToReturn = out.append(")\n").toString();
        return model;
    }

    private void declaration(StringBuilder out, String name, List<String> params,
                             Map<String, List<String>> signatures) {
        List<String> signature = new ArrayList<>();
        for (String param : params) signature.add(type(param));
        List<String> previous = signatures.putIfAbsent(name, signature);
        if (previous != null) {
            if (!previous.equals(signature)) throw invalid("Incompatible declarations for " + name);
            return;
        }
        out.append("    (").append(name);
        for (int i = 0; i < params.size(); i++) {
            out.append(" ?p").append(i).append(" - ").append(signature.get(i));
        }
        out.append(")\n");
    }

    private void action(StringBuilder out, PDDLAction action) {
        Map<String, String> variables = new LinkedHashMap<>();
        out.append("  (:").append(action.isDurativeAction ? "durative-action " : "action ")
                .append(local(action.name)).append("\n    :parameters (");
        for (int i = 0; i < action.parameters.size(); i++) {
            String param = action.parameters.get(i).name;
            if (variables.put(param, "?p" + i) != null) throw invalid("Repeated action parameter " + param);
            if (i > 0) out.append(" ");
            out.append("?p").append(i).append(" - ").append(type(param));
        }
        out.append(")\n");
        if (action.isDurativeAction) {
            if (action.duration.size() != 1) throw invalid("Durative action " + local(action.name)
                    + " must have exactly one duration");
            String name = action.duration.get(0).name;
            PDDLDuration duration = assets.durations.stream().filter(d -> d.name.equals(name))
                    .findFirst().orElseThrow(() -> invalid("Unknown duration " + name));
            if (duration.parameters.size() != 1) throw invalid("Duration " + name + " needs one value");
            String value = duration.parameters.get(0);
            out.append("    :duration (= ?duration ")
                    .append(functions.containsKey(value) ? functionCall(functions.get(value), variables) : number(value))
                    .append(")\n");
        } else if (!action.duration.isEmpty()) {
            throw invalid("Ordinary action " + local(action.name) + " cannot have a duration");
        }
        List<String> conditions = new ArrayList<>();
        for (PDDLCondition link : action.condition) {
            PDDLCondition condition = assets.conditions.stream().filter(c -> c.name.equals(link.name))
                    .findFirst().orElseThrow(() -> invalid("Unknown condition " + link.name));
            conditions.add(group(condition.conditionalOperator, condition.conditional_expressions,
                    variables, action.isDurativeAction));
        }
        out.append(action.isDurativeAction ? "    :condition " : "    :precondition ")
                .append(combine("and", conditions)).append("\n");
        List<String> effects = new ArrayList<>();
        for (PDDLEffect link : action.effect) {
            PDDLEffect effect = assets.effects.stream().filter(e -> e.name.equals(link.name))
                    .findFirst().orElseThrow(() -> invalid("Unknown effect " + link.name));
            if (!effect.conditionalOperator.isEmpty() && !effect.conditionalOperator.equalsIgnoreCase("and"))
                throw invalid("Unsupported effect operator " + effect.conditionalOperator);
            effects.add(group(effect.conditionalOperator, effect.conditional_expressions,
                    variables, action.isDurativeAction));
        }
        out.append("    :effect ").append(combine("and", effects)).append("\n  )\n");
    }

    private String group(String op, List<PDDLConditionalExpression> links,
                         Map<String, String> variables, boolean durative) {
        List<String> parts = new ArrayList<>();
        for (PDDLConditionalExpression link : links) {
            PDDLConditionalExpression expression = expressions.get(link.name);
            if (expression == null) throw invalid("Unknown expression " + link.name);
            parts.add(expression(expression, variables, durative));
        }
        return combine(op.isEmpty() ? "and" : op.toLowerCase(Locale.ROOT), parts);
    }

    private String expression(PDDLConditionalExpression e, Map<String, String> variables, boolean durative) {
        if (e.parameters.isEmpty()) throw invalid("Expression " + e.name + " has no arguments");
        List<String> parts = new ArrayList<>();
        String operator = e.operator;
        for (String argument : e.parameters) {
            PDDLPredicate predicate = predicates.get(argument);
            PDDLFunction function = functions.get(argument);
            if (predicate != null) {
                parts.add(call(symbol(predicate.realname, predicate.name), predicate.parameters, variables));
            } else if (function != null) {
                parts.add(functionCall(function, variables));
                if (!function.operator.isEmpty()) {
                    if (!operator.isEmpty() && !operator.equals(function.operator))
                        throw invalid("Conflicting operators in " + e.name);
                    operator = function.operator;
                }
                if (!function.value.isEmpty()) parts.add(number(function.value));
            } else throw invalid("Unknown expression argument " + argument);
        }
        String result;
        if (operator.isEmpty()) {
            if (parts.size() != 1) throw invalid("Expression " + e.name + " needs an operator");
            result = parts.get(0);
        } else {
            if (!Arrays.asList("=", "<", ">", "<=", ">=", "increase", "decrease", "assign",
                    "scale-up", "scale-down").contains(operator) || parts.size() != 2)
                throw invalid("Invalid operator or arity in " + e.name);
            result = "(" + operator + " " + String.join(" ", parts) + ")";
        }
        if (e.hasNegation) result = "(not " + result + ")";
        if (durative) {
            if (!Arrays.asList("at start", "at end", "over all").contains(e.timing))
                throw invalid("Missing or invalid timing in " + e.name);
            result = "(" + e.timing + " " + result + ")";
        } else if (!e.timing.isEmpty()) throw invalid("Temporal expression in ordinary action: " + e.name);
        return result;
    }

    private String functionCall(PDDLFunction f, Map<String, String> variables) {
        return call(symbol(f.realname, f.name), f.parameters, variables);
    }

    private String call(String name, List<String> parameters, Map<String, String> variables) {
        StringBuilder out = new StringBuilder("(" + name);
        for (String parameter : parameters) {
            String variable = variables.get(parameter);
            if (variable == null) throw invalid("Unbound action parameter " + parameter + " in " + name);
            out.append(" ").append(variable);
        }
        return out.append(")").toString();
    }

    private String type(String resource) {
        String type = types.get(resource);
        if (type == null) throw invalid("Unknown parameter type " + resource);
        return type;
    }

    private String symbol(String alias, String resource) {
        String name = alias.isEmpty() ? local(resource) : alias;
        if (!name.matches("[a-zA-Z][a-zA-Z0-9_-]*")) throw invalid("Invalid PDDL symbol " + name);
        return name;
    }

    private String local(String resource) { return constants.removePrefixes(resource); }

    private static String number(String value) {
        if (!value.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw invalid("Invalid numeric value " + value);
        return value;
    }

    private static String combine(String operator, List<String> expressions) {
        if (!operator.equals("and") && !operator.equals("or")) throw invalid("Unsupported logical operator " + operator);
        if (expressions.size() == 1) return expressions.get(0);
        return "(" + operator + (expressions.isEmpty() ? "" : " " + String.join(" ", expressions)) + ")";
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
