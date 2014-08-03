
library(igraph)

#MAKE SURE YOU CHOOSE AN EDGE FILE
cat("Enter an edge file name:\n")
edf = read.table(file.choose( ),sep=",",stringsAsFactors=FALSE)
#MAKE SURE YOU CHOOSE THE CORRESPONDING YEAR FILE
cat("Enter the corresponding year file name:\n")
vdf = read.table(file.choose( ),sep=",",stringsAsFactors=FALSE)

#Find a list of rows where a friendship is duplicated
rows = vector( )
#for all the entries in the edge graph
for(i in 1:length(edf$V1)){
	#for each friendship
	firstID = edf$V1[i]
	friendID = edf$V2[i]
	#look through the rest of the friendships
	for(j in i:length(edf$V1)){
		#if the same friendship is listed in reverse order
		if(edf$V1[j] == friendID && edf$V2[j] == firstID){
			#add that row to the list of rows to remove
			rows <- c(rows, j)
		}
	}
}

#Create a new data frame
nedf = data.frame(V1=rep(NA, 1), V2=rep(NA,1), stringsAsFactors=FALSE)
index = 1
#For each of the edges
for(i in 1:length(edf$V1)){
	repeatF = 0
	#Look at the list of rows we do not want to include
	for(j in 1:length(rows)){
		#If one of those rows is the same as what we are looking at
		if(rows[j] == i){
			#Set that it is a repeat
			repeatF = 1
			j = length(rows) + 1
		}
	}
	#If this edge is not a repeat
	if(repeatF == 0){
		#Add it to the new data frame
		nedf[index,] <- c(edf$V1[i],edf$V2[i])
		index = index + 1
	}
}

g = graph.data.frame(nedf,directed=F,vertices=vdf)

col = vector(length = length(vdf$V4))
for(i in 1:length(vdf$V4)){
	if(vdf$V4[i] == "WHITE"){
		col[i] = "blue"
	}else{
		col[i] = "red"
	}
}
V(g)$color <- col
plot(g)
