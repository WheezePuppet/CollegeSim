vals = read.table("C:/Users/Morgan/Desktop/IS/NewIS/output.txt")

#make a histogram of "social alienation factor," which is 100 minus the number of friendships
#the number of friendships for each person is the second column

saf = 100-vals[,2]
safmean = mean(saf)
safvar = var(saf)

hist(saf,xlab="Social Alienation Factor",main="Frequency of Social Alienation Levels")

print(c("Mean:",safmean))
print(c("Variance:",safvar))