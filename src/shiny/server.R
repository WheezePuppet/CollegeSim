
# Shiny Sim. A template for displaying an analysis of a Java simulation (often
# agent-based) in a Shiny Web-based interface.
#
# Stephen Davies, Ph.D. -- University of Mary Washington
# 10/5/2014
#
library(shiny)
library(shinyIncubator)
library(dplyr)
library(ggplot2)


# -------------------------------- Constants ---------------------------------
SIM.FILES.BASE.DIR <- "/tmp"

# xCHANGE: The full path of your project directory. Any .java file that appears
# in this directory hierarchy will be compiled as part of the simulation.
SOURCE.DIR <- "/home/stephen/research/diversity/CollegeSim"

CLASSES.DIR <- "/tmp/classes"

OUTPUT.FILE <- paste0(SIM.FILES.BASE.DIR,"/","stdoutSIMTAG.csv")

PEOPLE.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","peopleSIMTAG.csv")

DROPOUT.STATS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","dropoutSIMTAG.csv")

SIM.PARAMS.FILE <- paste0(SIM.FILES.BASE.DIR,"/","sim_paramsSIMTAG.txt")

# xCHANGE: The package/classname of the main() Java class in your sim.
SIM.CLASS.NAME <- "edu.umw.cpsc.collegesim.Sim"

JAVA.RUN.TIME.OPTIONS <- ""

# OPTIONAL: The rate (number of milliseconds between refreshes) at which the
# web app will read the simulator's output file for progress to update plots
# and such.
REFRESH.PERIOD.MILLIS <- 500

# OPTIONAL: Any Java libraries needed by the simulation.
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

    # Return a data frame containing the most recent contents of the 
    # PEOPLE.STATS.FILE.
    people.stats <- function() {
ps <<- parse.stats.df(PEOPLE.STATS.FILE)
        return(parse.stats.df(PEOPLE.STATS.FILE))
    }


    # Return a data frame containing the most recent contents of the 
    # DROPOUT.STATS.FILE.
    dropout.stats <- function() {
dr <<- parse.stats.df(DROPOUT.STATS.FILE)
        return(parse.stats.df(DROPOUT.STATS.FILE))
    }

    parse.stats.df <- function(filename.template) {
        if (!file.exists(sub("SIMTAG",simtag,filename.template))) {
            return(data.frame())
        }
        tryCatch({
            # Change the colClasses argument here, if desired, to control the
            # classes used for each of the data columns.
            read.csv(sub("SIMTAG",simtag,filename.template),header=TRUE,
                colClasses=classes.for.person.output.lines)
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
                ifelse(input$seedType=="specific",
                                            paste("-seed",input$seed),
                                            ""),
                ">",sub("SIMTAG",simtag,OUTPUT.FILE),"&"))
        })
    }


    # CHANGE: put any graphics commands to produce a visual analysis of the
    # simulation's output here.
    output$friendshipsPlot <- renderPlot({
        if (input$runsim < 1) return(NULL)
        people.stats.df <- people.stats()
        if (nrow(people.stats.df) > 0) {
            by.race.by.year <- group_by(people.stats.df,period,race) %>%
                dplyr::summarize(avgFriends=mean(numFriends)) %>%
                filter(!is.na(avgFriends))
            the.plot <- ggplot(by.race.by.year,
                aes(x=period,y=avgFriends,col=race)) + 
                geom_line() + 
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) +
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
        if (nrow(dropout.stats.df) > 0) {
            people.by.race.by.year <- 
                group_by(people.stats.df,period,race) %>%
                summarize(p.count=n())
            dropout.by.race.by.year <- 
                group_by(dropout.stats.df,period,race) %>%
                summarize(d.count=n())
            dropout.data <- inner_join(people.by.race.by.year,
                    dropout.by.race.by.year,
                by=c("period","race")) %>%
                mutate(percDropouts=d.count/(p.count+d.count)*100)
            the.plot <- ggplot(dropout.data,
                aes(x=period,y=percDropouts,col=race)) + 
                geom_line() + 
                scale_x_continuous(limits=c(0,isolate(input$maxTime)-1),
                                    breaks=0:isolate(input$maxTime)-1) +
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


    # Nuke any sims that are still currently running.
    kill.all.sims <- function() {
        system(paste("pkill -f",SIM.CLASS.NAME))
    }
})
