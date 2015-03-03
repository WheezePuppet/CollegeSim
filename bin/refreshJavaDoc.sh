touch /tmp/doingcollegesim
cd /home/stephen/CollegeSim/src/edu/umw/cpsc/collegesim
git pull
mkdir /tmp/classes
javac -cp /home/stephen/CollegeSim/lib/mason.17.jar -d /tmp/classes *.java
javadoc -cp /home/stephen/CollegeSim/src:/home/stephen/CollegeSim/lib/mason.17.jar  -d ~/public_html/CollegeSimJavaDoc edu.umw.cpsc.collegesim

