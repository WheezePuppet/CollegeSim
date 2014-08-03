vals = read.table("C:/Users/Morgan/Desktop/IS/NewIS/output.txt")

#display a histogram of number of groups by race

gWhite = vector( )
gMin = vector( )

for(i in 1:nrow(vals)){
	if(vals[i,4] == "WHITE"){
		gWhite = c(gWhite,vals[i,3])
	}else{
		gMin = c(gMin, vals[i,3])
	} 
}

gMeanW = mean(gWhite)
gMeanM = mean(gMin)
gVarW = var(gWhite)
gVarM = var(gMin)

print(c("Mean (W):",gMeanW))
print(c("Variance (W):",gVarW))
print(c("Mean (M):",gMeanM))
print(c("Variance (M):",gVarM))

#Right now I have it overwriting the white plot with the minority plot
#need to wrangle it so it displays both histograms on one image
hist(gWhite,xlab="Number of Groups",main="Number of Groups for White Students")
hist(gMin,xlab="Number of Groups",main="Number of Groups for Minority Students")