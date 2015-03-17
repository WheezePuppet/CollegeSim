package edu.umw.cpsc.collegesim;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import ec.util.*;
import sim.engine.*;
import sim.util.Bag;


/**
 * A group of students in the CollegeSim model, perhaps representing a
 * campus club, orientation group, dorm room floor, or group of friends.
 * <p></p>
 * Purpose in life:
 * <ul>
 * <li>Upon creation, populates with random students.</li>
 * <li>Every month, influences each of its members, using {@link
 * #LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE}, among other things. Each
 * student can have any of his/her attributes drift some amount towards the
 * mean of that attribute for other students in that group, or not.</li>
 * <li>Every month, randomly selects students to <b>recruit</b> to the group.
 * Students who are already members are simply ignored. Those who are not will
 * join with a probability related to their <b>affinity</b> to the group.</li>
 * <li>Every month, allow students to leave the group. This depends on {@link
 * #LIKELIHOOD_OF_RANDOMLY_LEAVING_GROUP}, but will also not allow the group
 * size to drift below the {@link #MINIMUM_GROUP_SIZE}. <i>(but Morgan doesn't
 * like this)</i></li>
 * </ul>
 */
public class Group implements Steppable{

    /**
     * The lower bound on the size of a group at any point during its
     * lifespan. */
    public static final int MINIMUM_GROUP_SIZE = 1;

    /**
     * The lower bound on the size of a newly created group. (See {@link
     * #MINIMUM_GROUP_SIZE}.) */
    public static final int MINIMUM_START_GROUP_SIZE = 3;
  
    /**
     * The upper bound on the size of a newly created group. */
    public static final int MAXIMUM_START_GROUP_SIZE = 8; 
  
    /**
     * A threshold that determines the likelihood of whether a student will 
     * join a group. Higher numbers means less acceptance into groups. The
     * number itself is difficult to interpret, since a complex conflagration
     * of factors will be put together to compare to it. */
    public static final double RECRUITMENT_REQUIRED = .6;
  
    /**
     * Each time step, the probability that each student will leave each of
     * their groups, provided that leaving said group would not push the
     * group size below the minimum. */
    public static final double LIKELIHOOD_OF_RANDOMLY_LEAVING_GROUP = .1;
  
    /** Each time step, the probability that a student will change one of
     * their attribute values <i>provided</i> that said attribute is "different
     * enough" from their influencing peers to warrant change. */
    public static double LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE = .1;
  
    /**
     * Each time step, the number of students who will be "invited" to a
     * group. (<i>i.e.</i>, have {@link #recruitStudent(Person)} called on
     * them.)
     */
    public static final int NUM_PEOPLE_TO_RECRUIT = 10;

    /**
     * The number of forced-mixed-race orientation groups to start with.
     */
    public static int INITIAL_NUM_MIXED_RACE_GROUPS;

    /**
     * For the forced-mixed-race orientation groups, what fraction should be
     * composed of minorities?
     */
    public static double MIXED_RACE_GROUP_FRACTION;

    // Hand out consecutive unique numbers to new groups.
    private static int nextGroupId = 0;
    private int id;

    // A number in the range 0 to 1 indicating how aggressive the group is
    // in attracting members.
    private double recruitmentFactor;
  
    private ArrayList<Person> students;
    
    private void init() {
      this.id = nextGroupId++;
      students = new ArrayList<Person>();
      recruitmentFactor = Sim.instance().random.nextDouble();
    }

    /**
     * Constructs a new Group object and pre-populate it with members. 
     */
    public Group() {
        init();
        selectStartingStudents();
    }

    /**
     * Constructs a new Group object and pre-populate it with members of
     * (approximately) the fraction of minorities passed.
     */
    public Group(double minorityFraction) {
        init();
        selectStartingStudents(minorityFraction);
    }

    private int generateInitialGroupSize() {
        int initialGroupSize = Sim.instance().random.nextInt(
            MAXIMUM_START_GROUP_SIZE-MINIMUM_START_GROUP_SIZE) + 
            MINIMUM_START_GROUP_SIZE + 1;
        if(initialGroupSize>MINIMUM_GROUP_SIZE){
          initialGroupSize=MINIMUM_GROUP_SIZE;    //keeps groups at least 
          // the min
        }
        if(initialGroupSize>Sim.getNumPeople()){
          initialGroupSize=Sim.getNumPeople();    //to ensure the initial 
          // group size is never greater than the number of total people
        }
        return initialGroupSize;
    }

    // Note: make sure this stays in sync with the other
    // selectStartingStudents(). (I couldn't think of a good way to refactor
    // common functionality.)
    private void selectStartingStudents() {
        int initialGroupSize = generateInitialGroupSize();
        ArrayList<Person> people = Sim.getPeople();
        Person randStudent;
        for(int x = 0; x < initialGroupSize; x++){
          randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          while(groupContainsStudent(randStudent)){
            randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          }
          students.add(randStudent);
          randStudent.joinGroup(this);
        }
    }

    // Note: make sure this stays in sync with the other
    // selectStartingStudents(). (I couldn't think of a good way to refactor
    // common functionality.)
    private void selectStartingStudents(double minorityFraction) {
        double initialGroupSize = (double) generateInitialGroupSize();
        int numWhites = (int) Math.round(initialGroupSize * minorityFraction);
        int numMinorities = (int) initialGroupSize - numWhites;

        ArrayList<Person> people = Sim.getPeople();
        Person randStudent;

        for(int x = 0; x < numWhites; x++){
          randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          while(randStudent.getRace() != Person.Race.WHITE  ||
            groupContainsStudent(randStudent)){
            randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          }
          students.add(randStudent);
          randStudent.joinGroup(this);
        }
        for(int x = 0; x < numMinorities; x++){
          randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          while(randStudent.getRace() != Person.Race.MINORITY  ||
            groupContainsStudent(randStudent)){
            randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          }
          students.add(randStudent);
          randStudent.joinGroup(this);
        }
    }

    private ArrayList<Person> findStudentsToRecruit(ArrayList<Person> people){
        int numPeople = NUM_PEOPLE_TO_RECRUIT;
        ArrayList<Person> recruits = new ArrayList<Person>();
        Person randStudent;
        if(numPeople>Sim.getNumPeople()){
          numPeople=Sim.getNumPeople();    //to ensure the initial group size 
          // is never greater than the number of total people
        }
        for(int x = 0; x < numPeople; x++){
          randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          while(groupContainsStudent(randStudent)){
            randStudent = people.get(Sim.instance().random.nextInt(people.size()));
          }
          recruits.add(randStudent);
        }
        return recruits;
    }

    private void recruitStudent(Person s){
        if(!groupContainsStudent(s)){

          //FIX FOR DECIMALS
            double r = (affinityTo(s) + recruitmentFactor + 
                s.getExtroversion()*2 + Sim.instance().random.nextDouble()*2)/6.0; 
                //want to mess with balance here
            if(r>RECRUITMENT_REQUIRED){
                students.add(s);
                s.joinGroup(this);
            }
        }
    }
  
    private boolean groupContainsStudent(Person p){
        for (int x = 0; x<students.size(); x++){
          if (p.getID()==students.get(x).getID()){
            return true;
          }
        }
        return false;
    }
  

    /**
     * Return a number from 0 to 1 indicating the degree of affinity the
     *   Person passed has to the existing members of this group.
     */
     
     public double affinityTo(Person p){
         if(getSize( ) > 0){
             double temp = 0;
             for(int x = 0; x < students.size( ); x++){
                 Person q = students.get(x);
                 if(q.getID( ) != p.getID( )){
                     temp = temp + p.similarityTo(q);
                 }
             }
             return temp / students.size( );
         }else{
             return 0.5;
         }
     }

   	private void influenceMembers( ){
   	    //should we add something short for if n=1 or just let it go through
   	    //the loop even though that's kind of a waste
   	    int n = students.size( );
      if(n > 0){
    	ArrayList<Double> independentAverage = new ArrayList<Double>();
    	ArrayList<Double> dependentAverage = new ArrayList<Double>();
      	double tempTotal;
      	for (int x = 0; x < students.get(0).getIndependentAttributes( ).size( ); x++){    
        	tempTotal=0;
        	for (int y = 0; y < n; y++){
          		tempTotal+=students.get(y).getIndependentAttributes( ).get(x);
        	}
        	independentAverage.add(tempTotal/n);
      	}
      	for (int x = 0; x < students.get(0).getDependentAttributes( ).size( ); x++){
        	tempTotal=0;
        	for (int y = 0; y < n; y++){
          		tempTotal+=students.get(y).getDependentAttributes().get(x);
        	}
        	dependentAverage.add(tempTotal/n);
      	}

        //At this point, both independentAverage and dependentAverage are
        //filled the following should use two rands-- one to see if the
        //attribute should in fact change, and another to be used to
        //multiply by the distance to calculate how much it would increment
        //by note that a group's influence will never directly decrement
        //any attributes-- dependent attributes may only decrement by
        //indirect normalization We have to keep our numbers pretty low
        //here-- this will be called at every step
        
      	double distanceI;  //distance between current person's current 
        // independent attribute and the group's average attribute
      	double distanceD;  //distance between current person's current 
        // dependent attribute and group's average attribute
      	double increment; //how much each attribute will increment by
      	for(int x = 0; x < n; x++){
        	for (int y = 0; y < independentAverage.size( ); y++){
          		distanceI = independentAverage.get(y) - students.get(x).getIndependentAttributes().get(y);
          		distanceD = dependentAverage.get(y) - students.get(x).getDependentAttributes().get(y);
          		if(Sim.instance( ).random.nextDouble(true,true)< LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE){
            		increment = (Sim.instance( ).random.nextDouble(true,true)/52) * distanceI; 
                    //random number inclusively from 0-1, then divide by 5,
                    //then multiply by the distance that attribute is from
                    //the group's average
            		students.get(x).setIndAttrValue(y, (students.get(x).getIndependentAttributes().get(y)) +
                        increment);
          		}  

          		if(Sim.instance().random.nextDouble(true,true) < LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE){  
            		increment = (Sim.instance( ).random.nextDouble(true, true)/5)*distanceD;
            		students.get(x).setDepAttrValue(y,(students.get(x).getDependentAttributes().get(y)) +
                        increment);  //Morgan's method
          		}
        	}
      	}
      }
    }

     private void possiblyLeaveGroup(Person p){
      if(Sim.instance().random.nextDouble(true,true)<
            LIKELIHOOD_OF_RANDOMLY_LEAVING_GROUP && 
            students.size()>MINIMUM_GROUP_SIZE){
        p.leaveGroup(this);
        removeStudent(p);
      }
    }

    /**
     * Make this group perform one month's actions. These include:
     * <ol>
     * <li>"Backwards influence" its members, by possibly altering some of
     * their attributes to make them more like those of the group at
     * large.</li>
     * <li>Recruit other students from the student body at large.</li>
     * <li>Give each member a chance to leave the group.</li>
     * </ol>
     * After this, the Group reschedules itself for the next month (or
     * August, if it's coming up on summertime.)
     * <p>Note that Groups only step during academic months.</p>
     */
      public void step(SimState state){
        System.out.println("#### GROUP " + id + " (" + 
            state.schedule.getTime() + ")");
        influenceMembers();
        ArrayList<Person> recruits = findStudentsToRecruit(Sim.getPeople());
        for(int x = 0; x < recruits.size(); x++){
          recruitStudent(recruits.get(x));
        }
        for(int x = 0; x<students.size(); x++){
          possiblyLeaveGroup(students.get(x));
        }
        
        if (Sim.instance().nextMonthInAcademicYear()) {
          // It's not the end of the academic year yet. Run again
          // next month.
          Sim.instance( ).schedule.scheduleOnceIn(1, this);
        } else {
            if (!Sim.instance().isLastYearOfSim()) {
              // It's summer break! Sleep for the summer.
              Sim.instance( ).schedule.scheduleOnceIn(
                  Sim.NUM_MONTHS_IN_SUMMER + 1, this);
            }
        }
    }
  
  
    /** Sets the recruitment factor for this group, which is a number in the
     * range 0 to 1 indicating how aggressive the group is in attracting
     * members. Higher numbers increase the likelihood of {@link
     * #recruitStudent(Person)} returning true.
     */
    public void setRecruitmentFactor(double r){
      recruitmentFactor=r;
    }
    
    /** Returns the number of students currently in the group.
     */
    public int getSize(){
      return students.size();
    }
  
    /** Returns the number of students of a particular race currently in the 
     * group.
     */
    public int getSize(Person.Race race){
        int num=0;
        for (int i=0; i<students.size(); i++) {
            if (students.get(i).getRace() == race) {
                num++;
            }
        }
        return num;
    }

    public static void printHeaderToGroupsFile(PrintWriter pw) {
        pw.println("year,id,numMin,numWhi");
    }

    public void printToFile(PrintWriter pw) {
        pw.println(Sim.instance().getCurrYearNum() + "," + id + "," + 
            getSize(Person.Race.MINORITY) + "," + getSize(Person.Race.WHITE));
    }

    /** Gets the recruitment factor for this group (See {@link
     * #setRecruitmentFactor(double)}.)
     */
    public double getRecruitmentFactor(){
      return recruitmentFactor;
    }
    

    private void listMembers(){
      System.out.println("The following students are in group " + id + ":");
      for(int x = 0; x < students.size(); x++){
        System.out.println("\t" + students.get(x));
      }
    }
  
    /** Get the ID of this group, a unique number across all group objects.
     */
    public int getID(){
      return id;
    }

    /** Returns the member whose position in the person vector is passed. 
     * (FIX: seems like a weird API?) */
    public Person getPersonAtIndex(int x){
      return students.get(x);
    }

    /** Removes the person passed from this group. If the person is not
     * already a member, does nothing. */
    public void removeStudent(Person p){
      for(int x = 0; x<students.size(); x++){
          if(students.get(x).getID( ) == p.getID( )){
            students.remove(x);
          }
      }
    }

    /** Clears all Person objects from this group. (This method will in
     * turn inform each ex-member that they are no longer members.) */
    public void removeEveryoneFromGroup(){
      for(int x = 0; x<students.size(); x++){
        students.get(x).leaveGroup(this);
      }
    }

}
