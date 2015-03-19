package edu.umw.cpsc.collegesim;
import sim.engine.*;
import sim.util.*;
import sim.field.network.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.ArrayList;


/** The top-level singleton simulation class, with main(). 
 * <p></p>
 * Purpose in life:
 * <ol>
 * <li>At start of simulation, create {@link #INIT_NUM_PEOPLE} people and
 * {@link #INIT_NUM_GROUPS} groups. Schedule them all to run, and
 * ourselves.</li>
 * <li>Each August, increment everyone's year, enroll the new freshman class,
 * create new groups, and schedule all these.</li>
 * <li>Each May, dump year-end statistics, graduate and/or dropout students
 * (based on their alienation). (And remove random groups?).</li>
 * </ol>
 */
public class Sim extends SimState implements Steppable{

    /** 
     * The random number seed for this simulation.
     */
    public static long SEED;

    /**
     * A graph where each node is a student and each edge is a friendship 
     * between those students. It is undirected. */
    public static Network peopleGraph = new Network(false);

    /**
     * A hashtag identifying the current run of the simulation.
     */
    public static long SIMTAG;

    /**
     * The number of people, of random year-in-college (fresh, soph, etc.)
     * that the simulation will begin with. */
    public static int INIT_NUM_PEOPLE;


    /**
     * The number of groups, with random initial membership, that the
     * simulation will begin with. */
    public static int INIT_NUM_GROUPS;


    /**
     * The number of newly enrolling freshmen each year.     */
    public static int NUM_FRESHMEN_ENROLLING_PER_YEAR;


    /**
     * The number of new groups to be added each year.
     */
    public static int NUM_NEW_GROUPS_PER_YEAR;
    
    /** The coefficient (see also {@link #DROPOUT_INTERCEPT}) of a linear
     * equation to transform alienation to probability of
     * dropping out. If x is based on the alienation level,
     * then y=mx+b, where m is the DROPOUT_RATE and b the
     * DROPOUT_INTERCEPT, gives the probability of dropping out. */
    public static double DROPOUT_RATE;
    
    /** See {@link #DROPOUT_RATE}. */
    public static double DROPOUT_INTERCEPT;

    public static final int NUM_MONTHS_IN_ACADEMIC_YEAR = 9;
    public static final int NUM_MONTHS_IN_SUMMER = 3;
    public static final int NUM_MONTHS_IN_YEAR = NUM_MONTHS_IN_ACADEMIC_YEAR +
        NUM_MONTHS_IN_SUMMER;
    
    /** The length of the simulation in years, settable via command-line. */
    public static int NUM_SIMULATION_YEARS;


    // The list of every group in the entire simulation. 
    private static ArrayList<Group> allGroups = new ArrayList<Group>();
    
    // The list of every student in the entire simulation. (Maintained in
    // addition to the Network for convenience.)
    private static ArrayList<Person> peopleList = new ArrayList<Person>();
    
    // Singleton pattern.
    private static Sim theInstance;


    private static File outF;
    private static BufferedWriter outWriter;
    private static File FoutF;
    private static BufferedWriter FoutWriter;
    private static File PrefoutF;
    private static BufferedWriter PrefoutWriter;
    private static File groupF;
    private static PrintWriter groupWriter;
    static PrintWriter encounterWriter;
    static PrintWriter similarityWriter;
    
    // Here is the schedule!
    // Persons run at clock time 0.5, 1.5, 2.5, ..., 8.5, ..summer.., 12.5...
    // Groups run at clock time 1, 2, 3, ..., 9 ..summer.. 13...
    // The Sim object itself runs at 0.1, 9.1, 12.1, 21.1, 33.1, ... in other
    // words, every August and May, just before all Persons and Groups run for
    // the first time that academic year and after they all run for the last
    // time that academic year.
    boolean nextMonthInAcademicYear() {
        double curTime = Sim.instance().schedule.getTime();
        int curTimeInt = (int) Math.ceil(curTime);
        // if curTimeInt is 1, that means we are at the first month.
        int monthsWithinYear = curTimeInt % NUM_MONTHS_IN_YEAR;
        return (monthsWithinYear < NUM_MONTHS_IN_ACADEMIC_YEAR);
    }

    /**
     * Return the total number of students currently in the simulation.
     */
    public static int getNumPeople( ){
        return peopleList.size();
    }

    /**
     * Return the total number of groups currently in the simulation.
     */
    public static int getNumGroups(){
        return allGroups.size();
    }

    /* creating the instance */
    public static synchronized Sim instance(long seed){
        if (theInstance == null){
            theInstance = new Sim(seed);
        }
        return theInstance;
    }

    /* getting the instance */
    static synchronized Sim instance()
    {
        if (theInstance == null) throw new AssertionError();

        return theInstance;
    }


    /** Return the list of all students in the simulation. */
    public static ArrayList<Person> getPeople(){
        return peopleList;
    }
    
    public Sim(long seed){
        super(seed);
        this.SEED = seed;

        try {
            encounterWriter = new PrintWriter(
                new FileWriter("encounters"+Sim.SIMTAG+".csv"));
            encounterWriter.println("year,id1,id2,type");
            encounterWriter.flush();

            similarityWriter = new PrintWriter(
                new FileWriter("similarity"+Sim.SIMTAG+".csv"));
            similarityWriter.println("year,races,similarity,becameFriends");
            similarityWriter.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }
    
    public void start( ){
        super.start( );

        // NOTE: the simulation starts at time -1. (Yes, NEGATIVE one.) This
        // is why we do things like schedule the first students at time 1.5
        // from now, and groups at time 2.0 from now: so they run at times 0.5
        // and 1.0, respectively.

        for(int i=0; i<INIT_NUM_PEOPLE; i++){
            //Create a person of random year, add and schedule them.
            Person person = new Person();
            person.setYear(random.nextInt(4)+1);
            peopleList.add(person);
            peopleGraph.addNode(person);
            schedule.scheduleOnceIn(1.5, person);
        }

        // Initialize with some "plain ol' groups."
        for(int x = 0; x<INIT_NUM_GROUPS; x++){
            //Create a new group, add and schedule it.
            Group group = new Group();
            allGroups.add(group);
            schedule.scheduleOnceIn(2.0, group);
        }

        // Initialize with forced-mixed-race orientation groups (if any).
        for(int x = 0; x<Group.INITIAL_NUM_MIXED_RACE_GROUPS; x++){
            Group group = new Group(Group.MIXED_RACE_GROUP_FRACTION);
            allGroups.add(group);
            schedule.scheduleOnceIn(2.0, group);
        }

        for(int i = 0; i<peopleList.size(); i++){
            for (int j=0; j<Person.INITIAL_NUM_FORCED_OPPOSITE_RACE_FRIENDS;
                                                                        j++){
                peopleList.get(i).forceAddRandomOppRaceFriend();
            }
        }

        //Schedule ourselves to run at start of first academic year.
        schedule.scheduleOnceIn(1.1, this);

    }
    
    /**
     * Run the simulation from the command line. See printUsageAndQuit() for
     * usage details.
     */
    public static void main(String[] args) throws IOException {

        // Mandatory command-line values.
        NUM_SIMULATION_YEARS = -1;
        SIMTAG = -1;

        // Optional values with defaults.
        Person.RACE_WEIGHT = 5;
        Person.PROBABILITY_WHITE = .8;
        INIT_NUM_PEOPLE = 4000;
        NUM_FRESHMEN_ENROLLING_PER_YEAR = 1000;
        INIT_NUM_GROUPS = 200;
        NUM_NEW_GROUPS_PER_YEAR = 10;
        Person.NUM_TO_MEET_POP = 5;
        Person.NUM_TO_MEET_GROUP = 10;
        Person.DECAY_THRESHOLD = 2;
        Person.FRIENDSHIP_COEFFICIENT = .22;
        Person.FRIENDSHIP_INTERCEPT = .05;
        Person.NUM_PREFERENCES = Person.PREFERENCE_POOL_SIZE = 20;
        Person.NUM_HOBBIES = Person.NUM_HOBBIES = 20;
        Person.INITIAL_NUM_FORCED_OPPOSITE_RACE_FRIENDS = 0;
        Group.INITIAL_NUM_MIXED_RACE_GROUPS = 0;
        Group.MIXED_RACE_GROUP_FRACTION = .5;
        Group.RECRUITMENT_REQUIRED = .6;
        Group.LIKELIHOOD_OF_RANDOMLY_LEAVING_GROUP = .1;
        SEED = System.currentTimeMillis();

        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-maxTime")) {
                NUM_SIMULATION_YEARS = Integer.valueOf(args[++i]);
            } else if (args[i].equals("-simtag")) {
                SIMTAG = Long.valueOf(args[++i]);
            } else if (args[i].equals("-raceWeight")) {
                Person.RACE_WEIGHT = Integer.valueOf(args[++i]);
            } else if (args[i].equals("-probWhite")) {
                Person.PROBABILITY_WHITE = Double.valueOf(args[++i]);
            } else if (args[i].equals("-seed")) {
                SEED = Long.parseLong(args[++i]);
            } else if (args[i].equals("-initNumPeople")) {
                INIT_NUM_PEOPLE = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-numFreshmenPerYear")) {
                NUM_FRESHMEN_ENROLLING_PER_YEAR = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-initNumGroups")) {
                INIT_NUM_GROUPS = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-numNewGroupsPerYear")) {
                NUM_NEW_GROUPS_PER_YEAR = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-driftRate")) {
                Group.LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE =
                    Double.parseDouble(args[++i]);
            } else if (args[i].equals("-dropoutRate")) {
                DROPOUT_RATE = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-dropoutIntercept")) {
                DROPOUT_INTERCEPT = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-numToMeetPop")) {
                Person.NUM_TO_MEET_POP = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-numToMeetGroup")) {
                Person.NUM_TO_MEET_GROUP = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-decayThreshold")) {
                Person.DECAY_THRESHOLD = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-friendshipCoefficient")) {
                Person.FRIENDSHIP_COEFFICIENT = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-friendshipIntercept")) {
                Person.FRIENDSHIP_INTERCEPT = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-numPreferences")) {
                Person.NUM_PREFERENCES = Person.PREFERENCE_POOL_SIZE =
                    Integer.parseInt(args[++i]);
            } else if (args[i].equals("-numHobbies")) {
                Person.NUM_HOBBIES = Person.HOBBY_POOL_SIZE =
                    Integer.parseInt(args[++i]);
            } else if (args[i].equals("-initNumForcedOppRaceFriends")) {
                Person.INITIAL_NUM_FORCED_OPPOSITE_RACE_FRIENDS = 
                    Integer.parseInt(args[++i]);
            } else if (args[i].equals("-initNumMixedRaceGroups")) {
                Group.INITIAL_NUM_MIXED_RACE_GROUPS = 
                    Integer.parseInt(args[++i]);
            } else if (args[i].equals("-mixedRaceGroupFraction")) {
                Group.MIXED_RACE_GROUP_FRACTION = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-recruitmentRequired")) {
                Group.RECRUITMENT_REQUIRED = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-likelihoodOfLeavingGroup")) {
                Group.LIKELIHOOD_OF_RANDOMLY_LEAVING_GROUP = 
                    Double.parseDouble(args[++i]);
            }
        }

        if (NUM_SIMULATION_YEARS == -1  ||
            SIMTAG == -1) {
            printUsageAndQuit();
        }

        // Write the parameters file to a SIMTAG-annotated filename in the 
        // current directory.
        try {
            PrintWriter paramsFile = new PrintWriter(new BufferedWriter(
                new FileWriter("./sim_params" + SIMTAG + ".txt")));
            paramsFile.println("seed="+SEED);
            paramsFile.println("maxTime="+NUM_SIMULATION_YEARS);
            paramsFile.println("simtag="+SIMTAG);
            paramsFile.println("raceWeight="+Person.RACE_WEIGHT);
            paramsFile.println("initNumPeople="+INIT_NUM_PEOPLE);
            paramsFile.println("numFreshmenPerYear="+
                NUM_FRESHMEN_ENROLLING_PER_YEAR);
            paramsFile.println("initNumGroups="+INIT_NUM_GROUPS);
            paramsFile.println("numNewGroupsPerYear="+
                NUM_NEW_GROUPS_PER_YEAR);
            paramsFile.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

        // Add the "-seed SEED" arguments to args so that when doLoop() runs,
        // it has the same seed we just randomly set from above.
        String newargs[] = new String[args.length + 2];
        for (int i=0; i<args.length; i++) {
            newargs[i] = args[i];
        }
        newargs[newargs.length-2] = "-seed";
        newargs[newargs.length-1] = "" + SEED;
        args = newargs;

        doLoop(new MakesSimState() { 
            public SimState newInstance(long seed, String[] args) {
                return instance(seed);
            }
            public Class simulationClass() {
                return Sim.class;
            }
        }, args);
    }

    private boolean isEndOfSim() {
        return (schedule.getTime()/NUM_MONTHS_IN_YEAR) > NUM_SIMULATION_YEARS;
    }

    boolean isLastYearOfSim() {
        return (schedule.getTime()/NUM_MONTHS_IN_YEAR) >= 
            NUM_SIMULATION_YEARS - 1;
    }

    int getCurrYearNum() {
        return (int) schedule.getTime()/NUM_MONTHS_IN_YEAR;
    }

    private void dumpToFiles() {

        if(outWriter!=null){
            try{
                outWriter.close();
            }catch(IOException e){
                System.out.println("Could not close file");
            }
        }
        if(FoutWriter!=null){
            try{
                FoutWriter.close();
            }catch(IOException e){
                System.out.println("Could not close file");
            }
        }
        if(groupWriter!=null){
            groupWriter.close();
        }

        if(!isEndOfSim()){
            String f="people"+SIMTAG+".csv";
            try{
                outF = new File(f);
                outF.createNewFile( );
                outWriter = new BufferedWriter(new FileWriter(outF,true));
            }catch(IOException e){
                System.out.println("Couldn't create file");
                e.printStackTrace();
                System.exit(1);
            }

            if (Sim.instance().getCurrYearNum() == 0) {
                Person.printHeaderToFile(outWriter);
            }
            for(int x = 0; x<peopleList.size(); x++){
                peopleList.get(x).printToFile(outWriter);
            }
            
            //FILE OF FRIENDSHIPS
            String ff="friendships"+SIMTAG+".csv";
            try{
                // append to current file, if exists
                FoutF = new File(ff);
                FoutWriter = new BufferedWriter(new FileWriter(FoutF, true));
                if (Sim.instance().getCurrYearNum() == 0) {
                    printHeaderToFriendshipsFile(FoutWriter);
                }
                for(int x = 0; x<peopleList.size(); x++){
                    peopleList.get(x).printFriendsToFile(FoutWriter);
                }
                FoutWriter.flush();
            }catch(IOException e){
                System.out.println("Couldn't create file");
                e.printStackTrace();
                System.exit(1);
            }
            
            //FILE OF GROUPS
            String gf="groups"+SIMTAG+".csv";
            try{
                // append to current file, if exists
                groupF = new File(gf);
                groupWriter = new PrintWriter(new FileWriter(groupF, true));
                if (Sim.instance().getCurrYearNum() == 0) {
                    Group.printHeaderToGroupsFile(groupWriter);
                }
                for(int x = 0; x<allGroups.size(); x++){
                    allGroups.get(x).printToFile(groupWriter);
                }
                groupWriter.flush();
            }catch(IOException e){
                System.out.println("Couldn't create file");
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    static void printHeaderToFriendshipsFile(BufferedWriter writer) {
        try {
            writer.write("period,id,friendId\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void dumpToDropoutFile(Person p) {
        String f="dropout"+SIMTAG+".csv";
        BufferedWriter outWriter = null;
        try{
            File outputFile = new File(f);
            if (!outputFile.exists()) {
                outputFile.createNewFile( );
                outWriter = new BufferedWriter(new FileWriter(outputFile));
                Person.printHeaderToFile(outWriter);
            } else {
                outWriter = new BufferedWriter(new FileWriter(outputFile,true));
            }
        }catch(IOException e){
            System.out.println("Couldn't create file");
            e.printStackTrace();
            System.exit(1);
        }
        p.printToFile(outWriter);
    }

    public void dumpPreferencesOfGraduatedStudent(Person x){
        if(PrefoutWriter!=null){
            try{
                PrefoutWriter.close();
            }catch(IOException e){
                System.out.println("Could not close file");
            }
        }

        if(PrefoutWriter!=null){
            try{
                PrefoutWriter.close();
            }catch(IOException e){
                System.out.println("Could not close file");
            }
        }
    }

    public void dumpPreferencesOfDropoutStudent(Person x){
        if(PrefoutWriter!=null){
            try{
                PrefoutWriter.close();
            }catch(IOException e){
                System.out.println("Could not close file");
            }
        }
        try{
        String string = "dropout" + SIMTAG + ".csv";
                PrefoutF = new File(string);
                if(!PrefoutF.exists()){
                    PrefoutF.createNewFile();
                    PrefoutWriter = 
                        new BufferedWriter(new FileWriter(PrefoutF));
                    PrefoutWriter.write(
                        "period,ID,numFriends,race,alienation,year");
                }
            }catch(IOException e){
                System.out.println("Couldn't create file");
                e.printStackTrace();
                System.exit(1);
            }
            x.printPreferencesToFile(PrefoutWriter);
    }


    public void step(SimState state){

        System.out.println("#### SIM (" + schedule.getTime() + ")");
        if(!isEndOfSim()) {

            if(nextMonthInAcademicYear()){
                /*
                 * August.
                 * Year-start activities. Increment everyone's year, enroll
                 * the new freshman class, create new groups.
                 */
                System.out.println("---------------");
                System.out.println("Starting year: "+getCurrYearNum());
                for(int x = 0; x<peopleList.size(); x++){
                    peopleList.get(x).incrementYear();
                }
                for(int x = 0; x<NUM_FRESHMEN_ENROLLING_PER_YEAR; x++){
                    //Create and add a new freshman
                    Person person = new Person();
                    person.setYear(1);
                    peopleList.add(person);
                    peopleGraph.addNode(person);
                    //Schedule the person.
                    //Why 1.4 from now? Because (1) we the Sim are running at 
                    //int.1, and (2) students each run at int.5.
                    schedule.scheduleOnceIn(1.4, person);
                }
                for(int x = 0; x<NUM_NEW_GROUPS_PER_YEAR; x++){
                    //Create a new group with the list of people
                    Group group = new Group();
                    //Add the group
                    allGroups.add(group);
                    //Schedule the group.
                    //Why 1.9 from now? Because (1) we the Sim are running at 
                    //int.1, and (2) groups each run at integer times.
                    schedule.scheduleOnceIn(1.9,group);
                }
                /*
                 * The new academic year is now ready to begin! Schedule
                 * myself to wake up in May.
                 */
                schedule.scheduleOnceIn(NUM_MONTHS_IN_ACADEMIC_YEAR, this);

            }else{

                /*
                 * May.
                 * Year-end activities. Dump output files, graduate and
                 * dropout students, remove some groups.
                 */
                System.out.println("End of year: "+getCurrYearNum());
                ArrayList<Person> toRemove = new ArrayList<Person>();
                // ArrayList<Group> toRemoveGroups = new ArrayList<Group>();

                dumpToFiles();
                if(!isEndOfSim()) {
                    //For all of the people
                    for(int x = 0; x<peopleList.size(); x++){
                        Person student = peopleList.get(x);
                        //If they have more than four years, they graduate
                        if(student.getYear( ) >= 4){
                            dumpPreferencesOfGraduatedStudent(student);
                            toRemove.add(student);
                        //Otherwise
                        }else{
                            double alienationLevel = student.getAlienation( );
                            double alienation = DROPOUT_RATE * alienationLevel 
                                + DROPOUT_INTERCEPT; 
                            double dropChance = random.nextDouble( );
                            if(dropChance <= alienation){
                                dumpToDropoutFile(student);
                                toRemove.add(student);
                            }
                        }
                    }
/*
 * Nuke groups randomly...do we want to do this?
                    for(int x = 0; x<allGroups.size(); x++){
                        if(random.nextDouble(true, true)>.75){
                            toRemoveGroups.add(allGroups.get(x));
                        }
                    }
                    for(int x = 0; x<toRemoveGroups.size(); x++){
                        toRemoveGroups.get(x).removeEveryoneFromGroup();
                        allGroups.remove(toRemoveGroups.get(x));
                    }
*/
                    for(int x = 0; x<toRemove.size(); x++){
                        //Let the person leave their groups
                        toRemove.get(x).leaveUniversity();
                        peopleList.remove(toRemove.get(x));
                        peopleGraph.removeNode(toRemove.get(x));
                    }
                    // toRemoveGroups.clear();
                    toRemove.clear();
                }
                /*
                 * The academic year is now complete -- have a great summer!
                 * Schedule myself to wake up in August, unless this is truly
                 * the end.
                 */
                if (!isLastYearOfSim()) {
                    schedule.scheduleOnceIn(NUM_MONTHS_IN_SUMMER, this);
                } else {
                    schedule.seal();
                }
            }
        }

    }

    /** (public simply to get it in the JavaDoc.) */
    public static void printUsageAndQuit() {
        System.err.println(
        "Usage: Sim -maxTime numGenerations     # Integer\n" +
        "  -simtag simulationTag                # Long\n" + 
        "  [-raceWeight numAttrsRaceIsWorth]    # Integer; default 5\n" +
        "  [-probWhite fracNewStudentsWhoAreW]  # Double; default .8\n" +
        "  [-trialNum trialNumber]              # Integer; default 1\n" +
        "  [-initNumPeople initNumPeople]       # Integer; default 4000\n" +
        "  [-numFreshmenPerYear num]            # Integer; default 1000\n" +
        "  [-initNumGroups initNumGroups]       # Integer; default 200\n" +
        "  [-numNewGroupsPerYear num]           # Integer; default 10\n" +
        "  [-driftRate probChangeAttribute]     # Double; default .1\n" +
        "  [-dropoutRate rate]                  # Double; default .01\n" +
        "  [-dropoutIntercept intercept]        # Double; default .05\n" +
        "  [-numToMeetPop num]                  # Integer; default 5\n" +
        "  [-numToMeetGroup num]                # Integer; default 10\n" +
        "  [-decayThreshold numMonthsStayAlive] # Integer; default 2\n" +
        "  [-friendshipCoefficient coeff]       # Double; default .22\n" +
        "  [-friendshipIntercept intercept]     # Double; default .05\n" +
        "  [-numHobbies num]                    # Integer; default 20\n" +
        "  [-numPreferences num]                # Integer; default 20\n" +
        "  [-initNumForcedOppRaceFriends num]   # Integer; default 0\n" +
        "  [-initNumMixedRaceGroups num]        # Integer; default 0\n" +
        "  [-mixedRaceGroupFraction fracMin]    # Double; default .5\n" +
        "  [-recruitmentRequired frac]          # Double; default .6\n" +
        "  [-likelihoodOfLeavingGroup frac]     # Double; default .1\n" +
        "  [-seed seed].                        # Long; default rand");
        System.exit(1);
    }
}
