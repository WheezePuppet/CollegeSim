vals = read.table("C:/Users/Morgan/Desktop/IS/NewIS/output.txt")

#display a histogram of SAF by race

safWhite = vector( )
safMin = vector( )

for(i in 1:nrow(vals)){
	calc = 100-vals[i,2]
	if(vals[i,4] == "WHITE"){
		safWhite = c(safWhite,calc)
	}else{
		safMin = c(safMin, calc)
	} 
}

safmeanW = mean(safWhite)
safmeanM = mean(safMin)
safvarW = var(safWhite)
safvarM = var(safMin)

print(c("Mean (W):",safmeanW))
print(c("Variance (W):",safvarW))
print(c("Mean (M):",safmeanM))
print(c("Variance (M):",safvarM))

#Right now I have it overwriting the white plot with the minority plot
#need to wrangle it so it displays both histograms on one image
hist(safWhite,xlab="Social Alienation Factor",main="SAF for White Students")
hist(safMin,xlab="Social Alienation Factor",main="SAF for Minority Students")