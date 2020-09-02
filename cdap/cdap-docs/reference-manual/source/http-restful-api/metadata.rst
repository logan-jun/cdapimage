.. meta::
    :author: Cask Data, Inc.
    :description: HTTP RESTful Interface to the Cask Data Application Platform
    :copyright: Copyright © 2015-2017 Cask Data, Inc.

.. _http-restful-api-metadata:
.. _http-restful-api-v3-metadata:

=========================
Metadata HTTP RESTful API
=========================

.. highlight:: console

Use the CDAP Metadata HTTP RESTful API to set, retrieve, and delete the metadata annotations
of applications, datasets, streams, and other entities in CDAP.

**Note**: Metadata for versioned entities is not versioned, including entities such as applications,
programs, schedules, and program runs. Additions to metadata in one version are reflected in all versions.

Metadata consists of **properties** (a list of key-value pairs) or **tags** (a list of keys).
Metadata and their use are described in the :ref:`Developer Manual: Metadata and Lineage
<metadata-lineage>`.

The HTTP RESTful API is divided into these sections:

- :ref:`metadata properties <http-restful-api-metadata-properties>`
- :ref:`metadata tags <http-restful-api-metadata-tags>`
- :ref:`searching metadata <http-restful-api-metadata-searching>`
- :ref:`viewing lineage <http-restful-api-metadata-lineage>`
- :ref:`field level lineage <http-restful-api-metadata-fieldlevellineage>`
- :ref:`metadata for a run of a program <http-restful-api-metadata-run>`

Metadata keys, values, and tags must conform to the CDAP :ref:`alphanumeric extra extended
character set <supported-characters>`, and are limited to 50 characters in length. The entire
metadata object associated with a single entity is limited to 10K bytes in size.

There is one reserved word for property keys and values: *tags*, either as ``tags`` or
``TAGS``. Tags themselves have no reserved words.

The online metadata is migrated when CDAP is upgraded from 4.3.x to 5.0.0. During metadata migration,
writes to non-migrated entities fail and reads on metadata for non-migrated entities do not have all the metadata.
Metadata migration takes approximately 2-3 minutes. You can check the metadata migration status by using the
:ref:upgrade status <http-restful-api-monitor-status-system-upgrade> REST endpoint.

.. Base URL explanation
.. --------------------
.. include:: base-url.txt


.. _http-restful-api-metadata-properties:

Metadata Properties
===================

Annotating Properties
---------------------
To annotate user metadata properties for an application, dataset, or other entities including custom entities,
submit an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/<entity-details>/metadata/properties

or, for a particular program of a specific application::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/properties

or, for a particular version of an artifact::

  POST /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/properties

or, for a particular view of a stream::

  POST /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/properties

or, for a custom entity like field of a dataset::

    POST /v3/namespaces/<namespace-id>/datasets/<dataset-id>/field/<field-name>/metadata/properties

.. highlight:: json-ellipsis

with the metadata properties as a JSON string map of string-string pairs, passed in the
request body::

  {
    "key1" : "value1",
    "key2" : "value2",
    ...
  }

.. highlight:: console

New property keys will be added and existing keys will be updated.
Existing keys not in the properties map will not be deleted.

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``entity-details``
     - :ref:`Hierarchical key-value representation of the entity <metadata_custom_entities>`
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``spark``, ``workflows``, ``services``, or ``workers``
   * - ``program-id``
     - Name of the program
   * - ``artifact-id``
     - Name of the artifact
   * - ``artifact-version``
     - Version of the artifact
   * - ``stream-id``
     - Name of the stream
   * - ``view-id``
     - Name of the stream view
   * - ``dataset-id``
     - Name of the dataset
   * - ``field-name``
     - Name of the field

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The properties were set

**Note**: When using this API, properties can be added to the metadata of the specified entity
only in the *user* scope.


Retrieving Properties
---------------------
To retrieve user metadata properties for an application, dataset, or other entities including custom entities,
submit an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/<entity-details>/metadata/properties[?scope=<scope>]

or, for a particular program of a specific application::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/properties[?scope=<scope>]

or, for a particular version of an artifact::

  GET /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/properties[?scope=<scope>]

or, for a particular view of a stream::

  GET /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/properties[?scope=<scope>]

or, for a custom entity like field of a dataset::

  GET /v3/namespaces/<namespace-id>/datasets/<dataset-id>/field/<field-name>/metadata/properties[?scope=<scope>]

.. highlight:: json-ellipsis

with the metadata properties returned as a JSON string map of string-string pairs, passed
in the response body (pretty-printed)::

  {
    "key1" : "value1",
    "key2" : "value2",
    ...
  }

.. highlight:: console

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``entity-details``
     - :ref:`Hierarchical key-value representation of the entity <metadata_custom_entities>`
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``spark``, ``workflows``, ``services``, or ``workers``
   * - ``program-id``
     - Name of the program
   * - ``artifact-id``
     - Name of the artifact
   * - ``artifact-version``
     - Version of the artifact
   * - ``stream-id``
     - Name of the stream
   * - ``view-id``
     - Name of the stream view
   * - ``dataset-id``
     - Name of the dataset
   * - ``field-name``
     - Name of the field
   * - ``scope``
     - Optional scope filter. If not specified, properties in the ``user`` and
       ``system`` scopes are returned. Otherwise, only properties in the specified scope are returned.

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The properties requested were returned as a JSON string in the body of the response which can be empty if there are no properties associated with the entity, or the entity does not exist


Deleting Properties
-------------------
To delete **all** user metadata properties for an application, dataset, or other entities including custom entities,
submit an HTTP DELETE request::

  DELETE /v3/namespaces/<namespace-id>/<entity-details>/metadata/properties

or, for all user metadata properties of a particular program of a specific application::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/properties

or, for a particular version of an artifact::

  DELETE /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/properties

or, for a particular view of a stream::

  DELETE /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/properties

To delete **a specific property** for an application, dataset, or stream, submit
an HTTP DELETE request with the property key::

  DELETE /v3/namespaces/<namespace-id>/<entity-type>/<entity-id>/metadata/properties/<key>

or, for a particular property of a program of a specific application::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/properties/<key>

or, for a particular version of an artifact::

  DELETE /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/properties/<key>

or, for a particular view of a stream::

  DELETE /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/properties/<key>

or, for a custom entity like field of a dataset::

    DELETE /v3/namespaces/<namespace-id>/datasets/<dataset-id>/field/<field-name>/metadata/properties/<key>

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``entity-details``
     - :ref:`Hierarchical key-value representation of the entity <metadata_custom_entities>`
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``spark``, ``workflows``, ``services``, or ``workers``
   * - ``program-id``
     - Name of the program
   * - ``artifact-id``
     - Name of the artifact
   * - ``artifact-version``
     - Version of the artifact
   * - ``stream-id``
     - Name of the stream
   * - ``view-id``
     - Name of the stream view
   * - ``dataset-id``
     - Name of the dataset
   * - ``field-name``
     - Name of the field
   * - ``key``
     - Metadata property key

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The method was successfully called, and the properties were deleted, or in the case of a
       specific key, were either deleted or the key was not present, or the entity itself was not present

**Note**: When using this API, only properties in the *user* scope can be deleted.

.. _http-restful-api-metadata-tags:

Metadata Tags
=============

Adding Tags
-----------
To add user metadata tags for an application, dataset, or other entities including custom entities,
submit an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/<entity-details>/metadata/tags

or, for a particular program of a specific application::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/tags

or, for a particular version of an artifact::

  POST /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/tags

or, for a particular view of a stream::

  POST /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/tags

or, for a custom entity like field of a dataset::

  POST /v3/namespaces/<namespace-id>/datasets/<dataset-id>/field/<field-name>/metadata/tags

with the metadata tags, as a list of strings, passed in the JSON request body::

  ["tag1", "tag2"]

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``entity-details``
     - :ref:`Hierarchical key-value representation of the entity <metadata_custom_entities>`
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``spark``, ``workflows``, ``services``, or ``workers``
   * - ``program-id``
     - Name of the program
   * - ``artifact-id``
     - Name of the artifact
   * - ``artifact-version``
     - Version of the artifact
   * - ``stream-id``
     - Name of the stream
   * - ``view-id``
     - Name of the stream view
   * - ``dataset-id``
     - Name of the dataset
   * - ``field-name``
     - Name of the field

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The tags were set

**Note**: When using this API, tags can be added to the metadata of the specified entity only in the user scope.


Retrieving Tags
---------------
To retrieve user metadata tags for an application, dataset, or other entities including custom entities,
submit an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/<entity-details>/metadata/tags[?scope=<scope>]

or, for a particular program of a specific application::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/tags[?scope=<scope>]

or, for a particular version of an artifact::

  GET /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/tags[?scope=<scope>]

or, for a particular view of a stream::

  GET /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/tags[?scope=<scope>]

or, for a custom entity like field of a dataset::

  GET /v3/namespaces/<namespace-id>/dataset/<dataset-id>/field/<field-name>/metadata/tags[?scope=<scope>]

with the metadata tags returned as a JSON string in the return body::

  ["tag1", "tag2"]

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``entity-details``
     - :ref:`Hierarchical key-value representation of the entity <metadata_custom_entities>`
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``spark``, ``workflows``, ``services``, or ``workers``
   * - ``program-id``
     - Name of the program
   * - ``artifact-id``
     - Name of the artifact
   * - ``artifact-version``
     - Version of the artifact
   * - ``stream-id``
     - Name of the stream
   * - ``view-id``
     - Name of the stream view
   * - ``dataset-id``
     - Name of the dataset
   * - ``field-name``
     - Name of the field
   * - ``scope``
     - Optional scope filter. If not specified, properties in the ``user`` and
       ``system`` scopes are returned. Otherwise, only properties in the specified scope are returned.

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The tags requested were returned as a JSON string in the body of the response which can be empty if there are no tags associated with the entity or entity does not exist


Removing Tags
-------------
To delete all user metadata tags for an application, dataset, or other entities including custom entities, submit an
HTTP DELETE request::

  DELETE /v3/namespaces/<namespace-id>/<entity-details>/metadata/tags

or, for all user metadata tags of a particular program of a specific application::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/tags

or, for a particular version of an artifact::

  DELETE /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/tags

or, for a particular view of a stream::

  DELETE /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/tags

To delete a specific user metadata tag for an application, dataset, or stream, submit
an HTTP DELETE request with the tag::

  DELETE /v3/namespaces/<namespace-id>/<entity-type>/<entity-id>/metadata/tags/<tag>

or, for a particular user metadata tag of a program of a specific application::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/metadata/tags/<tag>

or, for a particular version of an artifact::

  DELETE /v3/namespaces/<namespace-id>/artifacts/<artifact-id>/versions/<artifact-version>/metadata/tags/<tag>

or, for a particular view of a stream::

  DELETE /v3/namespaces/<namespace-id>/streams/<stream-id>/views/<view-id>/metadata/tags/<tag>

or, for a custom entity like field of a dataset::

  DELETE /v3/namespaces/<namespace-id>/datasets/<dataset-id>/field/<field-name>/metadata/tags/<tag>

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``entity-details``
     - :ref:`Hierarchical key-value representation of the entity <metadata_custom_entities>`
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``spark``, ``workflows``, ``services``, or ``workers``
   * - ``program-id``
     - Name of the program
   * - ``artifact-id``
     - Name of the artifact
   * - ``artifact-version``
     - Version of the artifact
   * - ``stream-id``
     - Name of the stream
   * - ``view-id``
     - Name of the stream view
   * - ``dataset-id``
     - Name of the dataset
   * - ``field-name``
     - Name of the field
   * - ``tag``
     - Metadata tag

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The method was successfully called, and the tags were deleted, or in the case of a
       specific tag, was either deleted or the tag was not present, or the entity itself was not present

**Note**: When using this API, only tags in the user scope can be deleted.


.. _http-restful-api-metadata-searching:

Searching for Metadata
======================
CDAP supports searching metadata of entities. To find which applications, datasets, streams, etc. have a particular
metadata property or metadata tag, submit an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/metadata/search?query=<term>[&target=<entity-type>&target=<entity-type2>...][&<option>=<option-value>&...]

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``query``
     - :ref:`Query term <http-restful-api-metadata-query-terms>`, as described below. Query terms are case-insensitive.
   * - ``entity-type``
     - Restricts the search to either all or specified entity types: ``all``, ``artifact``, ``app``, ``dataset``,
       ``program``, ``stream``, ``view``
   * - ``option``
     - Options for controlling cursors, limits, offsets, the inclusion of hidden and custom entities, and sorting:

       .. list-table::
          :widths: 20 80
          :header-rows: 1

          * - Option Name
            - Option Value, Description, and Notes
          * - ``sort``
            - The sorting order for the ``results`` being returned. Default is to sort search results as a function of
              relative weights for the specified search query. Specify the sort order as the field name followed by the
              sort order (either ``asc`` or ``desc``) with a space separating the two. Using URL-encoding, an example:
              ``&sort=creation-time+asc``. Note that this field is only applicable when the search query is ``*``.
          * - ``offset``
            - The number of search results to skip before including them in the returned ``results``. Default is ``0``.
          * - ``limit``
            - The number of metadata search entities to return in the ``results``. By default, there is no limit.
          * - ``cursor``
            - Cursor to move to in the search results. This would be a value returned in the ``cursors`` field of a
              response of a previous metadata search request. Note that this field is only applicable when the search
              query is ``*``.
          * - ``numCursors``
            - Determines the number of chunks of search results of size ``limit`` to fetch after the first chunk of
              size ``limit``. This parameter can be used to roughly estimate the total number of results that match
              the search query. Only used when the search query is ``*``.
          * - ``showHidden``
            - By default, metadata search hides entities whose name starts with an ``_`` (underscore) from the search
              results. Set this to ``true`` to include these hidden entities in search results. Default is ``false``.
          * - ``showCustom``
            - By default, metadata search hides custom entities from the search results for backward compatibility.
              Set this to ``true`` to include these custom entities in search results. Default is ``false``.
          * - ``entityScope``
            - The scope of entities for the metadata search. By default, all entities will be returned. Set this to
              ``USER`` to include only user entities; set this to ``SYSTEM`` to include only system entities.

       Format for an option: ``&<option-name>=<option-value>``

.. highlight:: json-ellipsis

Entities that match the specified query and entity type are returned in the body of the response in JSON format::

  {
    "cursors": [ ],
    "limit": 20,
    "numCursors": 0,
    "offset": 0,
    "showHidden": false,
    "sort": "creation-time DESC",
    "total": 2,
    "entityScope": [ "SYSTEM" ]
    "results": [
        {
            "entityId": {
                "application": "WordCount",
                "entity": "PROGRAM",
                "namespace": "default",
                "program": "RetrieveCounts",
                "type": "Service",
                "version": "-SNAPSHOT"
            },
            "metadata": {
                "SYSTEM": {
                    "properties": {
                        "creation-time": "1482091087438",
                        "description": "A service to retrieve statistics, word counts, and associations.",
                        "entity-name": "RetrieveCounts",
                        "version": "-SNAPSHOT"
                    },
                    "tags": [
                        "Realtime",
                        "Service"
                    ]
                }
            }
        },
        {
            "entityId": {
                "application": "WordCount",
                "entity": "PROGRAM",
                "namespace": "default",
                "program": "WordCounter",
                "type": "Flow",
                "version": "-SNAPSHOT"
            },
            "metadata": {
                "SYSTEM": {
                    "properties": {
                        "creation-time": "1482091087390",
                        "description": "Example Word Count Flow",
                        "entity-name": "WordCounter",
                        "version": "-SNAPSHOT"
                    },
                    "tags": [
                        "Flow",
                        "Realtime"
                    ]
                }
            }
        }
    ]
  }

.. highlight:: console

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Entity ID and metadata of entities that match the query and entity type(s) are returned in the body of the response

.. _http-restful-api-metadata-query-terms:

.. rubric:: Query Terms

CDAP supports prefix-based search of metadata properties and tags across both *user* and *system* scopes.
Search metadata of entities by using either a complete or partial name followed by an asterisk ``*``.

Search for properties and tags by specifying one of:

- a complete property key-value pair, separated by a colon, such as ``type:production``

- a complete property key with a partial value, such as ``type:prod*``

- a complete ``tags`` key with a complete or partial value, such as ``tags:production`` or ``tags:prod*``
  to search for tags only

- a complete or partial value, such as ``prod*``; this will return both properties and tags

- multiple search terms separated by space, such as ``type:prod* author:joe``; this will return entities having
  either of the terms in their metadata.

Since CDAP also annotates *system* metadata to entities by default as mentioned at
:ref:`System Metadata <metadata-system-metadata>`, the following *special* search queries are also supported:

- artifacts or applications containing a specific plugin: ``plugin:<plugin-name>``

- programs with a specific mode: ``batch`` or ``realtime``

- applications with a specific program type: ``flow:<flow-name>``, ``service:<service-name>``,
  ``mapreduce:<mapreduce-name>``, ``spark:<spark-name>``, ``worker:<worker-name>``,
  ``workflow:<workflow-name>``

- datasets, streams or views with schema field:

  - field name only: ``field-name``
  - field name with a type: ``<field-name>:<field-type>``, where ``field-type`` can be:

    - simple types: ``int``, ``long``, ``boolean``, ``float``, ``double``, ``bytes``, ``string``, ``enum``
    - complex types: ``array``, ``map``, ``record``, ``union``

.. highlight:: json-ellipsis

::

  {
     "type":"record",
     "name":"employee",
     "fields":[
        {
           "name":"employeeName",
           "type":"string"
        },
        {
           "name":"departments",
           "type":{
              "type":"array",
              "items":"long"
           }
        }
     ]
  }

.. highlight:: console

With a schema as shown above, queries such as ``employee:record``, ``employeeName:string``, ``departments``,
``departments:array`` can be issued.

.. _http-restful-api-metadata-lineage:

Viewing Lineages
================
To view the lineage of a dataset or stream, submit an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/<entity-type>/<entity-id>/lineage?start=<start-ts>&end=<end-ts>[&levels=<levels>][&collapse=<collapse>&collapse=<collapse>...]

where:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``entity-type``
     - One of ``datasets`` or ``streams``
   * - ``entity-id``
     - Name of the ``dataset`` or ``stream``
   * - ``start-ts``
     - Starting time-stamp of lineage (inclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``end-ts``
     - Ending time-stamp of lineage (exclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``levels``
     - Number of levels of lineage output to return. Defaults to 10. Determines how far back the provenance
       of the data in the lineage chain is calculated.
   * - ``collapse``
     - An optional set of ``collapse`` types (any of ``access``, ``run``, or ``component``) by which to
       :ref:`collapse the lineage output <http-restful-api-metadata-lineage-collapse>`.
       By default, lineage output is not collapsed. Multiple collapse parameters are supported.
   * - ``rollup``
     - An optional ``rollup`` type to use to :ref:`rollup the lineage output
       <http-restful-api-metadata-lineage-rollup>`. By default, lineage output is not rolled up.
       Currently supports the value ``workflow``.

See in the Metrics HTTP RESTful API :ref:`Querying by a Time Range <http-restful-api-metrics-time-range>`
for examples of the "now" time syntax.

For more information about collapsing lineage output, please refer to the section below on
:ref:`Collapsing Lineage Output <http-restful-api-metadata-lineage-collapse>`.

The lineage will be returned as a JSON string in the body of the response. The JSON describes lineage as a graph
of connections between programs and datasets (or streams) in the specified time range. The number of
levels of the request (``levels``) determines the depth of the graph. This impacts how far back the provenance of the
data in the lineage chain is calculated, as described in the :ref:`Metadata and Lineage <metadata-lineage>`.

Lineage JSON consists of three main sections:

- **Relations:** contains information on data accessed by programs.
  Access type can be *read*, *write*, *both*, or *unknown*.
  It also contains the *runid* of the program that accessed the data,
  and the specifics of any *component* of a program
  that also accessed the data. For example, a flowlet is a *component* of a flow.
- **Data:** contains Datasets or Streams that were accessed by programs.
- **Programs:** contains information on programs (flows, MapReduce, Spark, workers, etc.)
  that accessed the data.

.. highlight:: json-ellipsis

Here is an example, pretty-printed::

  {
      "start": 1442863938,
      "end": 1442881938,
      "relations": [
          {
              "data": "stream.default.purchaseStream",
              "program": "flows.default.PurchaseHistory.PurchaseFlow",
              "access": "read",
              "runs": [
                  "4b5d7891-60a7-11e5-a9b0-42010af01c4d"
              ],
              "components": [
                  "reader"
              ]
          },
          {
              "data": "dataset.default.purchases",
              "program": "flows.default.PurchaseHistory.PurchaseFlow",
              "access": "unknown",
              "runs": [
                  "4b5d7891-60a7-11e5-a9b0-42010af01c4d"
              ],
              "components": [
                  "collector"
              ]
          }
      ],
      "data": {
          "dataset.default.purchases": {
              "entityId": {
                  "dataset": "history",
                  "entity": "DATASET",
                  "namespace": "default"
              }
          },
          "stream.default.purchaseStream": {
              "entityId": {
                  "entity": "STREAM",
                  "namespace": "default",
                  "stream": "purchaseStream"
              }
          }
      },
      "programs": {
          "flows.default.PurchaseHistory.PurchaseFlow": {
              "entityId": {
                  "application": "PurchaseHistory",
                  "entity": "PROGRAM",
                  "namespace": "default",
                  "program": "PurchaseFlow",
                  "type": "Flow",
                  "version": "-SNAPSHOT"
              }
          }
      }
  }

.. highlight:: console

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Entities IDs of entities with the metadata properties specified were returned as a
       list of strings in the body of the response
   * - ``404 NOT FOUND``
     - No entities matching the specified query were found


.. _http-restful-api-metadata-lineage-collapse:

Collapsing Lineage Output
-------------------------
Lineage output can be collapsed by ``access``, ``run``, or ``component``. Collapsing allows you to group all the lineage
relations for the specified collapse-type together in the lineage output. Collapsing is useful when you do not want
to differentiate between multiple lineage relations that only differ by the collapse-type.

For example, consider a program that wrote to a dataset in multiple runs over a specified time interval.
If you do not want to differentiate between lineage relations involving different runs of this program
(so you only want to know that a program accessed a data entity in a given time interval), you could provide
the query parameter ``collapse=run`` to the lineage API. This would collapse the lineage relations in the
output to group the multiple runs of the program together.

You can also collapse lineage relations by `access` (which will group together those relations that
differ only by access type) or by `component` (which will group together those that differ only by
component together). The lineage HTTP RESTful API also allows you to use multiple `collapse`` parameters
in the same query.

.. highlight:: json-ellipsis

Consider these relations from the output of a lineage API request::

  {
    "relations": [
      {
        "accesses": [
          "read"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      },
      {
        "accesses": [
          "read"
        ],
        "components": [
          "reader"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      },
      {
        "accesses": [
          "read"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "ae188ea2-0c2f-11e6-b499-561602fdb525"
        ]
      },
      {
        "accesses": [
          "write"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      },
      {
        "accesses": [
          "write"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchase",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "ae188ea2-0c2f-11e6-b499-561602fdb525"
        ]
      }
    ]
  }

Collapsing the above by ``run`` would group the runs together as::

  {
    "relations": [
      {
        "accesses": [
          "read"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525",
          "ae188ea2-0c2f-11e6-b499-561602fdb525"
        ]
      },
      {
        "accesses": [
          "read"
        ],
        "components": [
          "reader"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      },
      {
        "accesses": [
          "write"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525",
          "ae188ea2-0c2f-11e6-b499-561602fdb525"
        ]
      }
    ]
  }

Collapsing by ``access`` would produce::

  {
    "relations": [
      {
        "accesses": [
          "read",
          "write"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      },
      {
        "accesses": [
          "read",
          "write"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "ae188ea2-0c2f-11e6-b499-561602fdb525"
        ]
      },
      {
        "accesses": [
          "read"
        ],
        "components": [
          "reader"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      }
    ]
  }

Similarly, collapsing by ``component`` will generate::

  {
    "relations": [
      {
        "accesses": [
          "read"
        ],
        "components": [
          "collector",
          "reader"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      },
      {
        "accesses": [
          "read"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "ae188ea2-0c2f-11e6-b499-561602fdb525"
        ]
      },
      {
        "accesses": [
          "write"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "a442db61-0c2f-11e6-bc75-561602fdb525"
        ]
      },
      {
        "accesses": [
          "write"
        ],
        "components": [
          "collector"
        ],
        "data": "dataset.default.purchase",
        "program": "mapreduce.default.PurchaseHistory.PurchaseFlow",
        "runs": [
          "ae188ea2-0c2f-11e6-b499-561602fdb525"
        ]
      }
    ]
  }

.. _http-restful-api-metadata-lineage-rollup:

Rolling Up Lineage Output
-------------------------
Lineage rollup allows you to group multiple entities together for a condensed view in the lineage output.

Currently, for the parameter ``rollup``, only the value ``workflow`` is supported, which allows programs
to be grouped together into workflows if multiple programs are created as part of a workflow.

For example, suppose you have a workflow that starts two programs to complete an associated task. If you do not want to
differentiate between lineage relations involving different programs of this workflow, you could provide the query
parameter ``rollup=workflow`` to the lineage API. This would rollup the lineage relations in the output to show
corresponding workflows instead of individual programs.

Consider these relations from the output of a lineage API request::

  {
    "start": 1442863938,
    "end": 1442881938,
    "relations": [
      {
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.phase-1",
        "access": "read",
        "runs": [
          "4b5d7891-60a7-11e5-a9b0-42010af01c4d"
        ],
        "components": [
          "reader"
        ]
      },
      {
        "data": "dataset.default.purchases",
        "program": "mapreduce.default.PurchaseHistory.phase-2",
        "access": "unknown",
        "runs": [
          "7d6r7891-60a7-11e5-a9b0-42010af01c4d"
        ],
        "components": [
          "collector"
        ]
      }
    ],
    "data": {
      "dataset.default.purchases": {
        "entityId": {
            "dataset": "purchases",
            "entity": "DATASET",
            "namespace": "default"
        }
      },
    },
    "programs": {
      "mapreduce.default.PurchaseHistory.phase-1": {
        "entityId": {
            "application": "PurchaseHistory",
            "entity": "PROGRAM",
            "namespace": "default",
            "program": "phase-1",
            "type": "Mapreduce",
            "version": "-SNAPSHOT"
        }
      },
      "mapreduce.default.PurchaseHistory.phase-2": {
        "entityId": {
            "application": "PurchaseHistory",
            "entity": "PROGRAM",
            "namespace": "default",
            "program": "phase-2",
            "type": "Mapreduce",
            "version": "-SNAPSHOT"
        }
      }
    },
  }

Rolling up the above using ``rollup=workflow`` would group the programs together as::

  {
    "start": 1442863938,
    "end": 1442881938,
    "relations": [
      {
        "data": "dataset.default.purchases",
        "program": "workflows.default.PurchaseHistory.DataPipelineWorkflow",
        "access": [
          "read",
          "unknown"
        ],
        "runs": [
          "5b3ar7891-60a7-11e5-a9b0-42010af01c4d"
        ],
        "components": [
          "reader"
        ]
      },
    ],
    "data": {
      "dataset.default.purchases": {
        "entityId": {
          "dataset": "purchases",
          "entity": "DATASET",
          "namespace": "default"
        }
      },
    },
    "programs": {
      "workflows.default.PurchaseHistory.DataPipelineWorkflow": {
        "entityId": {
            "application": "PurchaseHistory",
            "entity": "PROGRAM",
            "namespace": "default",
            "program": "DataPipelineWorkflow",
            "type": "Workflow",
            "version": "-SNAPSHOT"
        }
      },
    },
  }

.. highlight:: console

.. _http-restful-api-metadata-fieldlevellineage:

Field Level Lineage
===================

Fields associated with the Dataset
----------------------------------

Gets the fields that were associated with the dataset for the specified range of time::

  GET /namespaces/{namespace-id}/datasets/{dataset-id}/lineage/fields?start=<start-ts>&end=<end-ts>[&prefix=<prefix>]

where:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``dataset-id``
     - Name of the ``dataset``
   * - ``start-ts``
     - Starting time-stamp (inclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``end-ts``
     - Ending time-stamp (exclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``prefix``
     - Optional ``prefix``, when provided only fields that have given prefix will be returned
   * - ``includeCurrent``
     - Optional flag, when set to true the current fields of the dataset will be be included irrespective of whether they have any lineage information or not.


Following is sample response::

  [{"name":"firstName","lineage":true},{"name":"lastName","lineage":true},{"name":"customer_id","lineage":false}]

.. highlight:: console

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Fields of dataset are returned as a list of strings in the body of the response
   * - ``400 BAD REQUEST``
     - Failure to parse the time range provided

Field Lineage Summary
---------------------

Gets the field lineage summary for a specified field of a dataset. The field lineage summary consists of the
sets of datasets and their respective fields used to compute the specified field of a dataset::

  GET /namespaces/{namespace-id}/datasets/{dataset-id}/lineage/fields/{field-name}?start=<start-ts>&end=<end-ts>&direction=incoming

where:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``dataset-id``
     - Name of the ``dataset``
   * - ``field-name``
     - Name of the ``field``
   * - ``start-ts``
     - Starting time-stamp (inclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``end-ts``
     - Ending time-stamp (exclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``direction``
     - ``incoming``, to return the set of dataset and fields which participated in the computation of the given field. ``outgoing``, to return the set of dataset and fields to whose computation the given field participated. ``both``, to return both incoming and outgoing.

The returned response consists of the direction in which the summary is requested and the datasets and fields
that were responsible for the computation of a specified field. Currently, the only supported direction is ``incoming``.

Following is a sample response::

  {
      "incoming": [
          {
              "dataset": {
                  "dataset": "Customer",
                  "entity": "DATASET",
                  "namespace": "default"
              },
              "fields": [
                  "body"
              ]
          },
          {
              "dataset": {
                  "dataset": "purchases",
                  "entity": "DATASET",
                  "namespace": "default"
              },
              "fields": [
                  "body"
              ]
          }
      ]
  }

.. highlight:: console

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Fields of dataset are returned as a list of strings in the body of the response
   * - ``400 BAD REQUEST``
     - Failure to parse the time range provided

Field Lineage Operations
------------------------

Gets the details of operations responsible for computation of a specified field of a dataset for a specified range of time::

  GET /namespaces/{namespace-id}/datasets/{dataset-id}/lineage/fields/{field-name}/operations?start=<start-ts>&end=<end-ts>&direction=incoming

where:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``dataset-id``
     - Name of the ``dataset``
   * - ``field-name``
     - Name of the ``field``
   * - ``start-ts``
     - Starting time-stamp (inclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``end-ts``
     - Ending time-stamp (exclusive), in seconds. Supports ``now``, ``now-1h``, etc. syntax.
   * - ``direction``
     - ``incoming``, to return the operations which participated in the computation of the given field. ``outgoing``, to return the operations in which this field participated. ``both``, to return both incoming and outgoing.

The single field can be computed in multiple ways, where each unique way consists of a list of operations that
participated in the computation and the list of programs that performed the computation. The returned response
consists of the direction in which the operations are requested. Currently, the only supported direction is ``incoming``.
For the specified direction, the response includes the different ways that the field was computed.

Following is a sample response::

    {
      "incoming": [
        {
            "operations": [
                {
                    "description": "Read files",
                    "inputs": {
                        "endPoint": {
                            "name": "purchases",
                            "namespace": "default"
                        }
                    },
                    "name": "File2.Read",
                    "outputs": {
                        "fields": [
                            "offset",
                            "body"
                        ]
                    }
                },
                {
                    "description": "Parsed field",
                    "inputs": {
                        "fields": [
                            {
                                "name": "body",
                                "origin": "File2.Read"
                            }
                        ]
                    },
                    "name": "CSVParser2.CSV Parse",
                    "outputs": {
                        "fields": [
                            "customer_id",
                            "item",
                            "price"
                        ]
                    }
                },
                {
                    "description": "Read files",
                    "inputs": {
                        "endPoint": {
                            "name": "Customer",
                            "namespace": "default"
                        }
                    },
                    "name": "File.Read",
                    "outputs": {
                        "fields": [
                            "offset",
                            "body"
                        ]
                    }
                },
                {
                    "description": "Parsed field",
                    "inputs": {
                        "fields": [
                            {
                                "name": "body",
                                "origin": "File.Read"
                            }
                        ]
                    },
                    "name": "CSVParser.CSV Parse",
                    "outputs": {
                        "fields": [
                            "id",
                            "first_name",
                            "last_name"
                        ]
                    }
                },
                {
                    "description": "Used as a key in a join",
                    "inputs": {
                        "fields": [
                            {
                                "name": "id",
                                "origin": "CSVParser.CSV Parse"
                            },
                            {
                                "name": "customer_id",
                                "origin": "CSVParser2.CSV Parse"
                            }
                        ]
                    },
                    "name": "Joiner.Join",
                    "outputs": {
                        "fields": [
                            "id",
                            "customer_id"
                        ]
                    }
                },
                {
                    "description": "Wrote to TPFS dataset",
                    "inputs": {
                        "fields": [
                            {
                                "name": "id_from_customer",
                                "origin": "Joiner.Rename id"
                            },
                            {
                                "name": "fname",
                                "origin": "Joiner.Rename CSVParser.first_name"
                            },
                            {
                                "name": "lname",
                                "origin": "Joiner.Rename CSVParser.last_name"
                            },
                            {
                                "name": "customer_id",
                                "origin": "Joiner.Join"
                            },
                            {
                                "name": "item",
                                "origin": "Joiner.Identity CSVParser2.item"
                            }
                        ]
                    },
                    "name": "Parquet Time Partitioned Dataset.Write",
                    "outputs": {
                        "endPoint": {
                            "name": "parquet_data",
                            "namespace": "default"
                        }
                    }
                }
            ],
            "programs": [
                {
                    "lastExecutedTimeInSeconds": 1532468358,
                    "program": {
                        "application": "customer_pipeline_spark",
                        "entity": "PROGRAM",
                        "namespace": "default",
                        "program": "DataPipelineWorkflow",
                        "type": "Workflow",
                        "version": "-SNAPSHOT"
                    }
                }
            ]
        }
      ]
    }

.. highlight:: console

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Fields of dataset are returned as a list of strings in the body of the response
   * - ``400 BAD REQUEST``
     - Failure to parse the time range provided

.. highlight:: console

.. _http-restful-api-metadata-run:

Retrieving Metadata for a Program Run (Deprecated)
==================================================
At every run of a program, the metadata associated with the program, the application it is part of, and any datasets
and streams used by the program run are recorded. To retrieve the metadata for a program run, submit an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/runs/<run-id>/metadata

.. highlight:: json-ellipsis

with the metadata returned as a JSON string in the return body::

  [
      {
          "entityId": {
              "entity": "STREAM",
              "namespace": "default",
              "stream": "purchaseStream"
          },
          "properties": {},
          "scope": "USER",
          "tags": []
      },
      {
          "entityId": {
              "application": "PurchaseHistory",
              "entity": "PROGRAM",
              "namespace": "default",
              "program": "PurchaseFlow",
              "type": "Flow",
              "version": "-SNAPSHOT"
          },
          "properties": {},
          "scope": "USER",
          "tags": [
              "flow-tag1"
          ]
      },
      {
          "entityId": {
              "dataset": "purchases",
              "entity": "DATASET",
              "namespace": "default"
          },
          "properties": {},
          "scope": "USER",
          "tags": []
      },
      {
          "entityId": {
              "application": "PurchaseHistory",
              "entity": "APPLICATION",
              "namespace": "default",
              "version": "-SNAPSHOT"
          },
          "properties": {},
          "scope": "USER",
          "tags": [
              "app-tag1"
          ]
      }
  ]

.. highlight:: console

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``spark``, ``workflows``, ``services``, or ``workers``
   * - ``program-id``
     - Name of the program
   * - ``run-id``
     - Program run id

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The properties requested were returned as a JSON string in the body of the response
   * - ``404 NOT FOUND``
     - The entity, program, or run for which properties are being requested was not found

**Note**: The Metadata aggregation of program, application, stream and dataset associated with a program is
deprecated. Program runs are treated like any other entity to which metadata can be associated directly.
To retrieve the direct metadata of program run please refer to
:ref:`Metadata Properties <http-restful-api-metadata-properties>` and
:ref:`Metadata Tags <http-restful-api-metadata-tags>`. An additional query parameter ``aggregateRun`` must be set to
``false`` while operating with metadata directly associated with program runs.

.. _metadata_custom_entities:

Metadata for Custom Entities
============================

Metadata can also be associated with custom entities. In CDAP Entities are separated into two kinds:

- CDAP Entities: These are system defined entities that have special meaning in CDAP. ``Namespace``, ``Stream``, ``Application``, ``Dataset`` etc. are example of CDAP Entities.

- Custom Entities: These are user defined entities that represent a resource that exists in CDAP.

Custom Entities are represented as a hierarchical key-value pair and can optionally have a explicitly defined type.

If a type is not specified then the last key in the hierarchy is considered as the type.

In REST APIs, custom entities are represented as hierarchical key-value pairs. If the last key in the hierarchy is
not a type, the type is specified as a query parameter.

For example, to add tags to a custom file entity in a dataset::

  POST /v3/namespaces/<namespace-id>/datasets/<dataset-id>/file/<file-name>/metadata/tags

In the example above, the custom entity is a single key-value pair where ``file`` is the key and ``<file-name>``
is the value.

To add tags to a custom jar entity in a namespace::

  POST /v3/namespaces/<namespace-id>/jar/<jar-id>/versions/<jar-version>/metadata/tags[?type=jar]

In the example above, the custom entity consists of two key-value pairs. The first has key ``jar`` and value
``<jar-id>``. The second has key ``versions`` and value ``<jar-version>``. We pass the jar as the type to specify
the type of the entity since the last key in the hierarchy is not the type in this case.

In both examples, the metadata tags are passed as a JSON list of strings in the request body::

  ["tag1", "tag2"]
