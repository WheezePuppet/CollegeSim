package edu.umw.cpsc.collegesim;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Enumeration;

import sim.util.distribution.Normal;

import java.lang.Math;

import sim.engine.*;
import sim.util.*;
import ec.util.*;
import sim.field.network.*;

/**
 * A student in the CollegeSim model.
 * <p></p>
 * Purpose in life:
 * <ul>
 * <li>Maintain a list of attributes, including constants, preferences,
 * hobbies, and race. (In the case of hobbies, which auto-normalize, this is
 * non-trivial.)</li>
 * <li>Every month, <b>encounter</b> some people who are members of your
 * groups, and some totally random people. Encounter means:
 *      <ul>
 *      <li>If you're friends with them, <b>tickle</b> (refresh).</li>
 *      <li>If you're not friends with them, <b>meet</b> them. Meet means:
 *          <ol>
 *          <li>Compute their <b>similarity</b>, on a scale of 0 to 1. This
 *          involves constant attributes, preferences, hobbies, and race,
 *          all weighted differently.</li> 
 *          <li>Based on the similarity, determine whether they become
 *          friends. (This is random, but based in part on {@link
 *          #FRIENDSHIP_COEFFICIENT} and {@link #FRIENDSHIP_INTERCEPT}.)</li>
 *          </ol>
 *      </li>
 *      </ul>
 * </li>
 * <li>Every month, decay friendships. Any friend who has not tickled / been
 * tickled since {@link #DECAY_THRESHOLD} becomes a non-friend.</li>
 * <li>This class can also compute how alienated the person feels based on her
 * number of friends (and your extroversion, currently constant). This is used
 * by {@link Sim} to drop students out.</li>
 * <li>This class has various group-related functions, like
 * <code>joinGroup()</code>, <code>isStudentInGroup()</code>, and
 * <code>leaveGroup()</code>.</li>
 * </ul>
 */
public class Person implements Steppable {

    public enum Race { WHITE, MINORITY };
    public enum Gender { MALE, FEMALE };

    /**
     * Baseline prior probability that a newly generated student will be of
     * race "WHITE". */
    public static double PROBABILITY_WHITE;

    /**
     * Baseline prior probability that a newly generated student will be of
     * gender "FEMALE". */
    public static final double PROBABILITY_FEMALE = 1;
    
    /** A number reflecting the relative importance that race has in
     * determining perceived similarity. The "units" of this constant are
     * in "equivalent number of attributes"; <i>i.e.</i>, if the
     * RACE_WEIGHT is 4, this means that if another person is the same race
     * as you, this will impact your perceived similarity to them (and
     * theirs to you) to the same degree that four of your individual
     * attributes being the same would. */
    public static double RACE_WEIGHT;

    /** A number reflecting the relative importance that gender has in
     * determining perceived similarity. The "units" of this constant are
     * in "equivalent number of attributes"; <i>i.e.</i>, if the
     * GENDER_WEIGHT is 4, this means that if another person is the same
     * gender as you, this will impact your perceived similarity to them
     * (and theirs to you) to the same degree that four of your individual
     * attributes being the same would. */   
    public static final double GEN_WEIGHT = 0;

    /** The relative importance of "constant" attributes, with respect to
     * other types of attributes. ("Constant" attributes are those that are
     * unchangeable; <i>e.g.</i>, "where are you from?") */
    public static final double CONST_WEIGHT = 1;

    /** The relative importance of "preferences", with respect to other types
     * of attributes. ("Preferences" are those attributes that can vary
     * independently with respect to each other. Having more of one preference
     * does not impact your value of another preference . (<i>e.g.</i>, the
     * degree to which you like purple does not depend on the degree to which
     * you like basketball.) */
    public static final double PREF_WEIGHT = 1.5;

    /** The relative importance of "hobbies", with respect to other types of
     * attributes. ("Hobbies" are those attributes that affect one other.
     * Having more of one hobby invariably means having relatively less of
     * others. (<i>e.g.</i>, the degree to which you spend time mountain
     * biking has an effect on the amount of time you spend reading graphic
     * novels, because time is constant. */
    public static final double HOBBY_WEIGHT = 2.5;
    
    /** The coefficient (see also {@link #FRIENDSHIP_INTERCEPT}) of a linear
     * equation to transform perceived similarity to probability of
     * friendship. If x is the perceived similarity, then y=mx+b, where m
     * is the FRIENDSHIP_COEFFICIENT, b the FRIENDSHIP_INTERCEPT, and y gives
     * the probability of becoming friends. */
    public static double FRIENDSHIP_COEFFICIENT;

    /** See {@link #FRIENDSHIP_COEFFICIENT}. */
    public static double FRIENDSHIP_INTERCEPT;
  
    /** Each time step (= 1 month), how many other people from a person's 
     * groups that person will encounter. Note that this number is only
     * unidirectional; <i>i.e.</i>, this person may well "be met by" 
     * numerous other people when their step() methods run. */
    public static int NUM_TO_MEET_GROUP;

    /** Each time step (= 1 month), how many other people from the overall
     * student body a person will encounter. Note that this number is only
     * unidirectional; <i>i.e.</i>, this person may well "be met by" 
     * numerous other people when their step() methods run. */
    public static int NUM_TO_MEET_POP;

    /** The number of consecutive months that two friends can <i>not</i>
     * tickle each other and yet remain friends. */
    public static int DECAY_THRESHOLD;

    /** When a new Student is created, the number of automatic friends of the
     *  opposite race that they will get. */
    public static int INITIAL_NUM_FORCED_OPPOSITE_RACE_FRIENDS;
    
    /** Each time step, the probability that a student will change one of
     * their attribute values. */
    //I removed "<i>provided</i> that said attribute is different enough from their influencing peers to warrant change"
    //because it was inaccurate - likelihood has nothing to do with attribute difference but is pure chance
    public static double LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE = .1;

    // Hand out consecutive unique numbers to new people.
    private static int nextPersonId = 0;
    private int id;

    private int year;
/*
 *  Take out extroversion for now. Everyone is at .5.
    private Normal extroversionDistro = 
        new Normal(.5, .15, Sim.instance( ).random);
*/
    
    private Race race;
    private Gender gender;
    
    private double extroversion;
    private ArrayList<Group> groups;
  
    /** The total number of "constant" attributes in the system. (See {@link
     * #CONST_WEIGHT}.) Each person will have a value from 0 to 1 for each,
     * indicating the extent to which they possess the attribute. */
    public static int CONSTANT_ATTRIBUTE_POOL_SIZE = 0;  // 100;
    private ArrayList<Double> constantAttrs     //Constant attributes
        = new ArrayList<Double>(
            Collections.nCopies(CONSTANT_ATTRIBUTE_POOL_SIZE, 0.0));
  
    /** The number of preferences each person has. (See {@link
     * #PREF_WEIGHT}.) */
    public static int NUM_PREFERENCES;

    /** The total number of "preferences" in the system. (See 
     * {@link #PREF_WEIGHT}.) Each person will either have the attribute or
     * not; and if they do, they will have a double value assigned
     * indicating its strength. (Morgan: is this still accurate?) */
    public static int PREFERENCE_POOL_SIZE;

    //A person's preferences, which can change but do not affect each other
    private ArrayList<Double> preferences
      = new ArrayList<Double>(Collections.nCopies(
            PREFERENCE_POOL_SIZE, 0.0));
  
    /** The number of "hobbies" attributes each person has. (See {@link
     * #HOBBY_WEIGHT}.) */
    public static int NUM_HOBBIES;

    /** The total number of "hobbies" in the system. (See 
     * {@link #HOBBY_WEIGHT}.) Each person will either have the attribute or
     * not; and if they do, they will have a double value assigned
     * indicating its strength. */
    public static int HOBBY_POOL_SIZE;

    //hobbies, which can change but you only have 1 unit to 
    //split among them
    //in other words, if one increases, then another decreases
    private ArrayList<Double> hobbies
      = new ArrayList<Double>(Collections.nCopies(
            HOBBY_POOL_SIZE, 0.0));

    /**
    * The following ArrayLists are used to store each student's influencible
    * preferences at the beginning and end of each year. The end of the 
    * student's last year will be the preferences and hobbies 
    * variables
    */
    private ArrayList<Double> preferencesYear0;
    private ArrayList<Double> hobbiesYear0;
    private ArrayList<Double> preferencesYear1;
    private ArrayList<Double> hobbiesYear1; 
    private ArrayList<Double> preferencesYear2;
    private ArrayList<Double> hobbiesYear2; 
    private ArrayList<Double> preferencesYear3;
    private ArrayList<Double> hobbiesYear3;

    //A list that will house the absolute sim time that this person first met,
    //or last tickled, each other person
    private Hashtable<Integer,Double> lastTickleTime
      = new Hashtable<Integer,Double>();

    /*
     * Personality drift for being affected by a Person's friends.
     */
    private void personalityDrift( ){
    	//Get a bag of the edges to the person's friends
    	Bag b = Sim.peopleGraph.getEdgesIn(this);
   	    
    	//should we add something short for if n=1 or just let it go through
   	    //the loop even though that's kind of a waste
   	    int n = b.size( );
   	    //If we have at least a single friend
   	    if(n > 0){
   	    	ArrayList<Double> preferenceAverage = new ArrayList<Double>();
   	    	ArrayList<Double> hobbyAverage = new ArrayList<Double>();
   	    	double tempTotal;
   	    	//For each attribute
   	    	for (int x = 0; x < PREFERENCE_POOL_SIZE; x++){
   	    		//Temporary total for attribute at index x
   	    		tempTotal = 0;
   	    		//For each friend
   	    		for (int y = 0; y < n; y++){
   	    			//add the value for this attribute to the temporary total
   	    			tempTotal+=((Person)
                        ((Edge) b.get(y)).getOtherNode(this)).getPreferences( ).get(x);
   	    		}
   	    		//Set the average value for this attribute
   	    		preferenceAverage.add(tempTotal/n);
   	    	}
   	    	//Do the same for hobbies
   	    	for (int x = 0; x < HOBBY_POOL_SIZE; x++){
   	    		tempTotal = 0;
   	    		for (int y = 0; y < n; y++){
   	    			tempTotal+=((Person)
                        ((Edge) b.get(y)).getOtherNode(this)).getHobbies( ).get(x);
   	    		}
   	    		hobbyAverage.add(tempTotal/n);
   	    	}
        
   	    	double distanceI;
   	    	double distanceD;
   	    	double increment;
   	    	
   	    	//For each preference 
   	    	for(int y = 0; y < PREFERENCE_POOL_SIZE; y++){
   	   			//Calculate the distance between the average attribute and this person's attribute
   	   			distanceI = preferenceAverage.get(y) - getPreferences( ).get(y);
   	   			//If we randomly decide to change the attribute
   	    		if(Sim.instance( ).random.nextDouble(true, true) < LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE){
   	    			//Calculate an increment by which to change using the distance and a random factor
   	    			increment = (Sim.instance( ).random.nextDouble(true,true)/5) * distanceI;
   	    			//Set the new preference value to whatever it was before plus the increment
   	    			setPreferenceValue(y, getPreferences( ).get(y) + increment);
   	    		}
   	    	}
   	    	
   	    	//The same process for hobbies
   	    	for(int y = 0; y < HOBBY_POOL_SIZE; y++){
   	    		distanceD = hobbyAverage.get(y) - getHobbies( ).get(y);
   	    		if(Sim.instance().random.nextDouble(true, true) < LIKELIHOOD_OF_RANDOMLY_CHANGING_ATTRIBUTE){  
   	    			increment = (Sim.instance( ).random.nextDouble(true, true)/5)*distanceD;
   	    			setHobbyValue(y, getHobbies( ).get(y) + increment);
   	    		}
   	    	}
      	}
   	}


    /** Removes this student from the university, forcing them to leave all 
     * groups. */
    public void leaveUniversity( ){
    	//This removes this person from all of their groups
    	for(int i=0; i<groups.size( ); i++){
    		Group group = groups.get(i);
    		group.removeStudent(this);
    	}
    }
    
    
    /**
     * The person whose id (index) is passed has now been tickled, so this
     * sets the last tickle time for this person to the current time.
     */
    public void refreshLastTickleTime(int index){
        lastTickleTime.put(index,Sim.instance().schedule.getTime());
    }
    
    /**
     * Blah, I'm no longer friends with this person, so this completely
     * removes them from my hashtable (and life.)
     */
    public void resetLastTickleTime(int index){
        lastTickleTime.remove(index);
    }
    
    private void decay( ){
        Enumeration<Integer> friendIDs = lastTickleTime.keys();
        while (friendIDs.hasMoreElements()) {
            int friendID = friendIDs.nextElement();
            Edge toRemoveIn = null;
            Edge toRemoveOut = null;
            double val = lastTickleTime.get(friendID);
            //if the people last met longer than the threshold ago
            if(Sim.instance().schedule.getTime() - val >= DECAY_THRESHOLD){
                Sim.instance().encounterWriter.println(
                    Sim.instance().getCurrYearNum()+","+id+","+friendID+
                        ",decay");
                Sim.instance().encounterWriter.flush();
              //Get a bag of all the edges into this person
              Bag bIn = Sim.peopleGraph.getEdgesIn(this);
              //for each of these edges
              for(int j=0; j<bIn.size( ); j++){
                //look for the person whose id matches the id of the 
                //person we want to decay
                Edge edgeIn = (Edge)bIn.get(j);
                Person otherPerson = (Person) edgeIn.getOtherNode(this);
                int otherID = otherPerson.getID( );
                if(otherID == friendID){
                  //when we find the person, make their edge the one we 
                  //want to remove
                  toRemoveIn = edgeIn;
                  j = bIn.size( );
                }
              }
              //Do the same with the other edges
              Bag bOut = Sim.peopleGraph.getEdgesOut(this);
              Person otherPerson = null;
              //for each of these edges
              for(int j=0; j<bOut.size( ); j++){
                //look for the person whose id matches the id of the person 
                //we want to decay
                Edge edgeOut = (Edge)bOut.get(j);
                otherPerson = (Person) edgeOut.getOtherNode(this);
                int otherID = otherPerson.getID( );
                if(otherID == friendID){
                  //when we find the person, make their edge the one we 
                  //want to remove
                  toRemoveOut = edgeOut;
                  otherPerson.resetLastTickleTime(id);
                  j = bOut.size( );
                }
              }
              //Do we have to do this? Remove the edge in and the edge out?
              Sim.peopleGraph.removeEdge(toRemoveIn);
              Sim.peopleGraph.removeEdge(toRemoveOut);
              resetLastTickleTime(friendID);
            }
          }
    }
    
    private void assignAttribute(int numAttr, ArrayList<Double> attr){
      boolean okay;
      for(int i=0; i<numAttr; i++){
     /*   //pick an attribute to change
        int index = Sim.instance( ).random.nextInt(poolSize);
        okay = false;
        //while we have not chosen an appropriate index
        while(!okay){
          //if the attribute is zero, it has not already been changed, so 
          //we use it
          if(attr.get(index) == 0.0){
            okay = true;
          //otherwise, we have to pick a new attribute
          }else{
            index = Sim.instance( ).random.nextInt(poolSize);
          }
        }*/
        //pick a degree to which the person will have this attribute
        //we generate a number between 0 and 1, including 1 but not including 0
        double degree = Sim.instance( ).random.nextDouble(false, true);
        //then we set the attribute at index i to be degree
        attr.set(i, degree);
      }
    }
    
    private boolean assignRaceGender(double probability){
      double gen = Sim.instance( ).random.nextDouble();
      if(gen <= probability){
        return true;
      }else{
        return false;
      }
    }
    
    Person() {
        this.id = nextPersonId++;
        groups = new ArrayList<Group>( );

        //Assigning constant attributes
        for(int i=0; i<CONSTANT_ATTRIBUTE_POOL_SIZE; i++){
            double rand = Sim.instance( ).random.nextDouble( );
            constantAttrs.set(i, rand);
        }
        //Assigning preferences
        assignAttribute(NUM_PREFERENCES, preferences);
        //Assigning hobbies
        assignAttribute(NUM_HOBBIES, hobbies);
        //
        //Assign a race   
        boolean white = assignRaceGender(PROBABILITY_WHITE);
        if(white){
            race = Race.WHITE;
        }else{
            race = Race.MINORITY;
        }
        //Assign a gender
        boolean female = assignRaceGender(PROBABILITY_FEMALE);
        if(female){
            gender = Gender.FEMALE;
        }else{
            gender = Gender.MALE;
        }
        //extroversion = extroversionDistro.nextDouble();
        //Take out extroversion for now.
        extroversion = .5;

    }
  
  /**
   * Meet the person passed as an argument, who is expected to <i>not</i>
   * already be friends with that person. Determine whether these two will
   * become friends, and if so, make them so. */
  public void meet(Person personToMeet){
    double similarity;
    boolean friends = false;
    int personToMeetID = personToMeet.getID( );
    //Calculate their similarity rating, and then see if they should become 
    //friends
    similarity = similarityTo(personToMeet);
    friends = becomeFriends(similarity);
    if (race==personToMeet.race) {
      Sim.instance().similarityWriter.println(
          Sim.instance().getCurrYearNum()+","+race+","+similarity+","+friends);
    } else {
      Sim.instance().similarityWriter.println(
          Sim.instance().getCurrYearNum()+",MIXED,"+similarity+","+friends);
    }
    Sim.instance().similarityWriter.flush();
    //if they become friends, add their edge to the network
    //and reset when they met
    if(friends){
        makeFriends(personToMeet);
        Sim.instance().encounterWriter.println(
            Sim.instance().getCurrYearNum()+","+id+","+personToMeet.id+
                ",meetFriends");
    } else {
        Sim.instance().encounterWriter.println(
            Sim.instance().getCurrYearNum()+","+id+","+personToMeet.id+
                ",meetNoFriends");
    }
    Sim.instance().encounterWriter.flush();
  }

  /**
   * Make this person "tickle" the person passed as an argument, who is
   * presumed to <i>already</i> be friends with the person. ("Tickle"
   * essentially means "refresh their friendship.") */
  public void tickle(Person person){
    Sim.instance().encounterWriter.println(
        Sim.instance().getCurrYearNum()+","+id+","+person.id+",tickle");
    Sim.instance().encounterWriter.flush();

    //reset when the two last encountered each other
    int tickleID = person.getID( );
    refreshLastTickleTime(tickleID);
    person.refreshLastTickleTime(id);
  }
  
  /**
   * Make this person encounter some number of other people from the given
   * pool. Note that the pool could be group membership, the entire campus,
   * and so on. Choose a person from the pool at random. If the two are
   * already friends, tickle the friendship. Otherwise, meet this person. Do
   * this until we have encountered the appropriate number of friends.*/
  private void encounter(int number, Bag pool){
    if(pool.size( ) < number){
      number = pool.size( );
    }
    for(int i=0; i<number; i++){
      Person personToMeet;
      do{
        personToMeet = (Person) 
            pool.get(Sim.instance( ).random.nextInt(pool.size( )));
      }while(personToMeet.id == id);
      if(friendsWith(personToMeet)){
        tickle(personToMeet);
      }else{
        meet(personToMeet);
      }
    }
  }
  
    /**
     * Make this person perform one month's actions. These include:
     * <ol>
     * <li>Encounter {@link #NUM_TO_MEET_GROUP} other people who are members
     * of one or more of their current groups.</li>
     * <li>Encounter {@link #NUM_TO_MEET_POP} other people from the student
     * body at large (who may or may not be members of their current
     * groups.)</li>
     * <li>Decay this user's existing friendships to reflect the passage of
     * time.</li>
     * </ol>
     * After this, the Person reschedules itself for the next month (or
     * August, if it's coming up on summertime.)
     * <p>Note that Persons only step during academic months.</p>
     */
    public void step(SimState state){
        System.out.println("#### PERSON " + id + " (" +
             state.schedule.getTime() + ")");
        Bag peopleBag = Sim.peopleGraph.getAllNodes( );
        if(!peopleBag.contains(this)){
            return;
        }
        //Get a bag of all the people in the groups
        Bag groupBag = getPeopleInGroups( );
        if(groupBag.size( ) > 1){
            encounter(NUM_TO_MEET_GROUP, groupBag);
        }
        //Get a bag of all the people and then encounter some number of those 
        //people
        if(peopleBag.size( ) > 1){
            encounter(NUM_TO_MEET_POP, peopleBag);
        }

        personalityDrift( );
        
        //NOTE: Decay only matters if the people are friends- you can't decay a
        //friendship that doesn't exist. So, the time they last met only
        //matters if they are friends already or if they become friends this
        //turn If they aren't already friends and if they don't become
        //friends this turn, then -1 for last met is fine (unless we
        //implement something where if two people meet enough times, they
        //become friends by brute force)
        //Now we want to see if any of the friendships have decayed
        decay( );

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

    public static void printHeaderToFile(BufferedWriter writer) {
        try {
            writer.write("period,id,numFriends,numGroups,race,gender,alienation,yearInSchool\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Output diagnostic and statistical information about this Person to
     * the writer passed.
     */
    public void printToFile(BufferedWriter writer) {
        String message = Sim.instance().getCurrYearNum() + "," + 
            Integer.toString(id) + ",";
        Bag b = Sim.peopleGraph.getEdgesIn(this);
        int numFriends = b.size( );
        message = message + Integer.toString(numFriends) + ","
            + Integer.toString(groups.size( )) + "," + race + "," + gender + ","
            + this.getAlienation( ) +  "," + year + "\n";
        try {
            writer.write(message);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Output friendship information.
     */
    public void printFriendsToFile(BufferedWriter writer) {
        String message = "";
        Bag b = Sim.peopleGraph.getEdgesIn(this);
        for (int i=0; i<b.size( ); i++) {
        	Person friend = (Person) ((Edge)b.get(i)).getOtherNode(this);
        	//We only document the friendship if the other person's id is greater
        	//otherwise, the friendship edge was already documented
        	message = message + Sim.instance().getCurrYearNum() + "," +
                this.getID( ) + "," + friend.getID( ) + "\n";
        }
        //We'll only try to write if there are actually friends
        if(b.size( ) > 0){
        	try {
        		writer.write(message);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
    }

    private boolean friendsWith(Person other) {
      Bag b = Sim.peopleGraph.getEdgesIn(this);
        for (int i=0; i<b.size(); i++) {
            Person otherSideOfThisEdge = 
                (Person) ((Edge)b.get(i)).getOtherNode(this);
            if (other.id == otherSideOfThisEdge.id) {
                return true;
            }
        }
        return false;
    }
    
    private boolean met(Person other){
      int otherID = other.getID( );
      if(lastTickleTime.get(otherID) == -1){
        return false;
      }else{
        return true;
      }
    }

    public void printPreferencesToFile(BufferedWriter writer) {
        String message = Sim.instance().getCurrYearNum() + "," + 
            this.getID( ) + ",";
        Bag b = Sim.peopleGraph.getEdgesIn(this);
        int numFriends = b.size( );
        message = message + numFriends + "," + race + "," + this.getAlienation() + "," + year + "\n";
        try {
          writer.write(message);
        } catch (Exception e) {
          e.printStackTrace();
        }
    }

    public void printChangeToFile(BufferedWriter writer) {
        double prefAverage=0;
        double hobbyAverage=0;
        String message = "";
        for(int x = 0; x < NUM_PREFERENCES; x++){
          prefAverage += Math.abs(preferences.get(x) - preferencesYear0.get(x));
        }
        prefAverage=prefAverage/NUM_PREFERENCES;
        for(int x = 0; x < NUM_HOBBIES; x++){
          hobbyAverage += Math.abs(hobbies.get(x) - hobbiesYear0.get(x));
        }
        hobbyAverage=hobbyAverage/NUM_HOBBIES;
        message = message + getID() + " " + extroversion + " " + Sim.peopleGraph.getEdgesIn(this).size() + " " + groups.size() + " " + hobbyAverage + " " + prefAverage + "\n";
        try {
          writer.write(message);
        } catch (Exception e) {
          e.printStackTrace();
        }
    }

    public String toString() {
        Bag b = Sim.peopleGraph.getEdgesIn(this);
        if (b.size() == 0) {
            return "Person " + id + " (lonely with no friends)";
        }
        String retval = "Person " + id + " (friends with ";
        for (int i=0; i<b.size(); i++) {
            retval += ((Person)(((Edge)b.get(i)).getOtherNode(this))).id;
            if (i == b.size()-1) {
                retval += ")";
            } else {
                retval += ",";
            }
        }
        return retval;
    }
    
    public int getID( ){
      return id;
    }
  
  public Race getRace( ){
	  return race;
  }
  
  public Gender getGender( ){
	  return gender;
  }
 
  double getExtroversion( ){
    return extroversion;
  }
  
  void joinGroup(Group group){
    groups.add(group);
  }
  
  boolean isStudentInGroup(Group group){
    for(int x = 0; x<groups.size( ); x++){
      if(groups.get(x).getID( ) == group.getID( )){
        return true;
      }
    }
    return false;
  }
    
    /**
     * Based on the possible presence of popular attributes possessed by
     * the Group's members, possibly absorb one or more of these attributes
     * into this Person, if he/she does not already have them.
     */
    public void possiblyAbsorbAttributesFrom(Group g) {
        // for now, if you absorb an attribute, just add it, don't try to
        // even it out by removing one.
    }

    // for doubles
    private double attrCounter(int num, ArrayList<Double> attr1, 
        ArrayList<Double> attr2){
    	
    	double sum1 = 0.0;
    	double sum2 = 0.0;
    	for(int i = 0; i < num; i ++){
    		sum1 += attr1.get(i);
    		sum2 += attr2.get(i);
    	}
    	
    	double difference = Math.abs(sum1 - sum2);
    	//return the similarity rating
    	return (num - difference);
/*      int count = 0;
      for(int i=0; i<num; i++){
        double difference = attr1.get(i) - attr2.get(i);
        difference = Math.abs(difference);
        //if the difference is within the accept interval
        if(difference <= interval){
          //increment constant count
          count++;
        }
      }
      return count;*/
    }
    
    /**
     * Returns a number between 0 and 1 indicating how similar this person
     * is perceived to be to the person passed. (1 = perfect similarity.) */
    public double similarityTo(Person other) {
      double similarity = 0.0;
      
      //Kind 1: Constant
      double constantCount = attrCounter(CONSTANT_ATTRIBUTE_POOL_SIZE,
        constantAttrs, other.constantAttrs);
      
      //Kind 2: Preferences
      double prefCount = attrCounter(PREFERENCE_POOL_SIZE, preferences, 
        other.preferences);
      
      //Kind 3: Hobbies
      ArrayList<Double> normalK3This = normalize(hobbies);
      ArrayList<Double> normalK3Other = normalize(other.hobbies);
      double hobbyCount = attrCounter(HOBBY_POOL_SIZE, normalK3This, 
        normalK3Other);
      
        //Do they have the same race?
        int raceCount = 0;
        if(race == other.race){
          raceCount = 1;
        }
        //Do they have the same gender?
        int genCount = 0;
        if(gender == other.gender){
          genCount = 1;
        }
        //Calculate their similarity rating, taking importance of each 
        //category (the weight) into account
      similarity = (constantCount * CONST_WEIGHT) + (prefCount * PREF_WEIGHT)
          + (hobbyCount * HOBBY_WEIGHT) + (raceCount * RACE_WEIGHT) 
          + (genCount * GEN_WEIGHT);
      double maxRating = (CONSTANT_ATTRIBUTE_POOL_SIZE * CONST_WEIGHT) 
          + (PREFERENCE_POOL_SIZE * PREF_WEIGHT)
          + (HOBBY_POOL_SIZE * HOBBY_WEIGHT) + RACE_WEIGHT + GEN_WEIGHT;
      return similarity / maxRating;
    
    }
    
  private boolean becomeFriends(double similarity){
    double acceptProb = 
        FRIENDSHIP_COEFFICIENT * similarity + FRIENDSHIP_INTERCEPT;
    double friendProb = Sim.instance( ).random.nextDouble( );
    if(friendProb <= acceptProb){
      return true;
    }else{
      return false;
    }
  }
  
  private ArrayList<Double> normalize(ArrayList<Double> attr){
    ArrayList<Double> normal = new ArrayList<Double>(
        Collections.nCopies(HOBBY_POOL_SIZE, 0.0));
    double sum = 0.0;
    for(int i=0; i<HOBBY_POOL_SIZE; i++){
        sum = sum + attr.get(i);
      }
    for(int i=0; i<HOBBY_POOL_SIZE; i++){
        double valThis = attr.get(i)/sum;
        normal.set(i,valThis);
      }
    return normal;
  }
  
  
  public double getAlienation( ){
	  //Get the number of friends this person has
	  Bag bIn = Sim.peopleGraph.getEdgesIn(this);
	  int numFriends = bIn.size( );
	  //Find the percent of the population with which this person is friends
	  //int totalPeople = Sim.getNumPeople( );
	  double requiredNumFriends = 3.0;
	  double percFriends = numFriends / requiredNumFriends;
	  //As extroversion increases, the likelihood to feel alienated increases
	  //As the percent of friends you have in the population increases, the likelihood
	  //to feel alienated decreases
	  double alienationFactor = extroversion / percFriends;
	  if(alienationFactor > 1){
		  alienationFactor = 1;
	  }
	  return alienationFactor;
  }
  
  
  /** Returns a list of doubles, one for each of the {@link
   * #HOBBY_POOL_SIZE} possible hobbies. This will indicate
   * the degree to which the person possesses each of those attributes (0.0
   * = does not have that attribute at all.) */
  public ArrayList<Double> getHobbies(){
    return normalize(hobbies);
  }
  
  /** Returns a list of doubles, one for each of the {@link
   * #PREFERENCE_POOL_SIZE} possible preferences. */
  public ArrayList<Double> getPreferences(){
    return preferences;
  }

  /** Sets the value of the preference whose index is passed to the value
   * passed. */
  public void setPreferenceValue(int index, double val){
      preferences.set(index, val); 
  }

  /** Sets the value of the hobby whose index is passed to the value passed.
   * Internally, this may have the side effect of adjusting the values of the
   * other hobbies so that their normalized sum continues to equal 1. */
  public void setHobbyValue(int index, double val){
    //this functions says I want the normalized value of attribute index to be
    //val
    double sum = 0.0;
    //Take the sum of all of the other non-normalized values
    for(int i=0; i<HOBBY_POOL_SIZE; i++){ if(index != i){ sum = sum +
    hobbies.get(i); } } double newNonNormalVal = (val * sum)/(1-val);
    hobbies.set(index, newNonNormalVal); }


    /**
     * Returns the union of all the students who are in at least one of
     * this student's groups.
     */
  public Bag getPeopleInGroups( ){
    boolean repeat = false;
    Bag groupmates = new Bag();
    for(int x = 0; x < groups.size( ); x++){
      for(int y = 0; y < groups.get(x).getSize(); y++){
        for(int z = 0; z < groupmates.size(); z++){
          if(groups.get(x).getPersonAtIndex(y).id == ((Person) groupmates.get(z)).id){
            repeat = true;    //student is already in this bag, don't add again
          }
        }
        if(!repeat){
          groupmates.add(groups.get(x).getPersonAtIndex(y));
        }
        repeat = false;
      }
    }
    return groupmates;
  }

    /** Marks this Person as no longer being a member of the Group passed.
     * Should <i>not</i> be called in isolation, else the Group object will
     * still think the Person is a member! See {@link
     * edu.umw.cpsc.collegesim.Group#removeEveryoneFromGroup()}.
     */
    public void leaveGroup(Group g){
        for(int x = 0; x<groups.size(); x++){
          if(groups.get(x).getID( ) == g.getID( )){
              //MONTY PYTHON'S HOLY GRAIL RIGHT HERE
              groups.get(x).removeStudent(this);
            groups.remove(x);
          }
        }
      }

    /** Sets the school year (1=freshman, 2=sophomore, etc.) of this
     * Person. No validation checking is performed. */
  public void setYear(int x){
    year = x;
    //store initial attributes
    if(year==1){
      preferencesYear0=new ArrayList<Double>(preferences);
      hobbiesYear0=new ArrayList<Double>(hobbies);
    }else if(year==2){
      preferencesYear1=new ArrayList<Double>(preferences);
      hobbiesYear1=new ArrayList<Double>(hobbies);
    }else if(year==3){
      preferencesYear2=new ArrayList<Double>(preferences);
      hobbiesYear2=new ArrayList<Double>(hobbies);
    }else if(year==4){
      preferencesYear3=new ArrayList<Double>(preferences);
      hobbiesYear3=new ArrayList<Double>(hobbies);
    }
  }

    /** Gets the school year (1=freshman, 2=sophomore, etc.) of this
     * Person.  */
  public int getYear(){
    return year;
  }

    /** Increments the school year (1=freshman, 2=sophomore, etc.) of this
     * Person, possibly to 5 or higher (no validation checking is
     * performed). */
  public void incrementYear(){
    if(year==1){
      preferencesYear1=new ArrayList<Double>(preferences);
      hobbiesYear1=new ArrayList<Double>(hobbies);
    }else if(year==2){
      preferencesYear2=new ArrayList<Double>(preferences);
      hobbiesYear2=new ArrayList<Double>(hobbies);
    }else if(year==3){
      preferencesYear3=new ArrayList<Double>(preferences);
      hobbiesYear3=new ArrayList<Double>(hobbies);
    }
    year++;
  }

  public boolean hasFullData(){
    if(preferencesYear0!=null&&preferencesYear1!=null&&preferencesYear3!=null&&preferences!=null&&hobbiesYear0!=null&&hobbiesYear1!=null&&hobbiesYear3!=null&&hobbies!=null){
      return true;
    }else{
      return false;
    }
  }

    private void makeFriends(Person newFriend) {
        Sim.peopleGraph.addEdge(this, newFriend, 1);
        refreshLastTickleTime(newFriend.id);
        newFriend.refreshLastTickleTime(id);
    }

    void forceAddRandomOppRaceFriend() {
        Bag peopleBag = Sim.peopleGraph.getAllNodes( );
        Person forcedFriend;
        do{
          forcedFriend = (Person) 
              peopleBag.get(Sim.instance( ).random.nextInt(peopleBag.size( )));
        }while(forcedFriend.id == id  || 
            forcedFriend.race == race  ||
            friendsWith(forcedFriend));
        this.makeFriends(forcedFriend);
        System.out.println("Student " + id + " (" + race + ") " +
            "forced with student " + forcedFriend.id + " (" +
            forcedFriend.race + ")");
    }
}
