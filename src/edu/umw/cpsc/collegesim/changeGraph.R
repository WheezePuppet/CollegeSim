 library(igraph)

people = read.table("averageChange.csv")
names(people)=c("ID","extro", "numF","numG","avgDepChg", "avgIndChg")
#hist(people$avgIndChg,breaks=seq(0,0.2,0.01))
#hist(people$avgDepChg,breaks=seq(0,1.5,0.1))

#plot(people$numF,people$avgDepChg)
#abline(lm(people$avgDepChg~people$numF))
#summary(lm(people$avgDepChg~people$numF))

#plot(people$avgDepChg,people$avgIndChg)
#abline(lm(people$avgIndChg~people$avgDepChg))
#summary(lm(people$avgIndChg~people$avgDepChg))

plot(people$extro,people$avgIndChg)
plot(people$extro,people$avgDepChg)