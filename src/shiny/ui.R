
library(shiny)
library(shinyIncubator)

APP.TITLE <- "CollegeSim"


shinyUI(fluidPage(

    tags$head(tags$link(rel="stylesheet", type="text/css",
        href="shinysim.css")),

    #progressInit(),

    headerPanel(APP.TITLE),

    sidebarPanel(
        h3("Parameters"), 
        div(id="runsimstuff",
            flowLayout(
                numericInput("maxTime","Number of sim years",
                    value=20,min=1,step=1),
                actionButton("runsim",label="Run sim")
            )
        ),
        fluidRow(
            div(class="inputCategory",
                HTML("Attributes:<br/>"),
                sliderInput("raceWeight",
                    label="Race weight (in # of equivalent attributes)",
                        value=40,min=0,max=100),
                    sliderInput("numPreferences",
                        label="Size of preference pool (and # prefs/student)",
                        value=20,min=0,max=100),
                    sliderInput("numHobbies",
                        label="Size of hobby pool (and # hobbies/student)",
                        value=20,min=0,max=100)
            ),
            div(class="inputCategory",
                HTML("Similarity &rarr; friendship equation <i>(y=mx+b)</i>:<br/>"),
                sliderInput("friendshipCoefficient",
                    label="Coefficient",value=.22,
                    min=0,max=1.5,step=0.01),
                sliderInput("friendshipIntercept",
                    label="Intercept",value=.05,
                    min=-.5,max=.5,step=0.01)
            ),
            div(class="inputCategory",
                HTML("Policies:<br/>"),
                radioButtons("forceBiracialFriendships",
                    label="Force initial biracial friendships",
                    choices=c("On"="on",
                        "Off"="off"),
                    selected="off",
                    inline=TRUE),
                conditionalPanel(
                    condition="input.forceBiracialFriendships == 'on'",
                    sliderInput("numForcedFriendships",
                        label="Number of forced friendships",
                        min=0,max=20,value=5)),
                hr(),
                radioButtons("oGroups",
                    label="Orientation groups",
                    choices=c("On"="on",
                        "Off"="off"),
                    selected="off",
                    inline=TRUE),
                conditionalPanel(
                    condition="input.oGroups == 'on'",
                    sliderInput("initNumMixedRaceGroups",
                        label="Number of orientation groups",
                        min=0,max=20,value=5)),
                conditionalPanel(
                    condition="input.oGroups == 'on'",
                    sliderInput("mixedRaceGroupFraction",
                        label="Fraction of minorities in O-groups",
                        min=.2,max=1,step=.05,value=.5))
            ),
            div(class="inputCategory",
                HTML("Encounters/decay:<br/>"),
                sliderInput("numToMeetPop",
                    label="Number of random people encountered monthly",
                    value=5, min=0,max=25,step=1),
                sliderInput("numToMeetGroup",
                    label="Number of group members encountered monthly",
                    value=10, min=0,max=25,step=1),
                sliderInput("decayThreshold", label=
                    "Decay threshold (num months before friends must tickle)",
                    value=2, min=0,max=12,step=1)
            ),
            div(class="inputCategory",
                HTML("Groups:<br/>"),
                sliderInput("initNumGroups",
                    label="Initial number of groups",value=20,
                    min=0,max=40),
                sliderInput("recruitmentRequired",
                    label="\"Recruitment required\"",
                    value=.6, min=0,max=1,step=.05),
                sliderInput("likelihoodOfLeavingGroup",
                    label="Likelihood (per year) of student leaving group ",
                    value=.1, min=0,max=.2,step=.01),
                sliderInput("numNewGroupsPerYear",
                    label="Number of new groups created per year",value=2,
                    min=0,max=10)
            ),
            div(class="inputCategory",
                HTML("Personality drift:<br/>"),
                sliderInput("groupDriftRate",
                    label="Group drift rate (prob of changing attr per group)",
                    value=.1, min=0,max=1,step=0.05),
                sliderInput("groupDriftDistance",
                    label="Group drift distance (fraction towards mean)",
                    value=.2, min=0,max=1,step=0.05),
                sliderInput("peerDriftRate",
                    label="Peer drift rate (prob of changing attr)",
                    value=.1, min=0,max=1,step=0.05),
                sliderInput("peerDriftDistance",
                    label="Peer drift distance (fraction towards mean)",
                    value=.2, min=0,max=1,step=0.05)
            ),

            hr(),

            flowLayout(
                radioButtons("seedType",label="Seed",
                    choices=c("Random"="rand",
                        "Specific"="specific"),
                    selected="rand",
                    inline=TRUE),
                conditionalPanel(condition="input.seedType == 'specific'",
                    numericInput("seed","",value=0))
            ),

            hr(),

            htmlOutput("log"),

            hr(),

            checkboxInput("showUninterestingParameters",
                label="Show uninteresting parameters", value=FALSE),
            conditionalPanel(
                condition="input.showUninterestingParameters == true",
                sliderInput("probWhite",
                    label="White student body proportion",value=.85,
                    min=0,max=1,step=0.05),
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
                    HTML("Dropout rate:<br/>"),
                    sliderInput("dropoutRate",
                        label="Dropout Rate",value=0.00,
                        min=0,max=.2,step=0.005),
                    sliderInput("dropoutIntercept",
                        label="Dropout Intercept",value=0.00,
                        min=0,max=.2,step=0.005)
                )
            )
        )
    ),

    mainPanel(
        tabsetPanel(
            tabPanel("Friendships",
                plotOutput("friendshipsPlot"),
                plotOutput("interracialRelationshipsPlot")
            ),
            tabPanel("Dropouts",
                plotOutput("dropoutPlot")
            ),
            tabPanel("Encounters",
                plotOutput("encountersPlot",height="800px")
            ),
            tabPanel("Groups",
                plotOutput("currentGroupsPlot"),
                plotOutput("groupsHistoryPlot"),
                plotOutput("numGroupsPlot")
            ),
            tabPanel("Similarity",
                plotOutput("similarityPlot"),
                plotOutput("similarityImpactPlot",height="600px")
            )
        )
    )
))
