package asepaper.pddlgenerator;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;

import java.io.*;
import java.util.Iterator;
import java.util.Vector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by eathkar on 03/05/16.
 */
public class PDDLGenerator {

    public String[] GeneratePlan(String domainFile, String problemFile, boolean writeToFile){
        FileManager.get().addLocatorClassLoader(PDDLGenerator.class.getClassLoader());
        Model domain = FileManager.get().loadModel(domainFile, null, "TURTLE");
        Model problem = null;
        try {
            problem = FileManager.get().loadModel(problemFile, null, "TURTLE");
            PDDLModel domainModel = processDomainFile(domain);
            String problemContent = processProblemFile(problem, domainModel);
            if (writeToFile) {
                Files.write(Paths.get(PDDLConstants.domainFileName),
                        domainModel.dataToReturn.getBytes(StandardCharsets.UTF_8));
                Files.write(Paths.get(PDDLConstants.problemFileName),
                        problemContent.getBytes(StandardCharsets.UTF_8));
            }
            return new String[] {domainModel.dataToReturn, problemContent};
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write PDDL output", ex);
        } finally {
            domain.close();
            if (problem != null) problem.close();
        }
    }

    private boolean containsType(String []typeArray, String typeToSearch){
        boolean containsType = false;
        //System.out.println("TYPE ARRAY:"+typeArray.length);
        for (int i = 0; i < typeArray.length; i++){
            //System.out.println("accessing typeArray["+i+"]:"+typeArray[i]+" with length: "+typeArray.length);
            if (typeArray[i].compareTo(typeToSearch) == 0){
                return true;
            }
        }

        return containsType;
    }

    private String processProblemFile(Model input, PDDLModel model) {
        String data = "";
        PDDLConstants constants = new PDDLConstants("", "");
        Vector<PDDLRaw> rawData = new Vector<PDDLRaw>();

        System.out.println("Processing problem file ...");

        // Note: Since predicates come at random, we will first save an unprocessed (raw) list of triplets, and
        // subsequently process them

        rawData = OrderedStatements.read(input, true);

        // Lets first generate some constant names - header of our file
        String problemData = "(define (problem " +
                constants.getDomain() +
                "-01)\n\t(:domain " +
                constants.getDomain() +
                ")";


        // Now lets do the processing
        // First Pass: Scan and store primal primitives

        Iterator<PDDLRaw> iterator = rawData.iterator();
        Vector<PDDLObjectCollection> objectStore = new Vector<PDDLObjectCollection>();
        Vector<PDDLAction> actions = new Vector<PDDLAction>();
        Vector<PDDLPredicateList> predicate_lists = new Vector<PDDLPredicateList>();
        Vector<PDDLPredicate> predicates = new Vector<PDDLPredicate>();

        while (iterator.hasNext()) {
            PDDLRaw currentTriplet = iterator.next();
            //System.out.println(currentTriplet.object + " " + currentTriplet.predicate + " " + currentTriplet.subject);
            if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.TYPE))
                    && containsType(model.types, constants.removePrefixes(currentTriplet.subject))){
                String objectName = constants.removePrefixes(currentTriplet.object);
                String objectType = constants.removePrefixes(currentTriplet.subject);
                addPDDLObject(objectStore, new PDDLObject(objectName, objectType));
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.TYPE)) &&
                    currentTriplet.subject.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.ACTION))){
                /*System.out.println("action exists: "+constants.removePrefixes(currentTriplet.object)+ " "+
                        actionExists(actions, constants.removePrefixes(currentTriplet.object)));*/
                if(!actionExists(actions, constants.removePrefixes(currentTriplet.object))) {
                    actions.add(new PDDLAction(constants.removePrefixes(currentTriplet.object)));
                    //System.out.println("Add action at type "+constants.removePrefixes(currentTriplet.object));
                }
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PREDICATE_LIST))){
                String predicateList = constants.removePrefixes(currentTriplet.subject);
                String action = constants.removePrefixes(currentTriplet.object);
                Iterator<PDDLAction> actionIterator = actions.iterator();
                boolean action_added = false;

                while (actionIterator.hasNext()){
                    PDDLAction currentAction = actionIterator.next();
                    if (currentAction.name.compareTo(constants.removePrefixes(action)) == 0){
                        // We need the name of the predicate list in order to match it to a PDDLPredicateList later on
                        currentAction.predicateListName = predicateList;
                        action_added = true;
                        break;
                    }
                }
                // In case we read a predicatelist prior to an action, then we add the action and the predicate list
                if(!action_added){
                    actions.add(new PDDLAction(constants.removePrefixes(action)));
                    actions.lastElement().predicateListName = predicateList;
                    //System.out.println("Add action at hasPredicateList "+constants.removePrefixes(action));
                }
            }
            else if(currentTriplet.predicate.endsWith(
                    constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PREDICATE))){

              //  if (currentTriplet.subject.contains("C09_C10")) {
                //    System.out.println("Subjet " + currentTriplet.subject + " object " + currentTriplet.object + " predicate " + currentTriplet.predicate);
               // }
                int index = predicateListIndex(predicate_lists, constants.removePrefixes(currentTriplet.object));
                if (index < 0){
                    predicate_lists.add(new PDDLPredicateList(constants.removePrefixes(currentTriplet.object)));
                    predicate_lists.lastElement().list.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.subject)));
                } else {
                    predicate_lists.elementAt(index).list.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.subject)));
                }
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.TYPE)) &&
                    currentTriplet.subject.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.PREDICATE_LIST))){
                if (predicateListIndex(predicate_lists, constants.removePrefixes(currentTriplet.object)) < 0){
                    predicate_lists.add(new PDDLPredicateList(constants.removePrefixes(currentTriplet.object)));
                }
            }
            else if(currentTriplet.predicate.endsWith(
                constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PARAMETER))){
                int index = predicateIndex(predicates, constants.removePrefixes(currentTriplet.object));
                if (index < 0){
                    //System.out.println("adding "+constants.removePrefixes(currentTriplet.object));
                    predicates.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.object)));
                    predicates.lastElement().parameters.add(constants.removePrefixes(currentTriplet.subject));
                } else {
                    predicates.elementAt(index).parameters.add(constants.removePrefixes(currentTriplet.subject));
                }
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.TYPE)) &&
                    currentTriplet.subject.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.PREDICATE))){
                if (predicateIndex(predicates, constants.removePrefixes(currentTriplet.object)) < 0){
                    predicates.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.object)));
                }
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.TYPE))
                    && model.functionNames.containsKey(constants.removePrefixes(currentTriplet.subject))){
                if (predicateIndex(predicates, constants.removePrefixes(currentTriplet.object)) < 0){
                    predicates.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.object)));
                    predicates.lastElement().type = model.functionNames.get(constants.removePrefixes(currentTriplet.subject));
                }
                else {
                    predicates.elementAt(predicateIndex(predicates,
                            constants.removePrefixes(currentTriplet.object))).type =
                            model.functionNames.get(constants.removePrefixes(currentTriplet.subject));
                }
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.TYPE))
                    && model.predicateNames.containsKey(constants.removePrefixes(currentTriplet.subject))){
                if (predicateIndex(predicates, constants.removePrefixes(currentTriplet.object)) < 0){
                    predicates.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.object)));
                    predicates.lastElement().type = model.predicateNames.get(constants.removePrefixes(currentTriplet.subject));
                    predicates.lastElement().isPred = true;
                }
                else {
                    predicates.elementAt(predicateIndex(predicates,
                            constants.removePrefixes(currentTriplet.object))).type =
                            model.predicateNames.get(constants.removePrefixes(currentTriplet.subject));
                    predicates.elementAt(predicateIndex(predicates,
                            constants.removePrefixes(currentTriplet.object))).isPred = true;
                }
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_OPERATOR))){
                if(predicateIndex(predicates, constants.removePrefixes(currentTriplet.object)) < 0){
                    predicates.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.object)));
                    predicates.lastElement().hasOperator = currentTriplet.subject;
                }
                else
                    predicates.elementAt(predicateIndex(predicates, constants.removePrefixes(currentTriplet.object))).
                            hasOperator = currentTriplet.subject;
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_VALUE))){
                if(predicateIndex(predicates, constants.removePrefixes(currentTriplet.object)) < 0){
                    predicates.add(new PDDLPredicate(constants.removePrefixes(currentTriplet.object)));
                    if (currentTriplet.subject.indexOf("^") > 0)
                        predicates.lastElement().hasValue = currentTriplet.subject.substring(0,
                            currentTriplet.subject.indexOf("^"));
                    else
                        predicates.lastElement().hasValue = currentTriplet.subject;
                }
                else {
                    if (currentTriplet.subject.indexOf("^") > 0)
                        predicates.elementAt(predicateIndex(predicates, constants.
                                removePrefixes(currentTriplet.object))).hasValue = currentTriplet.subject.substring(0,
                                currentTriplet.subject.indexOf("^"));
                    else
                        predicates.elementAt(predicateIndex(predicates, constants.
                                removePrefixes(currentTriplet.object))).hasValue = currentTriplet.subject;
                }
            }
            else if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_ACTION_OPERATOR))){
                Iterator<PDDLAction> actionIterator = actions.iterator();
                String action = constants.removePrefixes(currentTriplet.object);
                String operator = currentTriplet.subject;

                boolean action_added = false;

                while (actionIterator.hasNext()){
                    PDDLAction currentAction = actionIterator.next();
                    if (currentAction.name.compareTo(action) == 0){
                        currentAction.assignOperator(operator);
                        action_added = true;
                        break;
                    }
                }

                if(!action_added){
                    actions.add(new PDDLAction(action));
                    actions.lastElement().assignOperator(operator);
                    //System.out.println("Add action at hasActionOperator "+action);
                }
            }

        }

        // Verify predicates were stored at this point
        /*for (int la = 0; la < predicates.size(); la++){
            System.out.println("PREDICATE: "+predicates.elementAt(la).name);
        }*/

        // some "stitching" required after parsing:
        // (a) stitch predicates to pred. lists and
        // (b) pred. lists to actions

        for (int i = 0; i < predicate_lists.size(); i++){
            PDDLPredicateList currentList = predicate_lists.elementAt(i);
            for (int k = 0; k < currentList.list.size(); k++){
                PDDLPredicate currentPredicate = currentList.list.elementAt(k);
                for (int j = 0; j < predicates.size(); j++){
                    //System.out.println("Current predicate: "+currentPredicate.name + " and predicate list "+predicates.elementAt(j).name);
                    if (currentPredicate.name.compareTo(predicates.elementAt(j).name) == 0){
                        currentList.list.set(k, predicates.elementAt(j));
                    }
                }
            }
        }

        for (int i = 0; i < actions.size(); i++){
            PDDLAction currentAction = actions.elementAt(i);
            for (int k = 0; k < predicate_lists.size(); k++){
                if (currentAction.predicateListName.compareTo(predicate_lists.elementAt(k).name) == 0){
                    currentAction.predicateList = predicate_lists.elementAt(k);
                }
            }
        }

        // object generation
        String objectData = "\n\t(:objects";
        for (int i = 0; i < objectStore.size(); i++){
            PDDLObjectCollection currentCollection = objectStore.elementAt(i);
            for (int k = 0; k < currentCollection.getObjects().size(); k++){
                if (k == 0){
                    objectData += "\n\t\t";
                }
                PDDLObject currentObject = currentCollection.getObjects().elementAt(k);
                if (k < (currentCollection.getObjects().size() - 1))
                    objectData += " " + currentObject.name;
                else
                    objectData += " " + currentObject.name + " - " + currentObject.type;
            }

        }
        objectData += "\n\t)";

        // state generation

        actions.sort(java.util.Comparator.comparingInt(a ->
                a.name.equals("init") ? 0 : a.name.equals("goal") ? 1 : 2));
        String stateData = "";
        for (int i = 0; i < actions.size(); i++){
            PDDLAction currentAction = actions.elementAt(i);
            stateData += "\n\t(:"+currentAction.name;
            if (currentAction.hasOperator()){
                stateData += " ("+currentAction.getOperator();
            }
            for (int k = 0; k < currentAction.predicateList.list.size(); k++){
                PDDLPredicate currentPredicate = currentAction.predicateList.list.elementAt(k);
                // System.out.println("CURRENT PREDICATE: "+currentPredicate.name);
                stateData += "\n\t\t";
                if (!currentPredicate.hasOperator.isEmpty())
                    stateData += "("+currentPredicate.hasOperator+" ";
                stateData += "(";
                stateData += currentPredicate.type;
                if (currentPredicate.parameters.size() > 0){
                    stateData += " ";
                }

                stateData += String.join(" ", currentPredicate.parameters);
                stateData += ")";
                if(!currentPredicate.hasValue.isEmpty()){
                    stateData += " " + currentPredicate.hasValue;
                }
                if(!currentPredicate.hasOperator.isEmpty()){
                    stateData += ")";
                }

            }
            if (currentAction.hasOperator()){
                stateData += "\n\t\t)";
            }
            stateData += "\n\t)";
        }

        stateData += "\n)";

        data = problemData + objectData + stateData;

        return data;
    }

    private int predicateIndex (Vector<PDDLPredicate> predicateStore, String predicate){
        int index = 0;
        Iterator<PDDLPredicate> predicateIterator = predicateStore.iterator();
        while(predicateIterator.hasNext()){
            PDDLPredicate pred = predicateIterator.next();
            if (pred.name.compareTo(predicate) == 0){
                return index;
            }
            index++;
        }
        return -1;
    }
    private int predicateListIndex (Vector<PDDLPredicateList> predicateListStore, String predicateList){
        int index = 0;
        Iterator<PDDLPredicateList> predicateListIterator = predicateListStore.iterator();
        while(predicateListIterator.hasNext()){
            PDDLPredicateList list = predicateListIterator.next();
            if (list.name.compareTo(predicateList) == 0){
                return index;
            }
            index++;
        }
        return -1;
    }

    private boolean actionExists (Vector <PDDLAction> actionStore, String actionName){

        Iterator<PDDLAction> actionIterator = actionStore.iterator();
        while (actionIterator.hasNext()){
            String an = actionIterator.next().name;

            if(an.compareTo(actionName) == 0){
                return true;
            }
        }

        return false;
    }

    private void addPDDLObject(Vector<PDDLObjectCollection> dataStore, PDDLObject objectToAdd){
        if (dataStore.size() == 0){
            dataStore.add(new PDDLObjectCollection(objectToAdd.type));
            dataStore.lastElement().insertIntoCollection(objectToAdd);
        } else{
            int listIndex = 0;
            boolean hasList = false;
            Iterator<PDDLObjectCollection> iter = dataStore.iterator();
            while (iter.hasNext()) {
                PDDLObjectCollection currentCollection = iter.next();
                if (currentCollection.datatype.compareTo(objectToAdd.type) == 0) {
                    hasList = true;
                    break;
                }
                listIndex++;
            }

            if (hasList){
                dataStore.elementAt(listIndex).insertIntoCollection(objectToAdd);
            }
            else {
                dataStore.add(new PDDLObjectCollection(objectToAdd.type));
                dataStore.lastElement().insertIntoCollection(objectToAdd);
            }
        }
    }

    private PDDLModel processDomainFile(Model input){

        PDDLConstants constants = new PDDLConstants("", "");
        Vector<PDDLRaw> rawData = new Vector<PDDLRaw>();

        System.out.println("Processing domain file ...");

        // Note: Since predicates come at random, we will first save an unprocessed (raw) list of triplets, and
        // subsequently process them

            rawData = OrderedStatements.read(input, false);

            // The data structure which will store the processed data

            PDDLAssets assets = new PDDLAssets();

            // Now lets do the processing
            // First Pass: Scan and store primal primitives

            Iterator<PDDLRaw> iterator = rawData.iterator();
            while (iterator.hasNext()){


                PDDLRaw currentTriplet = iterator.next();

                if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.ACTION))){
                    assets.actions.add(new PDDLAction(currentTriplet.subject));
                }
                else if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.PREDICATE))){
                    assets.predicates.add(new PDDLPredicate(currentTriplet.subject));
                }
                else if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.FUNCTION))){
                    assets.functions.add(new PDDLFunction(currentTriplet.subject));
                }
                else if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.CONDITION))){
                    assets.conditions.add(new PDDLCondition(currentTriplet.subject));
                }
                else if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.EFFECT))){
                    assets.effects.add(new PDDLEffect(currentTriplet.subject));
                }
                else if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.CONDITIONAL_EXPRESSION))){
                    assets.conditionalExpressions.add(new PDDLConditionalExpression(currentTriplet.subject));
                }
                else if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.DURATION))){
                    assets.durations.add(new PDDLDuration(currentTriplet.subject));
                }
                else if (currentTriplet.predicate.contains(constants.getPredicateDefinition(PDDLConstants.predicateType.SUBCLASS_OF))
                        && currentTriplet.object.endsWith(constants.getObjectDefinition(PDDLConstants.objectType.PARAMETER))){
                    assets.parameters.add(new PDDLParameter(currentTriplet.subject));
                }

            }

            // Second Pass: For second-level primitives, populate with content
            iterator = rawData.iterator();
            while (iterator.hasNext()){
                PDDLRaw currentTriplet = iterator.next();
                SubjectType stype = getSubjectType(currentTriplet.subject, assets);

                // Note, there may be some triplets with unrecognized types, thats ok, we can ignore them
                if (stype != null){
                   //System.out.println("Got: "+stype.subjectType + " "+stype.vectorPosition);
                   switch (stype.subjectType){
                       case FUNCTION:
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PARAMETER))){
                               assets.functions.elementAt(stype.vectorPosition).parameters.add(currentTriplet.object);
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_OPERATOR))){
                               assets.functions.elementAt(stype.vectorPosition).operator = currentTriplet.object;
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_VALUE))){
                               assets.functions.elementAt(stype.vectorPosition).value = currentTriplet.object;
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_NAME))){
                               assets.functions.elementAt(stype.vectorPosition).realname = currentTriplet.object;
                           }
                           break;
                       case PREDICATE:
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PARAMETER))){
                               assets.predicates.elementAt(stype.vectorPosition).parameters.add(currentTriplet.object);
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_NAME))){
                               assets.predicates.elementAt(stype.vectorPosition).realname = currentTriplet.object;
                           }
                           break;
                       case DURATION:
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PARAMETER))){
                               assets.durations.elementAt(stype.vectorPosition).parameters.add(currentTriplet.object);
                           }
                           break;
                       case CONDITIONAL_EXPRESSION:
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PARAMETER))){
                               assets.conditionalExpressions.elementAt(stype.vectorPosition).parameters.add(currentTriplet.object);
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_TIMING))){
                               assets.conditionalExpressions.elementAt(stype.vectorPosition).timing = currentTriplet.object;
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_NEGATION))){

                               if (currentTriplet.object.contains("true")) {
                                   assets.conditionalExpressions.elementAt(stype.vectorPosition).hasNegation = true;
                               }
                               else assets.conditionalExpressions.elementAt(stype.vectorPosition).hasNegation = false;
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_OPERATOR))){
                               assets.conditionalExpressions.elementAt(stype.vectorPosition).operator = currentTriplet.object;
                           }
                           break;
                       case CONDITION: // Requires meta-linking
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_CONDITION_EXPRESSION_PART))){
                               assets.conditions.elementAt(stype.vectorPosition).conditional_expressions.add(new PDDLConditionalExpression(currentTriplet.object));
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_CONDITION_OPERATOR))){
                               assets.conditions.elementAt(stype.vectorPosition).conditionalOperator = currentTriplet.object;
                           }
                           break;
                       case EFFECT:
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_CONDITION_EXPRESSION_PART))){
                               assets.effects.elementAt(stype.vectorPosition).conditional_expressions.add(new PDDLConditionalExpression(currentTriplet.object));
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_EFFECT_OPERATOR))){
                               assets.effects.elementAt(stype.vectorPosition).conditionalOperator = currentTriplet.object;
                           }
                           break;
                       case ACTION:
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_EFFECT))){
                               assets.actions.elementAt(stype.vectorPosition).effect.add(new PDDLEffect(currentTriplet.object));
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_CONDITION))){
                               assets.actions.elementAt(stype.vectorPosition).condition.add(new PDDLCondition(currentTriplet.object));
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.IS_DURATIVE_ACTION))){
                              if (currentTriplet.object.contains("true")) assets.actions.elementAt(stype.vectorPosition).isDurativeAction = true;
                              else assets.actions.elementAt(stype.vectorPosition).isDurativeAction = false;
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_DURATION))){
                               assets.actions.elementAt(stype.vectorPosition).duration.add(new PDDLDuration(currentTriplet.object));
                           }
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_PARAMETER))){
                               assets.actions.elementAt(stype.vectorPosition).parameters.add(new PDDLParameter(currentTriplet.object));
                           }
                           break;
                       case PARAMETER:
                           if (currentTriplet.predicate.endsWith(constants.getPredicateDefinition(PDDLConstants.predicateType.HAS_NAME))){
                               assets.parameters.elementAt(stype.vectorPosition).realName = currentTriplet.object;
                           }
                           break;
                   }

                }
            }

            return new DomainRenderer(assets).render();
    }

    private SubjectType getSubjectType(
            String subject,
            PDDLAssets assets
            ){

        Iterator<PDDLDuration> durationIterator = assets.durations.iterator();
        Iterator<PDDLPredicate> predicateIterator = assets.predicates.iterator();
        Iterator<PDDLConditionalExpression> conditionalExpressionIterator = assets.conditionalExpressions.iterator();
        Iterator<PDDLEffect> effectIterator = assets.effects.iterator();
        Iterator<PDDLCondition> conditionIterator = assets.conditions.iterator();
        Iterator<PDDLAction> actionIterator = assets.actions.iterator();
        Iterator<PDDLFunction> functionIterator = assets.functions.iterator();
        Iterator<PDDLParameter> parameterIterator = assets.parameters.iterator();

        int counter = 0;
        while(durationIterator.hasNext()){
            if (subject.compareTo(durationIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.DURATION, counter);
            }
            counter++;
        }
        counter = 0;
        while(actionIterator.hasNext()){
            if (subject.compareTo(actionIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.ACTION, counter);
            }
            counter++;
        }
        counter = 0;
        while(conditionIterator.hasNext()){
            if (subject.compareTo(conditionIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.CONDITION, counter);
            }
            counter++;
        }
        counter = 0;
        while(effectIterator.hasNext()){
            if (subject.compareTo(effectIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.EFFECT, counter);
            }
            counter++;
        }
        counter = 0;
        while(functionIterator.hasNext()){
            if (subject.compareTo(functionIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.FUNCTION, counter);
            }
            counter++;
        }
        counter = 0;
        while(conditionalExpressionIterator.hasNext()){
            if (subject.compareTo(conditionalExpressionIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.CONDITIONAL_EXPRESSION, counter);
            }
            counter++;
        }
        counter = 0;
        while(predicateIterator.hasNext()){
            if (subject.compareTo(predicateIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.PREDICATE, counter);
            }
            counter++;
        }
        counter = 0;
        while(parameterIterator.hasNext()){
            if (subject.compareTo(parameterIterator.next().name) == 0){
                return new SubjectType(PDDLConstants.objectType.PARAMETER, counter);
            }
            counter++;
        }
        //Its ok to suppress the below warning, some things in the model are never used by this generator
        //System.out.println("Warning: could not determine subject type for "+subject);

        return null;
    }

}

// Utility data structure
class SubjectType{
    public PDDLConstants.objectType subjectType;
    public int vectorPosition;

    public SubjectType(PDDLConstants.objectType type, int vectorPos){
        subjectType = type;
        vectorPosition = vectorPos;
    }
}