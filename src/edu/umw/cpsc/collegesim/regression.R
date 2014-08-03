vals = read.table("C:/Users/Morgan/Desktop/IS/NewIS/output.txt")

#run a regression of SAF vs. introversion level and calculate
#significance (The lm() function in R does this.) Also
#scatterplot this.

plot(lm((100-vals[,2]) ~ vals[,6]))

#I'm not sure this is what you wanted but this is what I have D: