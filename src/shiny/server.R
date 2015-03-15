
# Shiny Sim. A template for displaying an analysis of a Java simulation (often
# agent-based) in a Shiny Web-based interface.
#
# Stephen Davies, Ph.D. -- University of Mary Washington
# 10/5/2014
#
library(shiny)
library(shinyIncubator)
library(tidyr)
library(dplyr)
library(ggplot2)


# -------------------------------- Constants ---------------------------------
SIM.FILES.BASE.DIR <- "/tmp"

SOURCE.DIR <- "/home/stephen/research/diversity/CollegeSim"

CLASSES.DIR <- "/tmp/classes"

OUTPUT.FILE <- paste0(SIM.FILES.BASE.DIR,"/","stdoutSIMTAG.txt")

PEOPLE.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","peopleSIMTAG.csv")

FRIENDSHIPS.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","friendshipsSIMTAG.csv")

ENCOUNTERS.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","encountersSIMTAG.csv")

GROUPS.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","groupsSIMTAG.csv")

SIMILARITY.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","similaritySIMTAG.csv")

DROPOUT.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","dropoutSIMTAG.csv")

SIM.PARAMS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","sim_paramsSIMTAG.txt")

SIM.CLASS.NAME <- "edu.umw.cpsc.collegesim.Sim"

JAVA.RUN.TIME.OPTIONS <- ""

REFRESH.PERIOD.MILLIS <- 500

LIBS <- c("mason.17.jar")

CLASSPATH <- paste(
    paste(SOURCE.DIR,"lib",LIBS,sep="/",collapse=":"),
    CLASSES.DIR,sep=":")


# ------------------------- The Shiny Sim server -----------------------------
shinyServer(function(input,output,session) {

    sim.started <- FALSE
    progress <- NULL
    simtag <- 0     # A hashtag we'll create for each particular sim run.
    params <- NULL

    classes.for.person.output.lines <- 
        c(rep("integer",4),rep("factor",2),"numeric","integer")

    classes.for.friendship.lines <- rep("integer",3)

    classes.for.encounters.lines <- c(rep("integer",3),"factor")

    classes.for.groups.lines <- c(rep("integer",4))

    classes.for.similarity.lines <- c("integer","factor","double","logical")

    # Return a data frame containing the most recent contents of the 
    # PEOPLE.STATS.FILE.
    people.stats <- function() {
ps <<- parse.stats.df(PEOPLE.STATS.FILE, classes.for.person.output.lines)
        return(parse.stats.df(PEOPLE.STATS.FILE,
            classes.for.person.output.lines))
    }


    # Return a data frame containing the most recent contents of the 
    # DROPOUT.STATS.FILE.
    dropout.stats <- function() {
dr <<- parse.stats.df(DROPOUT.STATS.FILE, classes.for.person.output.lines)
        return(parse.stats.df(DROPOUT.STATS.FILE,
            classes.for.person.output.lines))
    }

    # Return a data frame containing the most recent contents of the 
    # FRIENDSHIPS.STATS.FILE.
    friendship.stats <- function() {
fr <<- parse.stats.df(FRIENDSHIPS.STATS.FILE, classes.for.friendship.lines)
        return(parse.stats.df(FRIENDSHIPS.STATS.FILE,
            classes.for.friendship.lines))
    }

    # Return a data frame containing the most recent contents of the 
    # ENCOUNTERS.STATS.FILE.
    encounters.stats <- function() {
en <<- parse.stats.df(ENCOUNTERS.STATS.FILE, classes.for.encounters.lines)
        return(parse.stats.df(ENCOUNTERS.STATS.FILE,
            classes.for.encounters.lines))
    }

    # Return a data frame containing the most recent contents of the 
    # GROUPS.STATS.FILE.
    groups.stats <- function() {
gr <<- parse.stats.df(GROUPS.STATS.FILE, classes.for.groups.lines)
        return(parse.stats.df(GROUPS.STATS.FILE,
            classes.for.groups.lines))
    }

    # Return a data frame containing the most recent contents of the 
    # SIMILARITY.STATS.FILE.
    similarity.stats <- function() {
si <<- parse.stats.df(SIMILARITY.STATS.FILE, classes.for.similarity.lines)
        return(parse.stats.df(SIMILARITY.STATS.FILE,
            classes.for.similarity.lines))
    }

    parse.stats.df <- function(filename.template, classes.list) {
        if (!file.exists(sub("SIMTAG",simtag,filename.template))) {
            return(data.frame())
        }
        tryCatch({
            # Change the colClasses argument here, if desired, to control the
            # classes used for each of the data columns.
            read.csv(sub("SIMTAG",simtag,filename.template),header=TRUE,
                colClasses=classes.list)
        },error = function(e) return(data.frame())
        )
    }


    # Return the seed, as recorded in the SIM.PARAMS.FILE.
    seed <- function() {
        get.param("seed")
    }
    

    # Return the value of the named parameter, as recorded in the 
    # SIM.PARAMS.FILE. Often (but not always) this will have been passed to
    # the sim via command-line argument, after being retrieved from the UI.
    get.param <- function(param.name) {
    
        if (!file.exists(sub("SIMTAG",simtag,SIM.PARAMS.FILE))) {
            return(NA)
        }
        if (is.null(params)) {
            #
            # seed=4592
            # maxTime=100
            # trialNumber=1
            # raceWeight=12
            #
            the.df <- read.table(sub("SIMTAG",simtag,SIM.PARAMS.FILE),
                header=FALSE,sep="=",stringsAsFactors=FALSE)
            params <<- setNames(the.df[[2]],the.df[[1]])
        }
        
        return(params[[param.name]])
    }


    # Shiny Observer to start the simulation when the button is pressed.
    observe({
        if (input$runsim < 1) return(NULL)

        isolate({
            maxTime <- input$maxTime
            if (!sim.started) {
                simtag <<- ceiling(runif(1,1,1e8))
                cat("Starting sim",simtag,"\n")
                progress <<- Progress$new(session,min=0,max=maxTime+1)
                progress$set(message="Launching simulation...",value=0)
                start.sim(input,simtag)
                progress$set(message="Initializing simulation...",value=1)
                sim.started <<- TRUE
            }
        })

        output$log <- renderText(HTML(paste0("<b>Log output:</b><br/>",
            "sim #",simtag,"<br/>",
            "seed: ",seed(),"<br/>")))

        people.stats.df <- people.stats()
        if (nrow(people.stats.df) > 0) {
            progress$set("Running simulation...",
                detail=paste(max(people.stats.df$period)+1,"of",maxTime,
                    "years"),
                value=max(people.stats.df$period)+1)
            if (max(people.stats.df$period) == maxTime-1) {
                progress$set("Done.",value=1+maxTime)
                sim.started <<- FALSE
                progress$close()
            } else {
                # If the simulation is running, but not finished, check
                # its progress again in a little bit.
                invalidateLater(REFRESH.PERIOD.MILLIS,session)
            }
        } else {
            # If the simulation isn't running yet, check its progress 
            # again in a little bit.
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })


    # Start a new instance of the Java simulation.
    start.sim <- function(input,simtag) {
        params <<- NULL
        isolate({
            if (!file.exists(
                    paste(SOURCE.DIR,
                        gsub("\\.","/",SIM.CLASS.NAME),
                        sep="/"))) {
                if (!file.exists(CLASSES.DIR)) {
                    system(paste("mkdir",CLASSES.DIR))
                }
                system(paste("find",SOURCE.DIR,"-name \"*.java\" ",
                    "> /tmp/javasourcefiles.txt"))
                system(paste("javac -d",CLASSES.DIR,
                    "-cp",CLASSPATH,"@/tmp/javasourcefiles.txt"))
                system("rm /tmp/javasourcefiles.txt")
            }
            setwd(SIM.FILES.BASE.DIR)
            system(paste("nice java -classpath ",CLASSPATH,
                JAVA.RUN.TIME.OPTIONS,SIM.CLASS.NAME,
                "-maxTime",input$maxTime,
                "-simtag",simtag,
                "-raceWeight",input$raceWeight,
                "-probWhite",input$probWhite,
                "-initNumPeople",input$initNumPeople,
                "-initNumGroups",input$initNumGroups,
                "-numNewGroupsPerYear",input$numNewGroupsPerYear,
                "-numFreshmenPerYear",input$numFreshmenPerYear,
                "-driftRate",input$driftRate,
                "-dropoutRate",input$dropoutRate,
                "-dropoutIntercept",input$dropoutIntercept,
                "-numToMeetPop",input$numToMeetPop,
                "-numToMeetGroup",input$numToMeetGroup,
                "-decayThreshold",input$decayThreshold,
                "-friendshipCoefficient",input$friendshipCoefficient,
                "-friendshipIntercept",input$friendshipIntercept,
                "-numIndepAttrs",input$numIndepAttrs,
                "-numDepAttrs",input$numDepAttrs,
                ifelse(input$forceBiracialFriendships=="on",
                    paste("-initNumForcedOppRaceFriends",
                        input$numForcedFriendships),""),
                ifelse(input$seedType=="specific",
                                            paste("-seed",input$seed),
                                            ""),
                ">",sub("SIMTAG",simtag,OUTPUT.FILE),"&"))
        })
    }


    output$friendshipsPlot <- renderPlot({
        if (input$runsim < 1) return(NULL)
        people.stats.df <- people.stats()
        if (nrow(people.stats.df) > 0) {
            by.race.by.year <- group_by(people.stats.df,period,race) %>%
                dplyr::summarize(avgFriends=mean(numFriends)) %>%
                filter(!is.na(avgFriends))
            the.plot <- ggplot(by.race.by.year,
                aes(x=period,y=avgFriends,col=race)) + 
                geom_line(size=1.2) + 
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) + 
                scale_color_manual(values=c("MINORITY"="brown","WHITE"="blue")) +
                expand_limits(y=0) +
                labs(title="Average number of friends by race",
                    x="Simulation year",
                    y="Mean number of friends")
            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })

    output$dropoutPlot <- renderPlot({
        if (input$runsim < 1) return(NULL)
        dropout.stats.df <- dropout.stats()
        people.stats.df <- people.stats()
        if (nrow(dropout.stats.df) > 0 &&
            nrow(people.stats.df) > 0) {
            people.by.race.by.year <- 
                group_by(people.stats.df,period,race) %>%
                summarize(p.count=n())
            dropout.by.race.by.year <- 
                group_by(dropout.stats.df,period,race) %>%
                summarize(d.count=n())
            for (year in 0:(max(dropout.stats.df$period))) {
                for (race in levels(dropout.by.race.by.year$race)) {
                    if (nrow(dropout.by.race.by.year[
                        dropout.by.race.by.year$period == year &
                        dropout.by.race.by.year$race == race,]) == 0) {
                        dropout.by.race.by.year <- rbind(
                            dropout.by.race.by.year, 
                            data.frame(period=year, race=race, d.count=0))
                    }
                }
            }
            dropout.data <- inner_join(people.by.race.by.year,
                    dropout.by.race.by.year,
                by=c("period","race")) %>%
                mutate(percDropouts=d.count/(p.count+d.count)*100)
            the.plot <- ggplot(dropout.data,
                aes(x=period,y=percDropouts,col=race)) + 
                geom_line() + 
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) +
                scale_color_manual(values=c("MINORITY"="brown","WHITE"="blue")) +
                expand_limits(y=0) +
                labs(title="Dropout rate",
                    x="Simulation year",
                    y="Percentage dropouts")
            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })

    output$interracialRelationshipsPlot <- renderPlot({
        if (input$runsim < 1) return(NULL)
        friendships.stats.df <- friendship.stats()
        people.stats.df <- people.stats()
        if (nrow(people.stats.df) > 0) {
            friendships <- inner_join(friendships.stats.df,
                select(people.stats.df,period,id,race),by=c("period","id"))
            friendships <- inner_join(friendships,people.stats.df,
                by=c("period"="period","friendId"="id")) %>%
                select(period,id,friendId,race.x,race.y)

            # friendships is now this data frame:
            #  period id friendId race.x race.y
            #1      0  0       66  WHITE  WHITE
            #2      0  0       10  WHITE  WHITE
            #3      0  0        7  WHITE  WHITE

            friendships.by.race <- 
                group_by(friendships,period,race.x,race.y) %>%
                dplyr::summarize(numRels=n())
            class(friendships.by.race) <- "data.frame"
            minority.with.minority.rels <- 
                filter(friendships.by.race,race.x=="MINORITY",
                    race.y=="MINORITY") %>%
                select(period,mm.rels=numRels)
            minority.with.white.rels <- 
                filter(friendships.by.race,race.x=="WHITE",
                    race.y=="MINORITY") %>%
                select(period,mw.rels=numRels)
            rels.df <- 
                inner_join(minority.with.white.rels, 
                    minority.with.minority.rels,
                    by=c("period")) %>%
                mutate(perc.mw.rels=100*mw.rels/(mm.rels+mw.rels))
            # "perc.mw.rels" now represents, for each year, the percentage of
            # total relationships that minorities have (with anybody) that are
            # with whites.

            the.plot <- ggplot(rels.df %>% 
                    gather(measure, value, mw.rels:perc.mw.rels),
                aes(x=period, y=value, color=measure)) + 
                geom_line(size=1.2) +
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) +
                scale_color_manual(name="",
                     breaks=c("mm.rels", "mw.rels", "perc.mw.rels"),
                     labels=c("# min-min fships",
                        "# min-whi fships", 
                        "% that are min-whi"),
                    values=c("mm.rels"="brown","mw.rels"="purple",
                        "perc.mw.rels"="darkgrey")) +
                expand_limits(y=0) +
                geom_hline(yintercept=100,linetype="dotted",color="black") +
                annotate("text", x=0, y=100+5, hjust=0, size=4,
                    label="max") +
                geom_hline(yintercept=input$probWhite*100,linetype="dashed",
                    color="blue") +
                annotate("text", x=0, y=input$probWhite*100-5, hjust=0, size=4,
                    label="expected proportion") +
                labs(title="Racial composition of minorities' friendships",
                    x="Simulation year", y="")
            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }

    })

    output$encountersPlot <- renderPlot({

        if (input$runsim < 1) return(NULL)

        encounters.stats.df <- encounters.stats()
        people.stats.df <- people.stats()

        if (nrow(encounters.stats.df) > 0 &&
            nrow(people.stats.df) > 0) {

            num.students.df <- people.stats.df %>% 
                group_by(period,race) %>% summarize(numStudents=n())

            x <- inner_join(encounters.stats.df,people.stats.df,
                    by=c("id1"="id")) %>%
                select(year,type,race)
            encounter.types <- group_by(x,year,race,type) %>%
                dplyr::summarize(numEvents=n())
            encounter.types <- inner_join(num.students.df,encounter.types,
                by=c("period"="year","race"="race"))

            # encounter.types now looks like this:
            #  period     race numStudents          type numEvents
            #1      0 MINORITY          21         decay       359
            #2      0 MINORITY          21   meetFriends       714
            # ...

            the.plot <- ggplot(encounter.types, 
                aes(x=period,y=numEvents/numStudents)) +
                facet_grid(race ~ .) +
                geom_line(aes(color=type,linetype=type,group=type),size=1.2) +
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) +
                scale_color_manual(name="",
                     breaks=c("tickle","meetFriends","meetNoFriends","decay"),
                     labels=c("tickles",
                        "new friendships",
                        "rejected friendships",
                        "decayed friendships"),
                    values=c("tickle"="orange","meetFriends"="darkgreen",
                        "meetNoFriends"="red","decay"="black")) + 
                scale_linetype_manual(name="",
                     breaks=c("tickle","meetFriends","meetNoFriends","decay"),
                     labels=c("tickles",
                        "new friendships",
                        "rejected friendships",
                        "decayed friendships"),
                    values=c("tickle"="dotted","meetFriends"="solid",
                        "meetNoFriends"="dashed","decay"="dashed")) + 
                expand_limits(y=0) +
                labs(title="Encounter/decay events per student",
                    x="Simulation year", y="Events per student")
            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }

    })

    output$currentGroupsPlot <- renderPlot({

        if (input$runsim < 1) return(NULL)

        groups.stats.df <- groups.stats()

        if (nrow(groups.stats.df) > 0) {

            last.full.year <- max(groups.stats.df$year) - 1

            if (last.full.year >= 1) {

                groups.stats.df <- 
                    filter(groups.stats.df, year==last.full.year)

                groups.stats.df <- groups.stats.df %>%
                    gather(measure, value, numMin, numWhi)

                the.plot <- ggplot(groups.stats.df) +
                    geom_bar(aes(x=id,fill=measure,y=value),stat="identity") +
                    expand_limits(y=0) +
                    scale_fill_manual(name="",
                          breaks=c("numMin","numWhi"),
                          labels=c("num minorities",
                             "num whites"),
                         values=c("numMin"="brown","numWhi"="blue")) +
                     labs(title=paste(
                        "Group composition: year",last.full.year),
                         x="Group ID", y="")

                print(the.plot)
            }
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })

    output$groupsHistoryPlot <- renderPlot({

        if (input$runsim < 1) return(NULL)

        groups.stats.df <- groups.stats()

        if (nrow(groups.stats.df) > 0) {

            groups.stats.df$numTot <- 
                groups.stats.df$numMin + groups.stats.df$numWhi
            summary.group.stats.df <- groups.stats.df %>%
                group_by(year) %>%
                summarize(meanMin=mean(numMin),
                          meanWhi=mean(numWhi),
                          meanTot=mean(numTot))

            summary.group.stats.df <- summary.group.stats.df %>%
                gather(measure, value, meanMin:meanTot)
            the.plot <- ggplot(summary.group.stats.df) +
                geom_line(aes(x=year,group=measure,col=measure,y=value),size=1.2) +
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) +
                expand_limits(y=0) +
                scale_color_manual(name="",
                     breaks=c("meanMin","meanWhi","meanTot"),
                     labels=c("avg # min",
                        "avg # whi",
                        "avg total size"),
                    values=c("meanMin"="brown","meanWhi"="blue",
                        "meanTot"="darkgrey")) + 
                labs(title="Group composition",
                    x="Simulation year", y="")

            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })

    output$numGroupsPlot <- renderPlot({

        if (input$runsim < 1) return(NULL)

        groups.stats.df <- groups.stats()

        if (nrow(groups.stats.df) > 0) {

            groups.stats.df$numTot <- 
                groups.stats.df$numMin + groups.stats.df$numWhi
            summary.group.stats.df <- groups.stats.df %>%
                group_by(year) %>%
                summarize(numGrp=n())

            the.plot <- ggplot(summary.group.stats.df) +
                geom_line(aes(x=year,y=numGrp),col="darkgrey",size=1.2) +
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) +
                expand_limits(y=0) +
                labs(title="Number of groups",
                    x="Simulation year", y="")

            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })

    output$similarityPlot <- renderPlot({

        if (input$runsim < 1) return(NULL)

        similarity.stats.df <- similarity.stats()

        if (nrow(similarity.stats.df) > 0) {

            the.plot <- ggplot(similarity.stats.df) +
                geom_boxplot(aes(y=similarity,x=races,fill=races))+
                scale_fill_manual(values=c("MINORITY"="brown",
                    "MIXED"="grey","WHITE"="blue"),
                          breaks=c("MINORITY","MIXED","WHITE"),
                          labels=c("min-min",
                             "min-whi","whi-whi")) +
                scale_x_discrete(labels=c("minority-minority encounters",
                    "minority-white encounters","white-white encounters")) +
                expand_limits(y=c(0,1)) +
                labs(title="Perceived similarity upon encountering")

            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })

    output$similarityImpactPlot <- renderPlot({

        if (input$runsim < 1) return(NULL)

        similarity.stats.df <- similarity.stats()

        if (nrow(similarity.stats.df) > 0) {

            x <- similarity.stats.df %>%
                group_by(races,becameFriends) %>% 
                summarize(num=n())
            fractions <- sapply(levels(x$races), function(race) { 
                x$num[x$races==race & x$becameFriends] / 
                    sum(x[x$races==race,"num"]) 
            })

            the.plot <- ggplot(x,aes(y=num,x=races)) +
                geom_bar(aes(fill=becameFriends),
                    stat="identity") +
                scale_fill_manual(values=c("TRUE"="darkgreen","FALSE"="red"),
                          breaks=c("MINORITY","MIXED","WHITE"),
                          labels=c("min-min",
                             "min-whi","whi-whi")) +
                scale_x_discrete(labels=c("minority-minority encounters",
                    "minority-white encounters","white-white encounters")) +
                annotate("text",x=1:3,label=paste0(round(fractions*100,2),"%"),
                    y=Inf, vjust=1.5, size=6) +
                labs(title="Fraction of encounters leading to friendship")

            print(the.plot)
        }
        # Recreate this plot in a little bit.
        if (sim.started) {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
        }
    })


    # Nuke any sims that are still currently running.
    kill.all.sims <- function() {
        system(paste("pkill -f",SIM.CLASS.NAME))
    }
})
