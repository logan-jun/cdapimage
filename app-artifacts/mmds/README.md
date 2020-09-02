# Model Management and Deployment Service

MMDS is a collaborative tool for developing, validating, distributing and retiring AI / ML models. This tool is a collection of service and user interface. Building a machine learning model is an iterative process. A data scientist will build many tens to hundreds of models before arriving at one that meets some acceptance criteria (e.g. AUC cutoff, accuracy
threshold). However, the current style of model building is ad-hoc and there is no practical way for a data scientist to manage models that are built over time. As a result, the data scientist must attempt to “remember” previously constructed models and insights obtained from them. This task is challenging for more than a handful of models and can hamper the process of sensemaking. Without a means to manage models, there is no easy way for a data scientist to answer questions such as “Which models were built using an incorrect feature?”, “Which model performed best on American customers?” or “How did the two top models compare?”. MMDS automatically track machine learning models in their native environments (e.g. scikit-learn, spark.ml), the ModelDB backend introduces a common layer of abstractions to represent models and pipelines, and the MMDS frontend allows visual exploration and analyses of models via a web-based interface.

## Why is MMDS required ?

Building a real-world machine learning model is a trial-and-error based iterative process. A data scientist starts with a hypothesis about the underlying data, builds a model based on this hypothesis, tests the model, and refines the hypothesis as well as model based on the results. The process of model building across all these companies could best be described as “ad-hoc” where the data scientist often built hundreds of models before arriving at one that met some acceptance criteria (e.g. AUC, accuracy). However, the data scientist had no means of tracking previously-built models or insights from previous experimentation. Consequently, the data scientist had to remember relevant information about previous models to inform the design of the next set of models.

As AI / ML is being democratized, an important part of any AI or ML process is the model that is generated either to predict or classify the target function. Multiple iterations, different experimentation, varied features used to build the model have to be systematically tracked. The Model management involves a collaborative team of modelers, architects, scoring officers, model auditors and validation testers. Many enterprises are struggling with the process of signing off on the development, validation, deployment, and retirement life cycle management. They need to readily know exactly where each model is in the life cycle, how old the model is, who developed the model, and who is using the model for what application.The ability to version-control the model over time is another critical business need which includes event logging and tracking changes to understand how the model form and usage is evolving over time.

Model decay is another serious challenge faced by organizations. Metrics are needed to determine when a model needs to be refreshed or replaced. Retired models also need to be archived. More reliable management of the score repositories is also a key requirement to ensure that quality representative data is available to evaluate model performance and profitability over time.

## Lifecycle of a model 

* Model Development,
* Model Validation, 
* Model Distribution and Deployment, &
* Model Enhancement or Retirement. 

## Features

* Web-based, central, secure repository for managing analytical models,
* Accounting and auditability,
* Import and export PMML model code with inputs and outputs,
* Create custom processes for each model,
* Perform common model management tasks using Model Projects category,
* Define test and production score logic using required inputs and outputs,
* Scoring test scheduler,
* Production scoring,
* Automated Model retaining,
* Model comparision reports &
* Model lifecycle templates.
