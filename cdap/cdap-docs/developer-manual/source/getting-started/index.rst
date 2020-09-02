.. meta::
    :author: Cask Data, Inc.
    :description: Index document
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

:hide-toc: true

.. _getting-started-index:

=========================
Getting Started With CDAP
=========================

.. toctree::
   :maxdepth: 1

   CDAP Sandbox <sandbox/index>
   Quick Start <quick-start>
   Development Environment Setup <dev-env>
   Starting and Stopping CDAP <start-stop-cdap>
   Building and Running Applications <building-apps>


The :ref:`CDAP Sandbox <sandbox-index>` includes all that
is needed to develop CDAP applications in your development environment, either your laptop
or a workstation. It has:

- A :ref:`CDAP Sandbox <sandbox-index>` that can run on a single machine in a single JVM. It provides all of
  the CDAP APIs without requiring a Hadoop cluster, using alternative, fully-functional
  implementations of CDAP features. For example, application containers are implemented as
  Java threads instead of YARN containers.
- The :ref:`CDAP UI, <cdap-ui>` a web-based graphical user interface to interact with CDAP instances
  and the applications they run.
- Tools for :ref:`ingesting data <ingesting-data>` and :ref:`authenticating
  clients <authentication-clients>`, :ref:`datasets, <datasets-index>` and :ref:`How-To
  Guides <guides-index>` to help you become familiar with CDAP, perform common
  tasks, and serve as the basis for developing your own applications.

Follow these steps:

1. Make sure you have the :ref:`system requirements and dependencies <system-requirements>`.
#. Obtain the CDAP Sandbox, using :ref:`one of the versions <sandbox-setup>`.
#. Follow the :ref:`installation instructions <sandbox-setup>` for the version you downloaded.
#. Follow our :ref:`Quick Start <quick-start>` for getting started with CDAP Data pipelines and Data Prep.
#. We suggest the :ref:`Guides <examples:examples-introduction-index>`
   as the easiest way to become familiar with CDAP Applications.
#. To begin writing your own application, start by setting up your :ref:`development environment <dev-env>`.
#. There are instructions for :doc:`starting and stopping CDAP <start-stop-cdap>`,
   and :doc:`building and running examples and applications <building-apps>`.

Online, we have additional resources ranging from :ref:`user groups <faq-cdap-user-groups>` to these manuals
and how-to guides:

- :ref:`Developer Manual: <developer-index>` Getting Started with building data pipelines, performing data prep and writing CDAP applications.
- :ref:`Reference Manual: <reference:reference-index>` APIs, Licenses and Dependencies
- :ref:`Administration Manual: <admin:admin-index>` Installation and Operation of Distributed CDAP installations
- :ref:`How-To Guides <examples:examples-introduction-index>`
