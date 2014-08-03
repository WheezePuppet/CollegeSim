vals = read.table("C:/Users/Morgan/Desktop/IS/NewIS/output.txt")

#make a histogram of number of groups

numG = vals[,3]
gMean = mean(numG)
gVar = var(numG)

hist(numG,xlab="Number of Groups",main="Number of Groups")

print(c("Mean:",gMean))
print(c("Variance:",gVar))