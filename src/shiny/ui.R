
library(shiny)
library(shinyIncubator)

# CHANGE: The title of your simulation webapp.
APP.TITLE <- "CollegeSim"


shinyUI(fluidPage(

    tags$head(tags$link(rel="stylesheet", type="text/css",
        href="shinysim.css")),

    progressInit(),

    headerPanel(APP.TITLE),

    sidebarPanel(
        h3("Simulation parameters"),
        fluidRow(

            # xCHANGE: Insert input widgets for each simulation parameter you
            # want to be able to change through the Web UI. "Numeric
            # multiplier" is a sample.
            sliderInput("raceWeight",
                label="Race weight (in # of equivalent attributes)",value=400,
                min=0,max=500),
            sliderInput("probWhite",
                label="White student body proportion",value=.85,
                min=0,max=1,step=0.05),
            sliderInput("driftRate",
                label="Drift rate (prob of changing attribute)",
                value=.0, min=0,max=1,step=0.05),
            HTML("<div class=morgan>decay rate (Morgan?)</div>"),

            hr(),

            radioButtons("seedType",label="",
                choices=c("Random seed"="rand",
                    "Specific seed"="specific"),
                selected="rand",
                inline=TRUE),
            conditionalPanel(condition="input.seedType == 'specific'",
                numericInput("seed","Seed",value=0)),

            # OPTIONAL: Only include this if it makes sense for the user to be
            # able to tweak the length of the simulation.
            numericInput("maxTime","Number of sim years",
                value=8,min=1,step=1),
            actionButton("runsim",label="Run sim"),
            htmlOutput("log"),

            hr(),

            checkboxInput("showUninterestingParameters",
                label="Show uninteresting parameters", value=FALSE),
            conditionalPanel(
                condition="input.showUninterestingParameters == true",
                div(class="inputCategory",
                    HTML("Students:<br/>"),
                    sliderInput("initNumPeople",
                        label="Initial number of students",value=100,
                        min=0,max=4000),
                    sliderInput("numFreshmenPerYear",
                        label="Number of new freshmen per year",value=25,
                        min=0,max=1000)
                ),
                div(class="inputCategory",
                    HTML("Groups:<br/>"),
                    sliderInput("initNumGroups",
                        label="Initial number of groups",value=50,
                        min=0,max=200),
                    sliderInput("numNewGroupsPerYear",
                        label="Number of new groups created per year",value=10,
                        min=0,max=10)
                ),
                div(class="inputCategory",
                    HTML("Dropout rate:<br/>"),
                    sliderInput("dropoutRate",
                        label="Dropout Rate",value=0.01,
                        min=0,max=.2,step=0.005),
                    sliderInput("dropoutIntercept",
                        label="Dropout Intercept",value=0.05,
                        min=0,max=.2,step=0.005)
                )
            )
        )
    ),

    mainPanel(
        # CHANGE: Insert output widgets for each type of analysis. Here we
        # just have two plots (the second of which isn't even set to anything
        # in server.R) as placeholders.
        tabsetPanel(
            tabPanel("Time series",
                plotOutput("friendshipsPlot"),
                plotOutput("dropoutPlot")
            )
        )
    )
))
